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

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.gradle.GradleDependency.SimpleDependency;
import biz.paluch.dap.support.UpgradeSuggestion;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI tests for Kotlin DSL {@code plugins { id("…") version "…" }} version resolution used by
 * {@link VersionUpgradeLookupService} and {@link UpgradeSuggestion}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class KotlinDslPluginVersionPsiTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	void pluginsIdVersionLiteralResolvesToVersionLocationAndQualifiedUpgradeSuggestion() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				plugins {
				    id("org.springframework.boot") version "4.0.0"
				}
				""");

		KtLiteralStringTemplateEntry versionEntry = PsiTreeUtil
				.collectElementsOfType(file, KtLiteralStringTemplateEntry.class).stream()
				.filter(e -> "4.0.0".equals(e.getText())).findFirst().orElseThrow();

		KtCallExpression dependencyExpression = KotlinDslUtils.findDependencyExpression(versionEntry);
		assertThat(dependencyExpression).as("id() call for plugins block").isNotNull();

		DependencyAndVersionLocation location = KotlinDslUtils.findKotlinVersionElement(dependencyExpression,
				versionEntry,
				GradlePropertyResolver.create(file));
		assertThat(location).as("VersionLocation on plugin version literal").isNotNull();
		assertThat(location.artifactId().groupId()).isEqualTo("org.springframework.boot");
		assertThat(location.artifactId().artifactId()).isEqualTo("org.springframework.boot");
		assertThat(location.isPropertyReference()).isFalse();

		GradleDependency gd = location.dependency();
		assertThat(gd).isInstanceOf(SimpleDependency.class);
		SimpleDependency simple = (SimpleDependency) gd;
		assertThat(simple.version()).isEqualTo("4.0.0");
	}

}
