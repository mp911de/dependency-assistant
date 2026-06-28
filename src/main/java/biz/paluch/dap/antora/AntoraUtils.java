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

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * Utilities for identifying Antora playbook files.
 *
 * <p>This type is exposed as {@code public} because
 * {@code GitHubWorkflowCompletionContributor} in the sibling
 * {@code biz.paluch.dap.github} package must consult it to suppress GitHub
 * workflow completions inside Antora playbook files.
 *
 * @author Mark Paluch
 */
public class AntoraUtils {

	public static final String PLAYBOOK_FILE_NAME = "antora-playbook.yml";

	private static final String RELEASE_DOWNLOAD_FRAGMENT = "/releases/download/";

	private AntoraUtils() {
	}

	/**
	 * Return whether the given file is an Antora playbook supported by this
	 * integration.
	 *
	 * <p>A file qualifies when it carries an injected
	 * {@link AntoraProjectContext#KEY} user-data entry (used by tests) or when its
	 * name matches {@link #PLAYBOOK_FILE_NAME}.
	 *
	 * @param file the PSI file to test; can be {@literal null}.
	 * @return {@literal true} if the file is an Antora playbook; {@literal false}
	 * otherwise.
	 */
	public static boolean isPlaybookFile(@Nullable PsiFile file) {

		if (file == null) {
			return false;
		}

		if (file.getUserData(AntoraProjectContext.KEY) != null) {
			return true;
		}

		return PLAYBOOK_FILE_NAME.equals(file.getName());
	}

	/**
	 * Return whether the given file is an Antora playbook supported by this
	 * integration.
	 * @param file the file to test.
	 * @return {@literal true} if this file is supported.
	 */
	public static boolean isPlaybookFile(VirtualFile file) {
		return PLAYBOOK_FILE_NAME.equals(file.getName());
	}

	/**
	 * Compute the {@link TextRange} that covers only the version segment of the
	 * {@code ui.bundle.url} scalar that owns the given element.
	 *
	 * <p>Used by the annotator and the line marker provider so they highlight only
	 * the version slice between {@code /releases/download/} and the next path
	 * separator.
	 *
	 * @param element the PSI element that lives inside (or is) the
	 * {@code ui.bundle.url} value scalar.
	 * @return the version-only text range, or the element's own text range if no
	 * bundle URL scalar can be located from the element.
	 */
	public static TextRange getVersionRange(PsiElement element) {

		YAMLScalar scalar = AntoraArtifactReferenceResolver.findBundleUrlScalar(element);
		if (scalar == null) {
			return element.getTextRange();
		}

		String text = scalar.getText();
		int fragmentIndex = text.indexOf(RELEASE_DOWNLOAD_FRAGMENT);
		if (fragmentIndex < 0) {
			return scalar.getTextRange();
		}

		int versionStart = fragmentIndex + RELEASE_DOWNLOAD_FRAGMENT.length();
		int versionEnd = text.indexOf('/', versionStart);
		if (versionEnd < 0) {
			return scalar.getTextRange();
		}

		int scalarStart = scalar.getTextRange().getStartOffset();
		return new TextRange(scalarStart + versionStart, scalarStart + versionEnd);
	}

}
