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

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * PSI utilities for identifying Antora playbooks and locating the version
 * segment of their UI bundle URLs.
 *
 * @author Mark Paluch
 */
public class AntoraUtils {

	public static final String PLAYBOOK_FILE_NAME = "antora-playbook.yml";

	private static final String RELEASE_DOWNLOAD_FRAGMENT = "/releases/download/";

	private AntoraUtils() {
	}

	/**
	 * Return whether the file is named {@code antora-playbook.yml}.
	 */
	public static boolean isPlaybookFile(@Nullable PsiFile file) {

		if (file == null) {
			return false;
		}
		return PLAYBOOK_FILE_NAME.equals(file.getName());
	}

	public static boolean isPlaybookFile(VirtualFile file) {
		return PLAYBOOK_FILE_NAME.equals(file.getName());
	}

	/**
	 * Locate the bundle version using absolute file offsets.
	 * @return the version range, the scalar range if no version segment is found,
	 * or the element range if no bundle scalar is found.
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
