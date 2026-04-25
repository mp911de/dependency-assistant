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

import biz.paluch.dap.support.NewerVersionLineMarkerProviderSupport.ActionNavigationHandler;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerInfo.LineMarkerGutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AssertProvider;
import org.assertj.core.error.MessageFormatter;

/**
 * AssertJ entry point for {@link CodeInsightTestFixture}-based assertions.
 *
 * <p>Typical usage: <pre class="code">
 * import static ...CodeInsightAssertions.assertThat;
 *
 * assertThat(fixture).hasSingleGutterContaining("Patch", "4.0.5");
 * assertThat(fixture).hasSingleGutter().tooltipContains("Patch", "4.0.5");
 * assertThat(fixture).hasSingleGutter().hasPsiElementText("4.0.3");
 * assertThat(fixture).hasSingleGutter().hasPsiElementTextContaining("4.0");
 * </pre>
 *
 * @author Mark Paluch
 */
public class CodeInsightAssertions {

	private CodeInsightAssertions() {
	}

	/**
	 * Create an assertion for the given {@link CodeInsightTestFixture}.
	 */
	public static CodeInsightFixtureAssert assertThat(CodeInsightTestFixture fixture) {
		return new CodeInsightFixtureAssert(fixture);
	}


	/**
	 * AssertJ assertions for {@link CodeInsightTestFixture}.
	 */
	public static class CodeInsightFixtureAssert
			extends AbstractAssert<CodeInsightFixtureAssert, CodeInsightTestFixture>
			implements AssertProvider<CodeInsightFixtureAssert> {

		CodeInsightFixtureAssert(CodeInsightTestFixture fixture) {
			super(fixture, CodeInsightFixtureAssert.class);
		}

		@Override
		public CodeInsightFixtureAssert assertThat() {
			return this;
		}

		/**
		 * Navigate to gutter mark assertions for all gutters found in the fixture.
		 */
		public GutterMarksAssert gutters() {
			isNotNull();
			return new GutterMarksAssert(this.actual.findAllGutters());
		}

		/**
		 * Navigate to gutter mark assertions for all gutters found in the fixture.
		 */
		public GutterMarkAssert gutter(int index) {
			isNotNull();
			return gutters().gutterAt(index);
		}

		/**
		 * Verify a single gutter mark is present and navigate to it.
		 */
		public GutterMarkAssert hasSingleGutter() {
			return gutters().hasSingleGutter();
		}

		/**
		 * Verify a single gutter mark is present and that its tooltip contains all of
		 * the given strings.
		 */
		public GutterMarksAssert hasSingleGutterContaining(String... expected) {
			return gutters().hasSingleGutterContaining(expected);
		}

		/**
		 * Verify that no gutter marks are present.
		 */
		public GutterMarksAssert hasNoGutterMarks() {
			return gutters().isEmpty();
		}

	}


	/**
	 * AssertJ assertions for a list of {@link GutterMark} instances.
	 */
	public static class GutterMarksAssert
			extends AbstractAssert<GutterMarksAssert, List<GutterMark>> {

		public GutterMarksAssert(List<GutterMark> gutterMarks) {
			super(gutterMarks, GutterMarksAssert.class);
		}

		/**
		 * Verify a single gutter mark is present and that its tooltip contains all of
		 * the given strings.
		 */
		public GutterMarksAssert hasSingleGutterContaining(String... expected) {
			hasSingleGutter().tooltipContains(expected);
			return this;
		}

		/**
		 * Verify that exactly the given number of gutter marks are present.
		 */
		public GutterMarksAssert hasSize(int expected) {
			isNotNull();
			int actual = this.actual.size();
			if (actual != expected) {

				String gutters = MessageFormatter.instance().format(info.description(), info.representation(), "%s",
						this.actual());
				failWithMessage("Expected %d gutter mark(s) but found %d: %s",
						expected, actual, gutters);
			}
			return this;
		}

		/**
		 * Verify that exactly one gutter mark is present and navigate to it.
		 */
		public GutterMarkAssert hasSingleGutter() {
			hasSize(1);
			return new GutterMarkAssert(this.actual.get(0));
		}

		/**
		 * Navigate to the gutter mark at the given index.
		 */
		public GutterMarkAssert gutter(int index) {
			return gutterAt(index);
		}

		/**
		 * Navigate to the gutter mark at the given index.
		 */
		public GutterMarkAssert gutterAt(int index) {
			isNotNull();
			if (index < 0 || index >= this.actual.size()) {
				failWithMessage("No gutter mark at index %d; found %d gutter mark(s)",
						index, this.actual.size());
			}
			return new GutterMarkAssert(this.actual.get(index));
		}

		/**
		 * Verify that no gutter marks are present.
		 */
		public GutterMarksAssert isEmpty() {
			isNotNull();
			int actual = this.actual.size();
			if (actual != 0) {
				String gutters = MessageFormatter.instance().format(info.description(), info.representation(), "%s",
						this.actual());
				failWithMessage("Expected no gutter marks but found %d: %s",
						actual, gutters);
			}
			return this;
		}

		/**
		 * Verify that no gutter marks are present.
		 */
		public GutterMarksAssert hasNoGutterMarks() {
			return isEmpty();
		}

	}


	/**
	 * AssertJ assertions for a single {@link GutterMark}.
	 *
	 * <p>PSI element text assertions require the {@link GutterMark} to be an
	 * instance of {@link LineMarkerInfo.LineMarkerGutterIconRenderer}, which
	 * exposes the underlying {@link LineMarkerInfo} and its associated
	 * {@link PsiElement}.
	 */
	public static class GutterMarkAssert
			extends AbstractAssert<GutterMarkAssert, GutterMark> {

		private GutterMarkAssert(GutterMark gutterMark) {
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

}
