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
 * A {@link LocalQuickFix} that exposes the {@link DependencyUpdate} it would
 * apply, so an aggregating action can discover and batch matching fixes without
 * knowing the concrete fix type.
 *
 * @author Mark Paluch
 * @see DependencyUpdate
 * @see ApplyAllUpgradesIntention
 */
public interface DependencyUpdateFix extends LocalQuickFix {

	/**
	 * Return the chosen update this fix writes when invoked, carrying the target
	 * version and the declaration and version sources an update writer rewrites.
	 *
	 * @return the dependency update; a chosen target, not a suggestion.
	 */
	DependencyUpdate getUpdate();

	/**
	 * Return whether this fix was produced for the given upgrade strategy.
	 *
	 * @param strategy the upgrade strategy to match against.
	 * @return {@literal true} if this fix targets {@code strategy};
	 * {@literal false} otherwise.
	 */
	boolean hasStrategy(UpgradeStrategy strategy);

	/**
	 * Return the version-literal element this fix rewrites.
	 *
	 * @return the anchored version literal, or {@literal null} when its PSI anchor
	 * is no longer valid.
	 */
	@Nullable
	PsiElement getStartElement();

}
