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
 * Tests for {@link GroovyDslExtParser}.
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GroovyDslExtParserTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	void extSetPropertyIsCollected() {

		PsiFile file = fixture.configureByText("build.gradle", """
				ext {
				    set('springModulithVersion', "2.0.4")
				}
				""");

		Map<String, String> props = GroovyDslExtParser.getExtProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.4");
	}

	@Test
	void extAssignmentPropertyIsCollected() {

		PsiFile file = fixture.configureByText("build.gradle", """
				ext {
				    springVersion = '6.1.0'
				    lombokVersion = '1.18.36'
				}
				""");

		Map<String, String> props = GroovyDslExtParser.getExtProperties(file);

		assertThat(props).containsEntry("springVersion", "6.1.0").containsEntry("lombokVersion", "1.18.36");
	}

	@Test
	void extDotAssignmentPropertyIsCollected() {

		PsiFile file = fixture.configureByText("build.gradle", """
				ext.springBootVersion = '3.5.0'
				""");

		Map<String, String> props = GroovyDslExtParser.getExtProperties(file);

		assertThat(props).containsEntry("springBootVersion", "3.5.0");
	}

}
