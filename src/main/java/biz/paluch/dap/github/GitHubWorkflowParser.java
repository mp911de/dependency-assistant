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

package biz.paluch.dap.github;

import java.util.ArrayList;
import java.util.List;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * YAML PSI parser for GitHub Actions workflow {@code uses:} declarations.
 *
 * <p>Scans a YAML workflow file for all {@code uses:} key-value pairs, parses
 * the scalar value into a {@link UsesRepositoryAction}, and collects the
 * results. References that represent local paths ({@code ./} or {@code ../}) or
 * Docker images ({@code docker://}) are silently ignored. References without an
 * {@code owner/repository@ref} structure are also ignored.
 *
 * @author Mark Paluch
 */
class GitHubWorkflowParser {

	/**
	 * Collect all {@code uses:} references from the given workflow file.
	 * @param file the YAML workflow PSI file to scan.
	 * @return the parsed references, possibly empty.
	 */
	public List<UsesRepositoryAction> parse(PsiFile file) {

		List<UsesRepositoryAction> result = new ArrayList<>();
		SyntaxTraverser.psiTraverser(file).filter(YAMLKeyValue.class).forEach(keyValue -> {

			if ("uses".equals(keyValue.getKeyText())) {
				UsesRepositoryAction ref = parseUses(keyValue);
				if (ref != null) {
					result.add(ref);
				}
			}
		});

		return result;
	}

	/**
	 * Parse a single {@code uses:} key-value pair into a
	 * {@link UsesRepositoryAction}.
	 * @param keyValue the YAML key-value PSI element.
	 * @return the parsed reference, or {@code null} if the value cannot be parsed.
	 */
	public static @Nullable UsesRepositoryAction parseUses(YAMLKeyValue keyValue) {

		PsiElement value = keyValue.getValue();
		if (!(value instanceof YAMLScalar scalar) || !scalar.isValid()) {
			return null;
		}

		return parseUses(scalar.getTextValue());
	}

	/**
	 * Parse the scalar text of a {@code uses:} value.
	 * @param text the raw uses value string.
	 * @return the parsed reference, or {@code null} if the value should be ignored.
	 */
	public static @Nullable UsesRepositoryAction parseUses(String text) {
		return GitHubAction.isValidUsage(text) ? new UsesRepositoryAction(GitHubAction.from(text)) : null;
	}

}
