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

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the dependency configuration and platform function constants in {@link GradleParser}.
 *
 * @author Mark Paluch
 */
class GradleDependencyConfigsUnitTests {

	@ParameterizedTest(name = "recognises config: {0}")
	@ValueSource(strings = { "implementation", "api", "runtimeOnly", "compileOnly", "testImplementation",
			"testRuntimeOnly", "testCompileOnly", "annotationProcessor", "kapt", "ksp", "classpath" })
	void recognisesCommonDependencyConfigs(String config) {
		assertThat(GradleUtils.DEPENDENCY_CONFIGS).contains(config);
	}

	@ParameterizedTest(name = "recognises platform: {0}")
	@ValueSource(strings = { "platform", "enforcedPlatform" })
	void recognisesPlatformFunctions(String func) {
		assertThat(GradleUtils.PLATFORM_FUNCTIONS).contains(func);
	}

	@Test
	void dependencyConfigsAreImmutable() {
		assertThatThrownBy(() -> GradleUtils.DEPENDENCY_CONFIGS.add("unknown"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void platformFunctionsAreImmutable() {
		assertThatThrownBy(() -> GradleUtils.PLATFORM_FUNCTIONS.add("unknown"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void dependencyConfigsDoNotOverlapWithPlatformFunctions() {
		Set<String> overlap = new java.util.HashSet<>(GradleUtils.DEPENDENCY_CONFIGS);
		overlap.retainAll(GradleUtils.PLATFORM_FUNCTIONS);
		assertThat(overlap).isEmpty();
	}

}
