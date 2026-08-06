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
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.fixtures.Coordinates;
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

	Cache cache = new Cache();

	Map<Coordinates, VirtualFile> pomsByCoordinates = new HashMap<>();

	MavenPomMetadataIntrospector inspector;

	@BeforeEach
	void setUp(Project project) {

		inspector = new MavenPomMetadataIntrospector(project, cache) {

			@Override
			protected VirtualFile findPom(ArtifactId artifactId, String version) {
				return pomsByCoordinates.get(Coordinates.of(artifactId, version));
			}

		};
	}

	@Test
	@ProjectFile(name = "member/v1/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>member-core</artifactId>
				<version>1.0</version>
				<scm><url>https://github.com/example/member-1.0</url></scm>
			</project>
			""")
	@ProjectFile(name = "member/v2/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>member-core</artifactId>
				<version>2.0.0</version>
				<scm><url>https://github.com/example/member-1.0.0</url></scm>
			</project>
			""")
	void resolvesPomByFullVersionedCoordinates(@ProjectFile("member/v1/pom.xml") PsiFile versionOne,
			@ProjectFile("member/v2/pom.xml") PsiFile versionTwo) {

		registerPom("com.example:member-core:1.0", versionOne);
		registerPom("com.example:member-core:1.0.0", versionTwo);

		assertThat(introspect("com.example:member-core:1.0").getRepositoryUrl())
				.isEqualTo("https://github.com/example/member-1.0");
		assertThat(introspect("com.example:member-core:1.0.0").getRepositoryUrl())
				.isEqualTo("https://github.com/example/member-1.0.0");
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

		registerPom("com.example:member-core:1.0.0", pom);

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

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

		registerPom("com.example:member-core:1.0.0", pom);

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

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

		registerPom("com.example:member-core:1.0.0", pom);

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

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

		registerPom("com.example:member-core:1.0.0", member);
		registerPom("com.example:example-parent:5", parent);

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

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

		registerPom("com.example:member-core:1.0.0", pom);

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

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

		registerPom("com.example:member-core:1.0.0", member);
		registerPom("com.example:example-parent:5", parent);

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

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

		registerPom("com.example:cycle-a:1.0.0", cycleA);
		registerPom("com.example:cycle-b:1.0.0", cycleB);

		CachedMetadata metadata = introspect("com.example:cycle-a:1.0.0");

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

		registerPom("com.example:member-core:1.0.0", pom);

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

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

		registerPom("com.example:member-core:1.0.0", pom);

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

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

		registerPom("com.example:member-core:1.0.0", pom);

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

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

		registerPom("com.example:member-core:1.0.0", pom);

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

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

		registerPom("com.example:member-core:1.0.0", member);
		registerPom("com.example:example-parent:5", parent);

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

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

		registerPom("com.example:example-bom:1.0.0", bomPom);
		cacheBomMembership("com.example:example-bom:1.0.0",
				"com.example:member-core:1.0.0");

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

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

		registerPom("com.example:member-core:1.0.0", memberPom);
		cacheBomMembership("com.example:example-bom:1.0.0",
				"com.example:member-core:1.0.0");

		CachedMetadata metadata = introspect("com.example:example-bom:1.0.0");

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

		registerPom("com.other:aggregator-bom:1.0.0", bomPom);
		cacheBomMembership("com.other:aggregator-bom:1.0.0", "com.example:member-core:1.0.0");

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

		assertThat(metadata.getRepositoryUrl()).isNull();
		assertThat(metadata.getIssueTrackerUrl()).isNull();
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>member-core</artifactId>
				<version>1.0.0</version>
				<description>Member component description</description>
				<scm><url>https://github.com/example/example-repo</url></scm>
				<issueManagement><system>JIRA</system></issueManagement>
			</project>
			""")
	void capturesDescriptionAndUsesIssueManagementSystemHint(@ProjectFile("member/pom.xml") PsiFile pom) {

		registerPom("com.example:member-core:1.0.0", pom);

		CachedMetadata metadata = introspect("com.example:member-core:1.0.0");

		assertThat(metadata.getProjectDescription()).isEqualTo("Member component description");
		assertThat(metadata.getIssueTrackerUrl()).isNull();
	}

	@Test
	@ProjectFile(name = "member/pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>member-core</artifactId>
				<version>1.0.0</version>
				<issueManagement><url>file:///tmp/issues.html</url></issueManagement>
			</project>
			""")
	void rejectsNonHttpIssueManagementUrl(@ProjectFile("member/pom.xml") PsiFile pom) {

		registerPom("com.example:member-core:1.0.0", pom);

		assertThat(introspect("com.example:member-core:1.0.0").getIssueTrackerUrl()).isNull();
	}

	private void registerPom(String coordinates, PsiFile pomFile) {
		pomsByCoordinates.put(Coordinates.of(coordinates), pomFile.getVirtualFile());
	}

	private void cacheBomMembership(String bomgav, String member) {

		Coordinates bom = Coordinates.of(bomgav);
		CachedArtifact bomArtifact = new CachedArtifact(bom.getArtifactId());
		bomArtifact.setBillOfMaterials(Coordinates.bom(bom.toString(), it -> it.member(member)), 1_000L);
		cache.addArtifacts(bomArtifact);
	}

	private CachedMetadata introspect(String gav) {
		Coordinates coordinates = Coordinates.of(gav);
		return inspector.getProjectMetadata(coordinates.getArtifactId(), coordinates.getVersion(),
				new EmptyProgressIndicator(ModalityState.NON_MODAL));
	}

}
