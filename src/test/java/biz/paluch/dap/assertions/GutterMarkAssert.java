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

package biz.paluch.dap.assertions;

import java.util.List;

import biz.paluch.dap.support.NewerVersionLineMarkerProvider.ActionNavigationHandler;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerInfo.LineMarkerGutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertions for a single {@link GutterMark}.
 *
 * <p>PSI element text assertions require the {@link GutterMark} to be an
 * instance of {@link LineMarkerInfo.LineMarkerGutterIconRenderer}, which
 * exposes the underlying {@link LineMarkerInfo} and its associated
 * {@link PsiElement}.
 */
public class GutterMarkAssert
		extends AbstractAssert<GutterMarkAssert, GutterMark> {

	GutterMarkAssert(GutterMark gutterMark) {
		super(gutterMark, GutterMarkAssert.class);
	}

	/**
	 * Verify that the tooltip text contains all of the given strings.
	 */
	public GutterMarkAssert tooltipContains(String... expected) {
		isNotNull();
		String tooltip = this.actual.getTooltipText();
		if (tooltip == null) {
			failWithMessage("Expected gutter tooltip to contain %s but tooltip was null",
					List.of(expected));
		}
		for (String fragment : expected) {
			if (!tooltip.contains(fragment)) {
				failWithMessage(
						"Expected gutter tooltip to contain '%s' but was:\n  \"%s\"",
						fragment, tooltip);
			}
		}
		return this;
	}

	/**
	 * Verify that the PSI element text associated with this gutter mark is exactly
	 * equal to the given string.
	 * <p>Requires the gutter mark to be a
	 * {@link LineMarkerInfo.LineMarkerGutterIconRenderer}.
	 *
	 * @param expected the exact expected PSI element text
	 */
	public GutterMarkAssert hasPsiElementText(String expected) {
		String text = resolvePsiElementText();
		if (!text.equals(expected)) {
			failWithMessage(
					"Expected PSI element text to be:\n  \"%s\"\nbut was:\n  \"%s\"",
					expected, text);
		}
		return this;
	}

	/**
	 * Verify that the PSI element text associated with this gutter mark contains
	 * the given substring.
	 * <p>Requires the gutter mark to be a
	 * {@link LineMarkerInfo.LineMarkerGutterIconRenderer}.
	 *
	 * @param expected the substring expected to be present in the PSI element text
	 */
	public GutterMarkAssert hasPsiElementTextContaining(String expected) {
		String text = resolvePsiElementText();
		if (!text.contains(expected)) {
			failWithMessage(
					"Expected PSI element text to contain '%s' but was:\n  \"%s\"",
					expected, text);
		}
		return this;
	}

	/**
	 * Resolve the PSI element text from the underlying
	 * {@link LineMarkerInfo.LineMarkerGutterIconRenderer}, failing with a
	 * descriptive message at each step if a precondition is not met.
	 */
	private String resolvePsiElementText() {
		isNotNull();
		if (this.actual instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> renderer) {
			LineMarkerInfo<?> markerInfo = renderer.getLineMarkerInfo();
			if (markerInfo == null) {
				failWithMessage("Expected LineMarkerInfo to be present but was null");
			}
			PsiElement element = markerInfo.getElement();
			if (element == null) {
				failWithMessage("Expected PSI element to be present in LineMarkerInfo but was null");
			}

			String text = element.getText();
			if (StringUtils.isEmpty(text.replace("\"", "").replace("'", ""))
					&& element instanceof LeafPsiElement) {
				element = element.getParent();
				text = element.getText();
			}
			if (text == null) {
				failWithMessage("Expected PSI element text to be present but was null");
			}
			return text;
		} else {
			failWithMessage(
					"Expected gutter mark to be a LineMarkerGutterIconRenderer " +
							"for PSI element text access but was: %s",
					this.actual.getClass().getName());
			return null;
		}
	}

	/**
	 * Verify that the gutter mark is navigable.
	 */
	public void hasNavigation() {
		isNotNull();
		if (this.actual instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> renderer) {
			if (!isNavigateAction(renderer)) {
				failWithMessage("Expected gutter mark to be navigable but was not");
			}
		} else {
			failWithMessage(
					"Expected gutter mark to be a LineMarkerGutterIconRenderer " +
							"for navigation access but was: %s",
					this.actual.getClass().getName());
		}
	}

	/**
	 * Verify that the gutter mark is not navigable.
	 */
	public void hasNoNavigation() {
		isNotNull();
		if (this.actual instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> renderer) {
			if (isNavigateAction(renderer)) {
				failWithMessage(
						"Expected gutter mark to be non-navigable but it has a navigation action to: %s",
						renderer.getLineMarkerInfo().getNavigationHandler());
			}
		} else {
			failWithMessage(
					"Expected gutter mark to be a LineMarkerGutterIconRenderer " +
							"for navigation access but was: %s",
					this.actual.getClass().getName());
		}
	}

	private boolean isNavigateAction(LineMarkerGutterIconRenderer<?> renderer) {

		GutterIconNavigationHandler<?> navigationHandler = renderer.getLineMarkerInfo().getNavigationHandler();
		if (navigationHandler instanceof ActionNavigationHandler) {
			return false;
		}
		return renderer.isNavigateAction();
	}

}
