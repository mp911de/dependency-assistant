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
 * Parses repository-backed {@code uses:} references from YAML.
 * <p>Local actions, Docker images, and unsupported values are ignored.
 *
 * @author Mark Paluch
 * @see GitHubAction
 */
class GitHubWorkflowParser {

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
	 * Parse a scalar value, or return {@literal null} if unsupported or invalid.
	 */
	public static @Nullable UsesRepositoryAction parseUses(YAMLKeyValue keyValue) {

		PsiElement value = keyValue.getValue();
		if (!(value instanceof YAMLScalar scalar) || !scalar.isValid()) {
			return null;
		}

		return parseUses(scalar.getTextValue());
	}

	/**
	 * Parse a repository action, or return {@literal null} if unsupported.
	 */
	public static @Nullable UsesRepositoryAction parseUses(String text) {
		return GitHubAction.isValidUsage(text) ? new UsesRepositoryAction(GitHubAction.from(text)) : null;
	}

}
