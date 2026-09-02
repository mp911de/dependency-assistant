/*
 * Copyright 2026-present the original author or authors.
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
import biz.paluch.dap.support.PropertyValue;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link KotlinDslExtraParser}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class KotlinDslExtraParserTests {

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			"2.0.3".also { extra["springModulithVersion"] = it }

			extra["buildStringKey"] = buildString {
			        append("2.0.3")
			    }

			extra["tripleKey"] = \"""2.0.3\"""
			""")
	void kotlinExtraPropertyAlternateFormatsAreCollected(PsiFile file) {

		Map<String, String> props = KotlinDslExtraParser.getExtraProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.3").containsEntry("buildStringKey", "2.0.3")
				.containsEntry("tripleKey", "2.0.3");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["springModulithVersion"] = "2.0.4"
			extra["lombokVersion"] = "1.18.36"
			""")
	void extraPropertiesAreCollected(PsiFile file) {

		Map<String, String> props = KotlinDslExtraParser.getExtraProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.4").containsEntry("lombokVersion", "1.18.36");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			"2.0.3".also { extra["springModulithVersion"] = it }
			""")
	void extraPropertyViaAlsoWithItIsCollected(PsiFile file) {

		Map<String, String> props = KotlinDslExtraParser.getExtraProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.3");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["springModulithVersion"] = buildString {
			        append("2.0.3")
			    }
			""")
	void extraPropertyViaBuildStringAppendIsCollected(PsiFile file) {

		Map<String, String> props = KotlinDslExtraParser.getExtraProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.3");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["springModulithVersion"] = \"""2.0.3\"""
			""")
	void extraPropertyViaTripleQuotedStringIsCollected(PsiFile file) {

		Map<String, String> props = KotlinDslExtraParser.getExtraProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.3");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val springVersion = "6.2.0"
			val lombokVersion by extra("1.18.36")
			val unversioned = 42
			""")
	void scriptValPropertiesAreCollected(PsiFile file) {

		Map<String, PropertyValue> props = KotlinDslExtraParser.parseValProperties(file);

		assertThat(props).containsOnlyKeys("springVersion", "lombokVersion");
		assertThat(props.get("springVersion").getValue()).isEqualTo("6.2.0");
		assertThat(props.get("springVersion").getValueLiteral().getText()).isEqualTo("\"6.2.0\"");
		assertThat(props.get("lombokVersion").getValue()).isEqualTo("1.18.36");
		assertThat(props.get("lombokVersion").getValueLiteral().getText()).isEqualTo("\"1.18.36\"");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val topLevel = "1.0"

			tasks.register("hello") {
			    val inLambda = "2.0"
			    val inLambdaExtra by extra("2.1")
			}

			fun helper() {
			    val inFunction = "3.0"
			}

			object Versions {
			    val inObject = "4.0"
			}
			""")
	void nestedValPropertiesAreIgnored(PsiFile file) {

		Map<String, PropertyValue> props = KotlinDslExtraParser.parseValProperties(file);

		assertThat(props).containsOnlyKeys("topLevel");
	}

}
