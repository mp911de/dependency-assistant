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

package biz.paluch.dap.extension;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Integration tests for {@link CodeInsightFixtureExtension}.
 *
 * @author Mark Paluch
 */
class CodeInsightFixtureExtensionTests {

	@Test
	@CodeInsightFixtureTests
	void injectsFixtureMethodParameters(CodeInsightTestFixture fixture, IdeaProjectTestFixture projectFixture) {

		assertThat(fixture).isNotNull();
		assertThat(projectFixture.getProject()).isSameAs(fixture.getProject());
	}

}
