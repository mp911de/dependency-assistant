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

package biz.paluch.dap.assistant.editor;

import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * A quick fix exposing its selected update for batch application.
 *
 * @author Mark Paluch
 * @see ApplyAllUpgradesIntention
 */
public interface DependencyUpdateFix extends LocalQuickFix {

	/**
	 * Return the selected update this fix applies.
	 */
	DependencyUpdate getUpdate();

	boolean hasStrategy(UpgradeStrategy strategy);

	/**
	 * Return the version literal, or {@literal null} if its anchor is no longer
	 * valid.
	 */
	@Nullable
	PsiElement getStartElement();

}
