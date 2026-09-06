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

package biz.paluch.dap.assertions;

import java.util.List;

import javax.swing.Icon;

import biz.paluch.dap.assistant.editor.DependencyLineMarkerProvider.UpgradeDialogNavigationHandler;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerInfo.LineMarkerGutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.assertj.core.api.AbstractAssert;

/**
 * Assertions for a single gutter mark.
 * <p>PSI, highlight, and navigation checks require a
 * {@link LineMarkerInfo.LineMarkerGutterIconRenderer}. Action-backed update
 * gutters count as non-navigable.
 *
 * @author Mark Paluch
 */
public class GutterMarkAssert
		extends AbstractAssert<GutterMarkAssert, GutterMark> {

	GutterMarkAssert(GutterMark gutterMark) {
		super(gutterMark, GutterMarkAssert.class);
	}

	/**
	 * Require the same icon instance.
	 */
	public GutterMarkAssert hasIcon(Icon expected) {
		isNotNull();
		Icon actualIcon = this.actual.getIcon();
		if (actualIcon != expected) {
			failWithMessage("Expected gutter icon to be:\n  %s\nbut was:\n  %s", expected, actualIcon);
		}
		return this;
	}

	/**
	 * Require no matching tooltip fragments. An absent tooltip satisfies this
	 * check.
	 */
	public GutterMarkAssert tooltipDoesNotContain(String... unexpected) {
		isNotNull();
		String tooltip = this.actual.getTooltipText();
		if (tooltip == null) {
			return this;
		}
		for (String fragment : unexpected) {
			if (tooltip.contains(fragment)) {
				failWithMessage("Expected gutter tooltip not to contain '%s' but was:\n  \"%s\"", fragment, tooltip);
			}
		}
		return this;
	}

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

	public GutterMarkAssert hasPsiElementText(String expected) {
		String text = resolvePsiElementText();
		if (!text.equals(expected)) {
			failWithMessage(
					"Expected PSI element text to be:\n  \"%s\"\nbut was:\n  \"%s\"",
					expected, text);
		}
		return this;
	}

	public GutterMarkAssert hasPsiElementTextContaining(String expected) {
		String text = resolvePsiElementText();
		if (!text.contains(expected)) {
			failWithMessage(
					"Expected PSI element text to contain '%s' but was:\n  \"%s\"",
					expected, text);
		}
		return this;
	}

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
	 * Require the marker range to cover exactly this document text.
	 */
	public GutterMarkAssert highlights(String expected) {
		isNotNull();
		if (!(this.actual instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> renderer)) {
			failWithMessage(
					"Expected gutter mark to be a LineMarkerGutterIconRenderer "
							+ "for highlight range access but was: %s",
					this.actual.getClass().getName());
			return this;
		}

		LineMarkerInfo<?> markerInfo = renderer.getLineMarkerInfo();
		if (markerInfo == null) {
			failWithMessage("Expected LineMarkerInfo to be present but was null");
			return this;
		}

		PsiElement anchor = markerInfo.getElement();
		if (anchor == null) {
			failWithMessage("Expected anchor PSI element to be present in LineMarkerInfo but was null");
			return this;
		}

		String fileText = anchor.getContainingFile().getText();
		int start = markerInfo.startOffset;
		int end = markerInfo.endOffset;
		if (start < 0 || end > fileText.length() || end < start) {
			failWithMessage("Invalid LineMarkerInfo range: [%d, %d) in document of length %d",
					start, end, fileText.length());
			return this;
		}

		String highlighted = fileText.substring(start, end);
		if (!highlighted.equals(expected)) {
			failWithMessage(
					"Expected gutter to highlight:\n  \"%s\"\nbut highlighted:\n  \"%s\"",
					expected, highlighted);
		}
		return this;
	}

	/**
	 * Require the marker range to contain this document text.
	 */
	public GutterMarkAssert highlightsContaining(String expected) {
		isNotNull();
		if (!(this.actual instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> renderer)) {
			failWithMessage(
					"Expected gutter mark to be a LineMarkerGutterIconRenderer "
							+ "for highlight range access but was: %s",
					this.actual.getClass().getName());
			return this;
		}

		LineMarkerInfo<?> markerInfo = renderer.getLineMarkerInfo();
		PsiElement anchor = markerInfo != null ? markerInfo.getElement() : null;
		if (anchor == null) {
			failWithMessage("Expected anchor PSI element to be present in LineMarkerInfo but was null");
			return this;
		}

		String fileText = anchor.getContainingFile().getText();
		String highlighted = fileText.substring(markerInfo.startOffset, markerInfo.endOffset);
		if (!highlighted.contains(expected)) {
			failWithMessage(
					"Expected gutter highlight to contain '%s' but highlighted:\n  \"%s\"",
					expected, highlighted);
		}
		return this;
	}

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
		if (navigationHandler instanceof UpgradeDialogNavigationHandler) {
			return false;
		}
		return renderer.isNavigateAction();
	}

}
