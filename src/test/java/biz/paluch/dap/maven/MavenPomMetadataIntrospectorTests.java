/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package biz.paluch.dap.maven;

import java.util.HashMap;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.BillOfMaterials;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.maven.MavenPomMetadataIntrospector.Gav;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedMetadata;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Integration tests for {@link MavenPomMetadataIntrospector}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class MavenPomMetadataIntrospectorTests {

	static ArtifactId MEMBER = ArtifactId.of("com.example", "member-core");

	static ArtifactId PARENT = ArtifactId.of("com.example", "example-parent");

	static ArtifactId BOM = ArtifactId.of("com.example", "example-bom");

	Cache cache = new Cache();

	Map<Gav, VirtualFile> poms = new HashMap<>();

	MavenPomMetadataIntrospector inspector;

	@BeforeEach
	void setUp(Project project) {

		inspector = new MavenPomMetadataIntrospector(project, cache) {

			@Override
			protected VirtualFile findPom(ArtifactId artifactId, String version) {
				return poms.get(Gav.of(artifactId, version));
			}

		};
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>member-core</artifactId>
				<version>1.0.0</version>
				<scm>
					<url>https://github.com/example/example-repo</url>
					<connection>scm:git:git@github.com:example/other-repo.git</connection>
				</scm>
				<issueManagement>
					<url>https://jira.example.com/browse/EX</url>
				</issueManagement>
			</project>
			""")
	void selectsScmUrlOverConnectionAndKeepsDeclaredTracker(@ProjectFile("member/pom.xml") PsiFile pom) {

		registerPom(MEMBER, "1.0.0", pom);

		CachedMetadata metadata = introspect(MEMBER, "1.0.0");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("https://github.com/example/example-repo");
		assertThat(metadata.getIssueTrackerUrl()).isEqualTo("https://jira.example.com/browse/EX");
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>member-core</artifactId>
				<version>1.0.0</version>
				<scm>
					<url>not a url at all</url>
					<connection>scm:git:https://github.com/example/example-repo.git</connection>
				</scm>
			</project>
			""")
	void skipsUndetectableScmUrlInFavorOfConnection(@ProjectFile("member/pom.xml") PsiFile pom) {

		registerPom(MEMBER, "1.0.0", pom);

		CachedMetadata metadata = introspect(MEMBER, "1.0.0");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("https://github.com/example/example-repo");
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>member-core</artifactId>
				<version>1.0.0</version>
				<scm>
					<url>https://github.com/example/example-repo</url>
				</scm>
			</project>
			""")
	void derivesIssueTrackerFromDetectedRepository(@ProjectFile("member/pom.xml") PsiFile pom) {

		registerPom(MEMBER, "1.0.0", pom);

		CachedMetadata metadata = introspect(MEMBER, "1.0.0");

		assertThat(metadata.getIssueTrackerUrl()).isEqualTo("https://github.com/example/example-repo/issues");
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<parent>
					<groupId>com.example</groupId>
					<artifactId>example-parent</artifactId>
					<version>5</version>
				</parent>
				<artifactId>member-core</artifactId>
				<issueManagement>
					<url>https://github.com/example/example-repo/issues</url>
				</issueManagement>
			</project>
			""")
	@ProjectFile(name = "parent/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>example-parent</artifactId>
				<version>5</version>
				<scm>
					<connection>scm:git:https://github.com/example/example-repo.git</connection>
				</scm>
			</project>
			""")
	void inheritsScmFromParentKeepingOwnTracker(@ProjectFile("member/pom.xml") PsiFile member,
			@ProjectFile("parent/pom.xml") PsiFile parent) {

		registerPom(MEMBER, "1.0.0", member);
		registerPom(PARENT, "5", parent);

		CachedMetadata metadata = introspect(MEMBER, "1.0.0");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("https://github.com/example/example-repo");
		assertThat(metadata.getIssueTrackerUrl()).isEqualTo("https://github.com/example/example-repo/issues");
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>member-core</artifactId>
				<version>1.0.0</version>
				<properties>
					<repository.git-url>https://github.com/example/example-repo</repository.git-url>
				</properties>
				<scm>
					<url>${unresolvable.property}</url>
					<connection>scm:git:${repository.git-url}.git</connection>
				</scm>
			</project>
			""")
	void interpolatesPropertiesAndDropsUnresolvedValues(@ProjectFile("member/pom.xml") PsiFile pom) {

		registerPom(MEMBER, "1.0.0", pom);

		CachedMetadata metadata = introspect(MEMBER, "1.0.0");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("https://github.com/example/example-repo");
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<parent>
					<groupId>com.example</groupId>
					<artifactId>example-parent</artifactId>
					<version>5</version>
				</parent>
				<artifactId>member-core</artifactId>
				<scm>
					<url>${repository.url}</url>
				</scm>
			</project>
			""")
	@ProjectFile(name = "parent/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>example-parent</artifactId>
				<version>5</version>
				<properties>
					<repository.url>https://github.com/example/example-repo</repository.url>
				</properties>
			</project>
			""")
	void resolvesParentPropertyReferencedInChildScm(@ProjectFile("member/pom.xml") PsiFile member,
			@ProjectFile("parent/pom.xml") PsiFile parent) {

		registerPom(MEMBER, "1.0.0", member);
		registerPom(PARENT, "5", parent);

		CachedMetadata metadata = introspect(MEMBER, "1.0.0");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("https://github.com/example/example-repo");
	}

	@Test
	@ProjectFile(name = "cycle-a/pom.xml", content = """
			<project>
				<parent>
					<groupId>com.example</groupId>
					<artifactId>cycle-b</artifactId>
					<version>1.0.0</version>
				</parent>
				<artifactId>cycle-a</artifactId>
			</project>
			""")
	@ProjectFile(name = "cycle-b/pom.xml", content = """
			<project>
				<parent>
					<groupId>com.example</groupId>
					<artifactId>cycle-a</artifactId>
					<version>1.0.0</version>
				</parent>
				<artifactId>cycle-b</artifactId>
			</project>
			""")
	void cyclicParentChainTerminatesWithEmptyMetadata(@ProjectFile("cycle-a/pom.xml") PsiFile cycleA,
			@ProjectFile("cycle-b/pom.xml") PsiFile cycleB) {

		ArtifactId cycleAId = ArtifactId.of("com.example", "cycle-a");
		registerPom(cycleAId, "1.0.0", cycleA);
		registerPom(ArtifactId.of("com.example", "cycle-b"), "1.0.0", cycleB);

		CachedMetadata metadata = introspect(cycleAId, "1.0.0");

		assertThat(metadata.getRepositoryUrl()).isNull();
		assertThat(metadata.getIssueTrackerUrl()).isNull();
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>member-core</artifactId>
				<version>1.0.0</version>
				<scm>
					<url>https://gitbox.apache.org/repos/asf/commons-lang.git</url>
				</scm>
			</project>
			""")
	void offPlatformScmContributesNothing(@ProjectFile("member/pom.xml") PsiFile pom) {

		registerPom(MEMBER, "1.0.0", pom);

		CachedMetadata metadata = introspect(MEMBER, "1.0.0");

		assertThat(metadata.getRepositoryUrl()).isNull();
		assertThat(metadata.getIssueTrackerUrl()).isNull();
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>member-core</artifactId>
				<version>1.0.0</version>
				<name>Member Core</name>
			</project>
			""")
	void capturesProjectNameFromOwnPom(@ProjectFile("member/pom.xml") PsiFile pom) {

		registerPom(MEMBER, "1.0.0", pom);

		CachedMetadata metadata = introspect(MEMBER, "1.0.0");

		assertThat(metadata.getProjectName()).isEqualTo("Member Core");
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>member-core</artifactId>
				<version>1.0.0</version>
				<name>${product.name} Core</name>
				<properties>
					<product.name>Member</product.name>
				</properties>
			</project>
			""")
	void interpolatesProjectName(@ProjectFile("member/pom.xml") PsiFile pom) {

		registerPom(MEMBER, "1.0.0", pom);

		CachedMetadata metadata = introspect(MEMBER, "1.0.0");

		assertThat(metadata.getProjectName()).isEqualTo("Member Core");
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>member-core</artifactId>
				<version>1.0.0</version>
				<name>${unresolvable.property} Core</name>
			</project>
			""")
	void dropsUnresolvedProjectName(@ProjectFile("member/pom.xml") PsiFile pom) {

		registerPom(MEMBER, "1.0.0", pom);

		CachedMetadata metadata = introspect(MEMBER, "1.0.0");

		assertThat(metadata.getProjectName()).isNull();
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<parent>
					<groupId>com.example</groupId>
					<artifactId>example-parent</artifactId>
					<version>5</version>
				</parent>
				<artifactId>member-core</artifactId>
			</project>
			""")
	@ProjectFile(name = "parent/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>example-parent</artifactId>
				<version>5</version>
				<name>Example Parent</name>
				<scm>
					<url>https://github.com/example/example-repo</url>
				</scm>
			</project>
			""")
	void ignoresParentProjectName(@ProjectFile("member/pom.xml") PsiFile member,
			@ProjectFile("parent/pom.xml") PsiFile parent) {

		registerPom(MEMBER, "1.0.0", member);
		registerPom(PARENT, "5", parent);

		CachedMetadata metadata = introspect(MEMBER, "1.0.0");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("https://github.com/example/example-repo");
		assertThat(metadata.getProjectName()).isNull();
	}

	@Test
	@ProjectFile(name = "bom/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>example-bom</artifactId>
				<version>1.0.0</version>
				<name>Example BOM</name>
				<scm>
					<url>https://github.com/example/example-repo</url>
				</scm>
			</project>
			""")
	void crossCheckResolvesMemberMetadataFromGoverningBom(@ProjectFile("bom/pom.xml") PsiFile bomPom) {

		registerPom(BOM, "1.0.0", bomPom);
		cacheBomMembership(BOM, "1.0.0", MEMBER, "1.0.0");

		CachedMetadata metadata = introspect(MEMBER, "1.0.0");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("https://github.com/example/example-repo");
		assertThat(metadata.getProjectName()).isNull();
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>member-core</artifactId>
				<version>1.0.0</version>
				<scm>
					<url>https://github.com/example/example-repo</url>
				</scm>
			</project>
			""")
	void crossCheckResolvesBomMetadataFromManagedMember(@ProjectFile("member/pom.xml") PsiFile memberPom) {

		registerPom(MEMBER, "1.0.0", memberPom);
		cacheBomMembership(BOM, "1.0.0", MEMBER, "1.0.0");

		CachedMetadata metadata = introspect(BOM, "1.0.0");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("https://github.com/example/example-repo");
	}

	@Test
	@ProjectFile(name = "bom/pom.xml", content = """
			<project>
				<groupId>com.other</groupId>
				<artifactId>aggregator-bom</artifactId>
				<version>1.0.0</version>
				<scm>
					<url>https://github.com/other/aggregator</url>
				</scm>
			</project>
			""")
	void crossCheckNeverCrossesGroupIdBoundaries(@ProjectFile("bom/pom.xml") PsiFile bomPom) {

		ArtifactId aggregatorBom = ArtifactId.of("com.other", "aggregator-bom");
		registerPom(aggregatorBom, "1.0.0", bomPom);
		cacheBomMembership(aggregatorBom, "1.0.0", MEMBER, "1.0.0");

		CachedMetadata metadata = introspect(MEMBER, "1.0.0");

		assertThat(metadata.getRepositoryUrl()).isNull();
		assertThat(metadata.getIssueTrackerUrl()).isNull();
	}

	private void registerPom(ArtifactId artifactId, String version, PsiFile pomFile) {
		poms.put(Gav.of(artifactId, version), pomFile.getVirtualFile());
	}

	private void cacheBomMembership(ArtifactId bomId, String bomVersion, ArtifactId memberId, String memberVersion) {

		CachedArtifact bomArtifact = new CachedArtifact(bomId);
		bomArtifact.setBillOfMaterials(BillOfMaterials.of(bomId, ArtifactVersion.of(bomVersion),
				Map.of(memberId, ArtifactVersion.of(memberVersion))));
		cache.addArtifacts(bomArtifact);
	}

	private CachedMetadata introspect(ArtifactId artifactId, String version) {
		return inspector.getProjectMetadata(artifactId, ArtifactVersion.of(version),
				new EmptyProgressIndicator(ModalityState.NON_MODAL));
	}

}
