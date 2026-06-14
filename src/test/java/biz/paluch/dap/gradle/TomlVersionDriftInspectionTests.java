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

import java.util.List;

import biz.paluch.dap.assistant.DependencyVersionDriftInspection;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.fixtures.Inspections;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Highlighting integration tests for {@link DependencyVersionDriftInspection}
 * on Gradle version catalogs.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class TomlVersionDriftInspectionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			jooqNew = "3.21.2"

			[libraries]
			jooq = { module = "org.jooq:jooq", version = "3.14.16" }
			jooqLatest = { module = "org.jooq:jooq", version.ref = "jooqNew" }
			""")
	void reportsDriftOnTheAliasNotTheSharedVersionsEntry(PsiFile toml) {

		GradleFixtures.analyze(toml);
		Inspections.registerDependency(fixture.getProject(), "other", "org.jooq", "jooq", "3.99.0");

		List<String> highlighted = Inspections.inspect(fixture.getProject(), toml).stream()
				.map(problem -> problem.getPsiElement().getText())
				.toList();

		assertThat(highlighted).containsExactlyInAnyOrder("\"3.14.16\"", "\"jooqNew\"");
	}

}
