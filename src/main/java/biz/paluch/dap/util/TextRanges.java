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

package biz.paluch.dap.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * Coordinate conversions for PSI {@link TextRange TextRanges}.
 *
 * @author Mark Paluch
 */
public class TextRanges {

	/**
	 * Convert a range in the element's parent coordinate system to a range local to
	 * the element.
	 *
	 * <p>The range and {@link PsiElement#getStartOffsetInParent()} must use the
	 * same parent coordinate system.
	 *
	 * @param inFile the range to convert.
	 * @param localElement the element that defines the local origin.
	 * @return the range shifted to the element's local coordinates.
	 */
	public static TextRange toLocal(TextRange inFile, PsiElement localElement) {
		return inFile.shiftLeft(localElement.getStartOffsetInParent());
	}

}
