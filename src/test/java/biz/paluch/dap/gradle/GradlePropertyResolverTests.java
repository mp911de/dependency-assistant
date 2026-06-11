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

import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GradlePropertyResolver}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class GradlePropertyResolverTests {

	@Test
	@ProjectFile(name = "build.gradle", content = "// anchor")
	@ProjectFile(name = "spring-security-dependencies.gradle", content = "ext.springSecurityVersion = '6.4.0'")
	void resolvesPropertyFromCustomNamedSiblingScript(PsiFile anchor) {

		GradlePropertyResolver resolver = GradlePropertyResolver.create(anchor);

		assertThat(resolver.getProperty("springSecurityVersion")).isEqualTo("6.4.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = "ext.springVersion = '6.0.0'")
	@ProjectFile(name = "dependency-versions.gradle", content = "ext.springVersion = '6.2.0'")
	void mergesScriptsOfOneDirectoryInFileNameOrder(PsiFile anchor) {

		GradlePropertyResolver resolver = GradlePropertyResolver.create(anchor);

		// scripts merge name-sorted, the alphabetically last declaration wins
		assertThat(resolver.getProperty("springVersion")).isEqualTo("6.2.0");
	}

}
