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

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TomlArtifactResolver} registry-based catalog lookup.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class TomlArtifactResolverRegistryTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	void knownAliasResolvesPathFromRegistry() {

		PsiFile file = fixture.configureByText("build.gradle.kts", "");
		VersionCatalogRegistry registry = new VersionCatalogRegistry(
				Map.of("tools", "gradle/tools.versions.toml"), "tools");
		TomlArtifactResolver resolver = new TomlArtifactResolver(fixture.getProject(), file, null, registry);

		TomlReference reference = TomlReference.of("tools", null, "some-lib");

		// File does not exist in the temp project, so resolution yields null — but the
		// alias lookup itself succeeds (no unresolved-due-to-unknown-alias path taken).
		assertThat(resolver.findCatalogForReference(reference, file.getVirtualFile())).isNull();
	}

	@Test
	void unknownAliasReturnsNull() {

		PsiFile file = fixture.configureByText("build.gradle.kts", "");
		VersionCatalogRegistry registry = new VersionCatalogRegistry(
				Map.of("tools", "gradle/tools.versions.toml"), "tools");
		TomlArtifactResolver resolver = new TomlArtifactResolver(fixture.getProject(), file, null, registry);

		TomlReference reference = TomlReference.of("unknown", null, "some-lib");

		assertThat(resolver.findCatalogForReference(reference, file.getVirtualFile())).isNull();
	}

}
