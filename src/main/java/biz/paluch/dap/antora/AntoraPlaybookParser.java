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

import java.util.ArrayList;
import java.util.List;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * YAML PSI parser for Antora playbook {@code ui.bundle.url} declarations.
 *
 * <p>Scans an Antora playbook file for {@code url} key-value pairs nested
 * directly under {@code ui.bundle} and parses each scalar value into an
 * {@link AntoraBundleUrl}. Unrelated {@code url} keys at any other path are
 * ignored, as are malformed URLs.
 *
 * @author Mark Paluch
 */
class AntoraPlaybookParser {

	/**
	 * Collect all {@code ui.bundle.url} references from the given Antora playbook.
	 * @param file the Antora playbook PSI file to scan.
	 * @return the parsed bundle URLs, possibly empty.
	 */
	List<AntoraBundleUrl> parse(PsiFile file) {

		List<AntoraBundleUrl> result = new ArrayList<>();
		SyntaxTraverser.psiTraverser(file)
				.filter(YAMLKeyValue.class)
				.filter(AntoraPlaybookParser::isBundleUrlKeyValue)
				.filter(it -> it.isValid() && it.getValue() instanceof YAMLScalar)
				.forEach(keyValue -> {

					AntoraBundleUrl url = parseUrl(keyValue);
					if (url != null) {
						result.add(url);
					}
				});

		return result;
	}

	/**
	 * Parse a single {@code url} key-value pair into an {@link AntoraBundleUrl}.
	 * @param keyValue the YAML key-value PSI element; must not be {@literal null}.
	 * @return the parsed bundle URL, or {@literal null} if the scalar value cannot
	 * be parsed.
	 */
	static @Nullable AntoraBundleUrl parseUrl(YAMLKeyValue keyValue) {
		return AntoraBundleUrl.from(keyValue.getValueText());
	}

	/**
	 * Return whether the given key-value pair represents the {@code url:} key
	 * directly under {@code ui.bundle}.
	 * @param keyValue the YAML key-value pair to test.
	 * @return {@literal true} if the key-value pair declares a
	 * {@code ui.bundle.url} entry; {@literal false} otherwise.
	 */
	static boolean isBundleUrlKeyValue(YAMLKeyValue keyValue) {

		if (!"url".equals(keyValue.getKeyText())) {
			return false;
		}

		YAMLKeyValue bundle = enclosingKeyValue(keyValue);
		if (bundle == null || !"bundle".equals(bundle.getKeyText())) {
			return false;
		}

		YAMLKeyValue ui = enclosingKeyValue(bundle);
		return ui != null && "ui".equals(ui.getKeyText());
	}

	private static @Nullable YAMLKeyValue enclosingKeyValue(YAMLKeyValue keyValue) {

		PsiElement parent = keyValue.getParent();
		if (!(parent instanceof YAMLMapping mapping)) {
			return null;
		}

		PsiElement grandParent = mapping.getParent();
		return grandParent instanceof YAMLKeyValue enclosing ? enclosing : null;
	}

}
