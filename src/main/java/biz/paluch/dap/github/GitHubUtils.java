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
 * Utilities for identifying GitHub Actions files and locating repository-backed
 * {@code uses:} refs in YAML PSI.
 *
 * @author Mark Paluch
 */
class GitHubUtils {

	private static final String WORKFLOWS_PATH_FRAGMENT = ".github/workflows/";

	private static final Predicate<YAMLKeyValue> IS_USES_KEY = kv -> "uses".equals(kv.getKeyText());

	/**
	 * Render an artifact as {@code owner/repository}.
	 */
	static String toString(ArtifactId artifactId) {
		if (artifactId instanceof GitHubAction action) {
			return action.toString();
		}
		return artifactId.groupId() + "/" + artifactId.artifactId();
	}

	/**
	 * Return whether the PSI file has a supported workflow file.
	 * @see #isWorkflowFile(VirtualFile)
	 */
	static boolean isWorkflowFile(@Nullable PsiFile file) {
		if (file == null) {
			return false;
		}
		return file.getVirtualFile() != null && isWorkflowFile(file.getVirtualFile());
	}

	/**
	 * Recognize YAML files under {@code .github/workflows/} and files named
	 * {@code action.yml} or {@code action.yaml}.
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
	 * Return the document range of a {@code uses:} ref after {@code @}.
	 * <p>Falls back to the scalar range if no separator exists, or the element
	 * range if no {@code uses:} scalar is found.
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
	 * Find the owning {@code uses:} scalar, or return {@literal null} if absent.
	 */
	public static @Nullable YAMLScalar findUsesScalar(PsiElement element) {
		YamlVersionSite site = YamlVersionSite.locate(element, IS_USES_KEY);
		return site != null ? site.scalar() : null;
	}
}
