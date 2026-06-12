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

import biz.paluch.dap.assistant.DependencyLineMarkerProvider.ActionNavigationHandler;
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
 * <p>Tooltip assertions apply to every gutter mark. PSI text, highlight, and
 * navigation assertions require the mark to be backed by a
 * {@link LineMarkerInfo.LineMarkerGutterIconRenderer}, since only that renderer
 * exposes the underlying {@link LineMarkerInfo} and its associated
 * {@link PsiElement}.
 *
 * <p>Example: <pre class="code">
 * assertThat(fixture)
 *     .hasSingleGutter()
 *     .tooltipContains("Patch", "6.0.3")
 *     .highlights("6.0.0");
 *
 * assertThat(buildFile)
 *     .hasSingleGutter()
 *     .hasPsiElementTextContaining("${junit}")
 *     .hasNavigation();
 * </pre>
 *
 * @author Mark Paluch
 */
public class GutterMarkAssert
		extends AbstractAssert<GutterMarkAssert, GutterMark> {

	GutterMarkAssert(GutterMark gutterMark) {
		super(gutterMark, GutterMarkAssert.class);
	}

	/**
	 * Verifies that the actual gutter mark tooltip contains all of the given
	 * fragments.
	 * @param expected the fragments expected in the tooltip text.
	 * @return this assertion object.
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
	 * Verifies that the PSI element text associated with this gutter mark is
	 * exactly equal to the given string.
	 * <p>Requires the gutter mark to be a
	 * {@link LineMarkerInfo.LineMarkerGutterIconRenderer}.
	 * @param expected the exact expected PSI element text.
	 * @return this assertion object.
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
	 * Verifies that the PSI element text associated with this gutter mark contains
	 * the given substring.
	 * <p>Requires the gutter mark to be a
	 * {@link LineMarkerInfo.LineMarkerGutterIconRenderer}.
	 * @param expected the substring expected to be present in the PSI element text.
	 * @return this assertion object.
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
	 * Verifies that the document substring covered by this gutter's
	 * {@link LineMarkerInfo} range is exactly equal to {@code expected}.
	 *
	 * <p>The range is the visible highlight produced by the line marker provider
	 * (typically the variant's {@code replaceableRange} for build-file dependency
	 * entries), so this assertion confirms which substring of the editor is
	 * visually marked as upgradable.
	 * @param expected the exact expected highlighted text.
	 * @return this assertion object.
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
	 * Verifies that the document substring covered by this gutter's
	 * {@link LineMarkerInfo} range contains the given fragment.
	 * @param expected the substring expected to appear in the highlighted text.
	 * @return this assertion object.
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

	/**
	 * Verifies that the actual gutter mark is navigable.
	 * <p>Action-backed update gutters are intentionally treated as non-navigable,
	 * even when IntelliJ exposes an action handler.
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
	 * Verifies that the actual gutter mark is not navigable.
	 * <p>Action-backed update gutters satisfy this assertion because they perform
	 * an action instead of navigating to another PSI location.
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
