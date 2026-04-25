/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.support.PropertyValue;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link GroovyDslExtParser}.
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GroovyDslExtParserTests {

	@Test
	@EditorFile(name = "build.gradle", content = """
			ext {
			    set('springModulithVersion', "2.0.4")
			}
			""")
	void extSetPropertyIsCollected(PsiFile buildFile) {

		Map<String, String> props = GroovyDslExtParser.getExtProperties(buildFile);

		assertThat(props).containsEntry("springModulithVersion", "2.0.4");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			ext {
			    springVersion = '6.1.0'
			    lombokVersion = '1.18.36'
			}
			""")
	void extAssignmentPropertyIsCollected(PsiFile buildFile) {

		Map<String, String> props = GroovyDslExtParser.getExtProperties(buildFile);

		assertThat(props).containsEntry("springVersion", "6.1.0").containsEntry("lombokVersion", "1.18.36");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			ext.springBootVersion = '3.5.0'
			""")
	void extDotAssignmentPropertyIsCollected(PsiFile buildFile) {

		Map<String, String> props = GroovyDslExtParser.getExtProperties(buildFile);

		assertThat(props).containsEntry("springBootVersion", "3.5.0");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			val junit = "6.0.0"
			""")
	void considersLocalVariable(PsiFile buildFile) {

		Map<String, PropertyValue> props = GroovyDslExtParser.parseLocalVariables(buildFile);

		assertThat(props).containsKey("junit");
		assertThat(props.get("junit").getValue()).isEqualTo("6.0.0");
	}

}
