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

import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.support.yaml.YamlVersionSite;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * Utilities for identifying GitHub Actions files.
 *
 * @author Mark Paluch
 */
class GitHubUtils {

	private static final String WORKFLOWS_PATH_FRAGMENT = ".github/workflows/";

	private static final Predicate<YAMLKeyValue> IS_USES_KEY = kv -> "uses".equals(kv.getKeyText());

	/**
	 * Render ArtifactId as string.
	 */
	static String toString(ArtifactId artifactId) {
		if (artifactId instanceof GitHubAction action) {
			return action.toString();
		}
		return artifactId.groupId() + "/" + artifactId.artifactId();
	}

	/**
	 * Return whether the given file is supported by the GitHub Actions integration.
	 * <p>A supported file must be a YAML file and either live under
	 * {@code .github/workflows/} or be named {@code action.yml} or
	 * {@code action.yaml}.
	 * @param file the PSI file to test.
	 * @return {@literal true} if this file is supported.
	 */
	static boolean isWorkflowFile(@Nullable PsiFile file) {
		if (file == null) {
			return false;
		}
		return file.getVirtualFile() != null && isWorkflowFile(file.getVirtualFile());
	}

	/**
	 * Return whether the given file is supported by the GitHub Actions integration.
	 * <p>A supported file must be a YAML file and either live under
	 * {@code .github/workflows/} or be named {@code action.yml} or
	 * {@code action.yaml}.
	 * @param file the file to test.
	 * @return {@literal true} if this file is supported.
	 */
	static boolean isWorkflowFile(VirtualFile file) {
		if (!isYamlFile(file)) {
			return false;
		}
		if (file.getNameWithoutExtension().equals("action")) {
			return true;
		}
		return file.getPath().contains(WORKFLOWS_PATH_FRAGMENT);
	}

	private static boolean isYamlFile(VirtualFile file) {
		String extension = file.getExtension();
		return StringUtils.hasText(extension)
				&& (extension.equalsIgnoreCase("yaml") || extension.equalsIgnoreCase("yml"));
	}

	/**
	 * Compute the {@link TextRange} that covers only the ref portion (after
	 * {@code @}) of the {@code uses:} scalar that owns the given element.
	 *
	 * <p>Used by both the GitHub annotator and line marker provider so they
	 * highlight a consistent sub-range and don't include the
	 * {@code owner/repository} prefix.
	 *
	 * @param element the PSI element that lives inside (or is) the {@code uses:}
	 * value scalar.
	 * @return the ref-only text range, or the element's own text range if no
	 * {@code uses:} scalar can be located from the element.
	 */
	public static TextRange getVersionRange(PsiElement element) {

		YAMLScalar scalar = findUsesScalar(element);
		if (scalar == null) {
			return element.getTextRange();
		}

		// Use raw text to keep offsets aligned with the document, even for quoted
		// scalars.
		String text = scalar.getText();
		int atIndex = text.indexOf('@');
		if (atIndex < 0) {
			return scalar.getTextRange();
		}

		TextRange scalarRange = scalar.getTextRange();
		int refStart = scalarRange.getStartOffset() + atIndex + 1;
		int refEnd = scalarRange.getEndOffset();
		// Trim a trailing matching quote when the scalar is quoted.
		if (scalar instanceof YAMLQuotedText) {
			refEnd -= 1;
		}

		return new TextRange(refStart, refEnd);
	}

	/**
	 * Return the {@link YAMLScalar} that is the value of a {@code uses:} key.
	 * @param element the element at the cursor position.
	 * @return the scalar, or {@literal null} if it is not the value of such a key.
	 */
	public static @Nullable YAMLScalar findUsesScalar(PsiElement element) {
		YamlVersionSite site = YamlVersionSite.locate(element, IS_USES_KEY);
		return site != null ? site.scalar() : null;
	}
}
