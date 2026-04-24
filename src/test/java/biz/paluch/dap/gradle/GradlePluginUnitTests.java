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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GradlePlugin}.
 *
 * @author Mark Paluch
 */
public class GradlePluginUnitTests {

	@Test
	void isValidPluginId_acceptsNormalId() {
		assertThat(GradlePlugin.isValidPluginId("org.springframework.boot")).isTrue();
	}

	@Test
	void isValidPluginId_rejectsPathTraversal() {
		assertThat(GradlePlugin.isValidPluginId("../evil")).isFalse();
	}

	@Test
	void isValidPluginId_rejectsEmpty() {
		assertThat(GradlePlugin.isValidPluginId("")).isFalse();
	}

	@Test
	void isValidPluginId_rejectsUrlSpecial() {
		assertThat(GradlePlugin.isValidPluginId("org@attacker.com/x")).isFalse();
	}

}
