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

package biz.paluch.dap.assistant;

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.state.ProjectId;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DeclaredVersions}.
 *
 * @author Mark Paluch
 */
class DeclaredVersionsTests {

	@Test
	void noneHasNoConflictAndNoVersion() {

		DeclaredVersions declaredVersions = DeclaredVersions.none();

		assertThat(declaredVersions.hasVersionDrift()).isFalse();
		assertThat(declaredVersions.hasVersion()).isFalse();
	}

	@Test
	void flagsConflictWhenDeclaredVersionsDiffer() {

		VirtualFile a = new MockVirtualFile("conflict-a/build.gradle", "// test");
		VirtualFile b = new MockVirtualFile("conflict-b/build.gradle", "// test");
		DeclaredVersions declaredVersions = DeclaredVersions.from(List.of(site(a, "com.acme", "app", "7.4.1.RELEASE"),
				site(b, "com.acme", "lib", "7.5.0.RELEASE")), ref -> null);

		assertThat(declaredVersions.hasVersionDrift()).isTrue();
		assertThat(declaredVersions.versions()).extracting(Object::toString)
				.containsExactlyInAnyOrder("7.4.1.RELEASE", "7.5.0.RELEASE");
	}

	@Test
	void resolvesGitRefsBeforeComparison() {

		VirtualFile file = new MockVirtualFile("git-ref/build.gradle", "// test");
		DeclaredVersions declaredVersions = DeclaredVersions.from(List.of(site(file, new GitRef("main"))),
				ref -> ArtifactVersion.of("7.5.0.RELEASE"));

		assertThat(declaredVersions.versions()).extracting(Object::toString).containsExactly("7.5.0.RELEASE");
		assertThat(declaredVersions.getHighestDeclaredVersion()).isEqualTo(ArtifactVersion.of("7.5.0.RELEASE"));
	}

	@Test
	void stripsSharedProjectPrefixFromConflictLocations() {

		VirtualFile a = new MockVirtualFile("prefix-a/build.gradle", "// test");
		VirtualFile b = new MockVirtualFile("prefix-b/build.gradle", "// test");
		DeclaredVersions declaredVersions = DeclaredVersions.from(List.of(site(a, "com.acme", "app", "7.4.1.RELEASE"),
				site(b, "com.acme", "lib", "7.5.0.RELEASE")), ref -> null);
		List<String> conflicts = new ArrayList<>();

		declaredVersions.forEachDrift((version, location) -> conflicts.add(version + "@" + location));

		assertThat(conflicts).containsExactlyInAnyOrder("7.4.1.RELEASE@app", "7.5.0.RELEASE@lib");
	}

	private static DeclarationSite site(VirtualFile file, String groupId, String artifactId, String version) {
		return new DeclarationSite(file, ProjectId.of(groupId, artifactId), dependency(ArtifactVersion.of(version)));
	}

	private static DeclarationSite site(VirtualFile file, ArtifactVersion version) {
		return new DeclarationSite(file, ProjectId.of(file), dependency(version));
	}

	private static Dependency dependency(ArtifactVersion version) {
		return new Dependency(ArtifactId.of("io.lettuce", "lettuce-core"), version);
	}

}
