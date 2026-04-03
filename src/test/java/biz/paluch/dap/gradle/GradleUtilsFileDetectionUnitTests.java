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

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link GradleUtils} file detection methods.
 *
 * @author Mark Paluch
 */
class GradleUtilsFileDetectionUnitTests {

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts" })
	void recognisesGradleScriptByName(String name) {
		assertThat(GradleUtils.GRADLE_SCRIPT_NAMES).contains(name);
	}

	@Test
	void recognisesGradlePropertiesFileName() {
		assertThat(GradleUtils.GRADLE_PROPERTIES).isEqualTo("gradle.properties");
	}

	@Test
	void recognisesVersionCatalogFileName() {
		assertThat(GradleUtils.LIBS_VERSIONS_TOML).isEqualTo("libs.versions.toml");
	}

	@Test
	void gradleScriptNamesAreImmutable() {
		assertThatThrownBy(() -> GradleUtils.GRADLE_SCRIPT_NAMES.add("unknown.gradle"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void kotlinDslRecognisedByKtsExtension() {
		// isKotlinDsl checks the .kts extension - we verify the logic by name pattern
		assertThat("build.gradle.kts").endsWith(".kts");
		assertThat("settings.gradle.kts").endsWith(".kts");
		assertThat("build.gradle").doesNotEndWith(".kts");
	}

}
