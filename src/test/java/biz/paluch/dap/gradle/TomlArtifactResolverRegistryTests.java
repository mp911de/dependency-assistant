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

package biz.paluch.dap.gradle;

import java.util.Map;

import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TomlArtifactResolver} registry-based catalog lookup.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class TomlArtifactResolverRegistryTests {

	@Test
	@ProjectFile(name = "build.gradle.kts")
	void knownAliasResolvesPathFromRegistry(PsiFile file) {

		VersionCatalogRegistry registry = new VersionCatalogRegistry(
				Map.of("tools", "gradle/tools.versions.toml"), "tools");
		TomlArtifactResolver resolver = new TomlArtifactResolver(file.getProject(), file, registry);

		TomlReference reference = TomlReference.of("tools", null, "some-lib");

		assertThat(resolver.findCatalogForReference(reference, file.getVirtualFile())).isNull();
	}

	@Test
	@ProjectFile(name = "build.gradle.kts")
	void unknownAliasReturnsNull(PsiFile file) {

		VersionCatalogRegistry registry = new VersionCatalogRegistry(
				Map.of("tools", "gradle/tools.versions.toml"), "tools");
		TomlArtifactResolver resolver = new TomlArtifactResolver(file.getProject(), file, registry);

		TomlReference reference = TomlReference.of("unknown", null, "some-lib");

		assertThat(resolver.findCatalogForReference(reference, file.getVirtualFile())).isNull();
	}

}
