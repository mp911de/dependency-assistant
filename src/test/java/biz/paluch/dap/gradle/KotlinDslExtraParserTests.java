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

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link KotlinDslExtraParser}.
 * @author Mark Paluch
 */
@RunInEdt(writeIntent = true)
class KotlinDslExtraParserTests {

	private CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() throws Exception {
		TestFixtureBuilder<IdeaProjectTestFixture> builder = IdeaTestFixtureFactory.getFixtureFactory()
				.createLightFixtureBuilder(new LightProjectDescriptor(), getClass().getSimpleName());
		fixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(builder.getFixture());
		fixture.setUp();
	}

	@AfterEach
	void tearDown() throws Exception {
		fixture.tearDown();
		fixture = null;
	}

	@Test
	void kotlinExtraPropertyAlternateFormatsAreCollected() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				"2.0.3".also { extra["springModulithVersion"] = it }

				extra["buildStringKey"] = buildString {
				        append("2.0.3")
				    }

				extra["tripleKey"] = \"""2.0.3\"""
				""");

		Map<String, String> props = KotlinDslExtraParser.getExtraProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.3").containsEntry("buildStringKey", "2.0.3")
				.containsEntry("tripleKey", "2.0.3");
	}

	@Test
	void extraPropertiesAreCollected() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				extra["springModulithVersion"] = "2.0.4"
				extra["lombokVersion"] = "1.18.36"
				""");

		Map<String, String> props = KotlinDslExtraParser.getExtraProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.4").containsEntry("lombokVersion", "1.18.36");
	}

	@Test
	void extraPropertyViaAlsoWithItIsCollected() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				"2.0.3".also { extra["springModulithVersion"] = it }
				""");

		Map<String, String> props = KotlinDslExtraParser.getExtraProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.3");
	}

	@Test
	void extraPropertyViaBuildStringAppendIsCollected() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				extra["springModulithVersion"] = buildString {
				        append("2.0.3")
				    }
				""");

		Map<String, String> props = KotlinDslExtraParser.getExtraProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.3");
	}

	@Test
	void extraPropertyViaTripleQuotedStringIsCollected() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				extra["springModulithVersion"] = \"""2.0.3\"""
				""");

		Map<String, String> props = KotlinDslExtraParser.getExtraProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.3");
	}

}
