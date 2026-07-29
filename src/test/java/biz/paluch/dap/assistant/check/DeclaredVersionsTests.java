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

package biz.paluch.dap.assistant.check;

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.fixtures.TestDeclaredVersions;
import biz.paluch.dap.state.ProjectId;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link DeclaredVersions}.
 *
 * @author Mark Paluch
 */
class DeclaredVersionsTests {

	private static final ArtifactId LETTUCE = ArtifactId.of("io.lettuce", "lettuce-core");

	private static final PackageIdentity PKG = PackageIdentity.of(LETTUCE, PackageSystem.MAVEN);

	@Test
	void emptyHasNoConflictAndNoVersion() {

		DeclaredVersions declaredVersions = DeclaredVersions.empty();

		assertThat(declaredVersions.hasVersionDrift()).isFalse();
		assertThat(declaredVersions.hasDeclarationDrift()).isFalse();
		assertThat(declaredVersions.hasDrift()).isFalse();
		assertThat(declaredVersions.hasVersion()).isFalse();
	}

	@Test
	void flagsConflictWhenDeclaredVersionsDiffer() {

		VirtualFile a = new MockVirtualFile("conflict-a/build.gradle", "// test");
		VirtualFile b = new MockVirtualFile("conflict-b/build.gradle", "// test");
		DeclaredVersions declaredVersions = TestDeclaredVersions.from(site(a, "com.acme", "app", "7.4.1.RELEASE"),
				site(b, "com.acme", "lib", "7.5.0.RELEASE"));

		assertThat(declaredVersions.hasVersionDrift()).isTrue();
		assertThat(declaredVersions.versions()).extracting(Object::toString)
				.containsExactlyInAnyOrder("7.4.1.RELEASE", "7.5.0.RELEASE");
	}

	@Test
	void flagsDeclarationDriftWhenInlineAndPropertyVersionsAgree() {

		VirtualFile a = new MockVirtualFile("drift-a/build.gradle", "// test");
		VirtualFile b = new MockVirtualFile("drift-b/build.gradle", "// test");
		DeclaredVersions declaredVersions = TestDeclaredVersions.from(
				site(a, "7.4.1.RELEASE", VersionSource.property("lettuce.version")),
				site(b, "7.4.1.RELEASE", VersionSource.declared("7.4.1.RELEASE")));

		assertThat(declaredVersions.hasVersionDrift()).isFalse();
		assertThat(declaredVersions.hasDeclarationDrift()).isTrue();
		assertThat(declaredVersions.hasDrift()).isTrue();
	}

	@Test
	void resolvesGitRefsBeforeComparison() {

		VirtualFile file = new MockVirtualFile("git-ref/build.gradle", "// test");
		DeclaredVersions declaredVersions = DeclaredVersions.from(List.of(site(file, new GitRef("main"))),
				ref -> ArtifactVersion.of("7.5.0.RELEASE"), null);

		assertThat(declaredVersions.versions()).extracting(Object::toString).containsExactly("7.5.0.RELEASE");
		assertThat(declaredVersions.getHighestDeclaredVersion()).isEqualTo("7.5.0.RELEASE");
	}

	@Test
	void showsCoordinateForConflictLocations() {

		VirtualFile a = new MockVirtualFile("prefix-a/build.gradle", "// test");
		VirtualFile b = new MockVirtualFile("prefix-b/build.gradle", "// test");
		DeclaredVersions declaredVersions = TestDeclaredVersions.from(site(a, "com.acme", "app", "7.4.1.RELEASE"),
				site(b, "com.acme", "lib", "7.5.0.RELEASE"));
		List<String> conflicts = new ArrayList<>();

		declaredVersions.forEachDrift((version, location) -> conflicts.add(version + "@" + location));

		assertThat(conflicts).containsExactlyInAnyOrder("7.4.1.RELEASE@com.acme:app", "7.5.0.RELEASE@com.acme:lib");
	}

	@Test
	void showsFilePathWhenProjectAbsent() {

		VirtualFile a = new MockVirtualFile("moduleA/build.gradle", "// test");
		VirtualFile b = new MockVirtualFile("moduleB/build.gradle", "// test");
		DeclaredVersions declaredVersions = TestDeclaredVersions.from(site(a, "7.4.1.RELEASE"),
				site(b, "7.5.0.RELEASE"));
		List<String> conflicts = new ArrayList<>();

		declaredVersions.forEachDrift((version, location) -> conflicts.add(version + "@" + location));

		assertThat(conflicts).containsExactlyInAnyOrder("7.4.1.RELEASE@" + a.getPath(),
				"7.5.0.RELEASE@" + b.getPath());
	}

	@Test
	void versionDriftToolTipEscapesLocations() {

		VirtualFile a = new MockVirtualFile("safe/build.gradle", "// test");
		VirtualFile b = new MockVirtualFile("<img src=x>/build.gradle", "// test");
		DeclaredVersions declaredVersions = TestDeclaredVersions.from(site(a, "7.4.1.RELEASE"),
				site(b, "7.5.0.RELEASE"));

		String toolTip = declaredVersions.getVersionDriftToolTip(ArtifactVersion.of("7.4.1.RELEASE")).toString();

		assertThat(toolTip).contains("&lt;img").doesNotContain("<img");
	}

	@Test
	void declarationDriftToolTipEscapesLocations() {

		VirtualFile a = new MockVirtualFile("<img src=x>/build.gradle", "// test");
		VirtualFile b = new MockVirtualFile("drift-b/build.gradle", "// test");
		DeclaredVersions declaredVersions = TestDeclaredVersions.from(
				site(a, "7.4.1.RELEASE", VersionSource.property("lettuce.version")),
				site(b, "7.4.1.RELEASE", VersionSource.declared("7.4.1.RELEASE")));

		String toolTip = declaredVersions.getDeclarationDriftToolTip().toString();

		assertThat(toolTip).contains("&lt;img").doesNotContain("<img");
	}

	private static DeclarationSite site(VirtualFile file, String groupId, String artifactId, String version) {
		return new DeclarationSite(file, ProjectId.of(groupId, artifactId), dependency(ArtifactVersion.of(version)));
	}

	private static DeclarationSite site(VirtualFile file, String version) {
		return site(file, ArtifactVersion.of(version));
	}

	private static DeclarationSite site(VirtualFile file, ArtifactVersion version) {
		return new DeclarationSite(file, ProjectId.of(file), dependency(version));
	}

	private static DeclarationSite site(VirtualFile file, String version, VersionSource versionSource) {
		return new DeclarationSite(file, ProjectId.of(file), dependency(ArtifactVersion.of(version), versionSource));
	}

	private static Dependency dependency(ArtifactVersion version) {
		return new Dependency(PKG, version);
	}

	private static Dependency dependency(ArtifactVersion version, VersionSource versionSource) {

		Dependency dependency = dependency(version);
		dependency.addVersionSource(versionSource);
		return dependency;
	}

}
