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

package biz.paluch.dap.assistant;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.util.PropertyUtils;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.psi.PropertyKeyValueFormat;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ProcessingContext;

/**
 * Base completion contributor for version segments in wrapper URLs.
 *
 * <p>Subclasses provide the format-specific version-range detection and update
 * application. Shared logic covers the prefix matcher, insert handler that
 * applies the selected release, and caret restoration after the wrapper URL is
 * rewritten.
 *
 * @author Mark Paluch
 */
public abstract class VersionContributorSupport extends ReleaseCompletionProvider {

	/**
	 * Matches a caret position inside any property value. Subclasses are expected
	 * to refine this pattern with format-specific property names.
	 */
	protected static final PsiElementPattern.Capture<PsiElement> PROPERTY_VALUE = PlatformPatterns.psiElement()
			.inside(PlatformPatterns.psiElement(PropertyValueImpl.class));

	@Override
	protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
			CompletionResultSet result) {

		if (CompletionPrefix.from(parameters, this::getVersionRanges).isPresent()) {
			super.addCompletions(parameters, context, result);
		}
	}

	@Override
	protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters, CompletionResultSet result) {

		if (parameters.getInvocationCount() > 1) {
			return result.withPrefixMatcher("");
		}

		return result.withPrefixMatcher(CompletionPrefix.from(parameters, this::getVersionRanges).toString());
	}

	@Override
	protected LookupElementBuilder postProcess(CompletionParameters parameters, LookupElementBuilder builder,
			PsiElement element, ArtifactRelease option) {

		PropertyImpl property = PropertyUtils.findProperty(element);
		if (property == null) {
			return builder;
		}

		CompletionPrefix prefix = CompletionPrefix.from(parameters, this::getVersionRanges);
		return wrapperInsertHandler(prefix, property).apply(option, parameters)
				.map(builder::withInsertHandler)
				.orElse(builder);
	}

	/**
	 * Return whether completion should open automatically after typing a version
	 * character in a supported wrapper URL.
	 * @param position the PSI position at the caret.
	 * @param typeChar the typed character.
	 * @return {@code true} if completion should open automatically.
	 */
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		return isVersionCharacter(typeChar) && PROPERTY_VALUE.accepts(position);
	}

	/**
	 * Return file-absolute ranges of the version segments in the wrapper URL of
	 * {@code property}, or an empty list when the value is not a supported wrapper
	 * URL.
	 */
	protected abstract List<TextRange> getVersionRanges(PropertyImpl property);

	/**
	 * Apply the dependency update at {@code versionLiteral}. Called from the insert
	 * handler after the property value has been restored to the original
	 * (pre-completion) text.
	 */
	protected abstract void applyVersionUpdate(PsiElement versionLiteral, DependencyUpdate update);

	private BiFunction<ArtifactRelease, CompletionParameters, Optional<WrapperInsertHandler>> wrapperInsertHandler(
			CompletionPrefix prefix, PropertyImpl property) {

		if (StringUtils.isEmpty(property.getUnescapedKey()) || StringUtils.isEmpty(property.getUnescapedValue())) {
			return (a, b) -> Optional.empty();
		}
		return (release, parameters) -> Optional.of(new WrapperInsertHandler(prefix, release,
				SmartPointerManager.createPointer(property)));
	}

	/**
	 * Completion prefix for the wrapper URL version segment at the caret.
	 *
	 * <p>The prefix also keeps the original value text and all version ranges so
	 * the insert handler can update every wrapper URL version occurrence.
	 */
	protected static class CompletionPrefix {

		private static final CompletionPrefix NONE = new CompletionPrefix("", 0, "", List.of());

		private final String prefix;

		private final int startOffset;

		private final String originalText;

		private final List<TextRange> ranges;

		private CompletionPrefix(String prefix, int startOffset, String originalText, List<TextRange> ranges) {
			this.prefix = prefix;
			this.startOffset = startOffset;
			this.originalText = originalText;
			this.ranges = ranges;
		}

		/**
		 * Return the completion prefix for the current parameters, using the supplied
		 * function to obtain the version ranges of the property at the caret.
		 * @param parameters the completion parameters.
		 * @param getVersionRanges function returning file-absolute version ranges for a
		 * wrapper property.
		 * @return the calculated prefix, or an empty prefix when completion is outside
		 * a supported version segment.
		 */
		public static CompletionPrefix from(CompletionParameters parameters,
				Function<PropertyImpl, List<TextRange>> getVersionRanges) {
			return CachedValuesManager.getProjectPsiDependentCache(parameters.getPosition(),
					it -> from(it, parameters.getOffset(), getVersionRanges));
		}

		private static CompletionPrefix from(PsiElement element, int caretOffset,
				Function<PropertyImpl, List<TextRange>> getVersionRanges) {

			PropertyImpl property = PropertyUtils.findProperty(element);
			PropertyValueImpl value = PropertyUtils.findPropertyValue(element);
			if (property == null || value == null) {
				return NONE;
			}

			String text = value.getText();
			if (StringUtils.isEmpty(text)) {
				return NONE;
			}

			List<TextRange> ranges = getVersionRanges.apply(property);
			String originalText = text.replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "");

			for (TextRange updateRange : ranges) {
				if (updateRange.containsOffset(caretOffset)) {

					int relativeCaretOffset = caretOffset - updateRange.getStartOffset();
					TextRange rangeInValue = updateRange.shiftLeft(value.getTextRange().getStartOffset());
					TextRange prefixRange = TextRange.from(rangeInValue.getStartOffset(), relativeCaretOffset);
					String prefix = prefixRange.substring(originalText);
					return new CompletionPrefix(prefix, caretOffset, originalText, ranges);
				}
			}

			return NONE;
		}

		/**
		 * Return the caret offset at which completion started.
		 */
		public int getStartOffset() {
			return startOffset;
		}

		/**
		 * Return the property value text before the completion placeholder was added.
		 */
		public String getOriginalText() {
			return originalText;
		}

		/**
		 * Return all version ranges in the wrapper URL.
		 */
		public List<TextRange> getRanges() {
			return ranges;
		}

		/**
		 * Return whether the caret is inside a supported version range.
		 */
		public boolean isPresent() {
			return !ranges.isEmpty();
		}

		@Override
		public String toString() {
			return prefix;
		}

	}

	/**
	 * Insert handler that applies the selected release to every version segment of
	 * the wrapper URL and restores the caret near the edited segment.
	 */
	private class WrapperInsertHandler implements InsertHandler<LookupElement> {

		private final CompletionPrefix prefix;

		private final ArtifactRelease release;

		private final SmartPsiElementPointer<PropertyImpl> pointer;

		private WrapperInsertHandler(CompletionPrefix prefix, ArtifactRelease release,
				SmartPsiElementPointer<PropertyImpl> pointer) {
			this.prefix = prefix;
			this.release = release;
			this.pointer = pointer;
		}

		@Override
		public void handleInsert(InsertionContext context, LookupElement lookupElement) {

			PropertyImpl property = pointer.getElement();
			if (property == null || !property.isValid()) {
				return;
			}

			int caretRangeIndex = caretRangeIndex();
			context.commitDocument();
			property.setValue(prefix.getOriginalText(), PropertyKeyValueFormat.FILE);

			PropertyImpl freshProperty = pointer.getElement();
			if (freshProperty == null || !freshProperty.isValid()) {
				return;
			}

			applyVersionUpdate(freshProperty, DependencyUpdate.create(release.artifactId(), release.getVersion()));

			int newCaretOffset = caretOffsetAfterUpdate(freshProperty, caretRangeIndex);
			context.getEditor().getCaretModel().moveToOffset(newCaretOffset);
			context.setTailOffset(newCaretOffset);
		}

		private int caretRangeIndex() {

			List<TextRange> ranges = prefix.getRanges();
			for (int i = 0; i < ranges.size(); i++) {
				if (ranges.get(i).containsOffset(prefix.getStartOffset())) {
					return i;
				}
			}
			return -1;
		}

		private int caretOffsetAfterUpdate(PropertyImpl freshProperty, int caretRangeIndex) {

			if (caretRangeIndex < 0) {
				return prefix.getStartOffset();
			}

			List<TextRange> ranges = getVersionRanges(freshProperty);
			if (caretRangeIndex >= ranges.size()) {
				return prefix.getStartOffset();
			}

			return ranges.get(caretRangeIndex).getEndOffset();
		}

	}

}
