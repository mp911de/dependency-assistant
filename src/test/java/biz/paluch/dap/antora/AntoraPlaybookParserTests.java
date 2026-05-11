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

package biz.paluch.dap.antora;

import java.util.List;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level integration tests for {@link AntoraPlaybookParser}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class AntoraPlaybookParserTests {

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			site:
			  title: Spring
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip
			""")
	void parsesBundleUrl(PsiFile playbookFile) {

		List<AntoraBundleUrl> result = new AntoraPlaybookParser().parse(playbookFile);

		assertThat(result).singleElement().satisfies(url -> {
			assertThat(url.host()).isEqualTo("github.com");
			assertThat(url.owner()).isEqualTo("spring-io");
			assertThat(url.repository()).isEqualTo("antora-ui-spring");
			assertThat(url.version()).isEqualTo("v0.4.26");
		});
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			url: https://example.com/o/r/releases/download/v1/u.zip
			""")
	void ignoresTopLevelUrlKey(PsiFile playbookFile) {
		assertThat(new AntoraPlaybookParser().parse(playbookFile)).isEmpty();
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			ui:
			  url: https://example.com/o/r/releases/download/v1/u.zip
			""")
	void ignoresUiUrlWithoutBundleParent(PsiFile playbookFile) {
		assertThat(new AntoraPlaybookParser().parse(playbookFile)).isEmpty();
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: not-a-valid-url
			""")
	void ignoresMalformedUrl(PsiFile playbookFile) {
		assertThat(new AntoraPlaybookParser().parse(playbookFile)).isEmpty();
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			content:
			  sources:
			    - url: https://example.com/sources.git
			""")
	void ignoresUnrelatedUrlKeys(PsiFile playbookFile) {
		assertThat(new AntoraPlaybookParser().parse(playbookFile)).isEmpty();
	}

}
