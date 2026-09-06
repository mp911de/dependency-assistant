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
 * Parse Antora {@code ui.bundle.url} scalars into detached bundle references.
 * <p>Malformed URLs and invalid PSI are ignored. Callers provide PSI read
 * access.
 *
 * @author Mark Paluch
 */
class AntoraPlaybookParser {

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

	static @Nullable AntoraBundleUrl parseUrl(YAMLKeyValue keyValue) {
		return AntoraBundleUrl.from(keyValue.getValueText());
	}

	/**
	 * Return whether the key is {@code url} directly under {@code ui.bundle}.
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
