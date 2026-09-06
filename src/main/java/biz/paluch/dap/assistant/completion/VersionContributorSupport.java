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

package biz.paluch.dap.assistant.completion;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.util.PropertyUtils;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyKeyValueFormat;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ProcessingContext;

/**
 * Base completion provider for version segments in wrapper URLs.
 * <p>Subclasses supply version ranges and update application. Insertion
 * restores the original property before applying the selected version to all
 * segments.
 *
 * @author Mark Paluch
 */
public abstract class VersionContributorSupport extends ReleaseCompletionProvider {

	private static final Key<CompletionPrefix> COMPLETION_PREFIX = Key
			.create("biz.paluch.dap.assistant.completion.versionPrefix");

	/**
	 * Property-value positions. Subclasses must refine this by wrapper property
	 * name.
	 */
	protected static final PsiElementPattern.Capture<PsiElement> PROPERTY_VALUE = PlatformPatterns.psiElement()
			.inside(PlatformPatterns.psiElement().withElementType(PropertiesTokenTypes.VALUE_CHARACTERS));

	@Override
	protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
			CompletionResultSet result) {

		CompletionPrefix prefix = CompletionPrefix.from(parameters, this::getVersionRanges);
		context.put(COMPLETION_PREFIX, prefix);
		if (prefix.isPresent()) {
			super.addCompletions(parameters, context, result);
		}
	}

	@Override
	protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters, ProcessingContext context,
			CompletionResultSet result) {

		if (showsFullHistory(parameters)) {
			return result.withPrefixMatcher("");
		}

		return result.withPrefixMatcher(getCompletionPrefix(context).toString());
	}

	@Override
	protected LookupElementBuilder postProcess(CompletionParameters parameters, ProcessingContext context,
			LookupElementBuilder builder, PsiElement element, ArtifactRelease option) {

		Property property = PropertyUtils.findProperty(element);
		if (property == null) {
			return builder;
		}

		CompletionPrefix prefix = getCompletionPrefix(context);
		return wrapperInsertHandler(prefix, property).apply(option, parameters)
				.map(builder::withInsertHandler)
				.orElse(builder);
	}

	private static CompletionPrefix getCompletionPrefix(ProcessingContext context) {
		CompletionPrefix prefix = context.get(COMPLETION_PREFIX);
		if (prefix == null) {
			throw new IllegalStateException("Completion prefix was not initialized");
		}
		return prefix;
	}

	/**
	 * Test the base automatic-completion condition.
	 * <p>Subclasses should also require a format-specific wrapper-version position.
	 */
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		return isVersionCharacter(typeChar) && PROPERTY_VALUE.accepts(position);
	}

	/**
	 * Return file-absolute version ranges, or an empty list for unsupported URLs.
	 */
	protected abstract List<TextRange> getVersionRanges(Property property);

	/**
	 * Apply the selected update to the restored property.
	 * <p>IntelliJ's tentative completion edit has already been removed.
	 */
	protected abstract void applyVersionUpdate(PsiElement versionLiteral, DependencyUpdate update);

	private BiFunction<ArtifactRelease, CompletionParameters, Optional<WrapperInsertHandler>> wrapperInsertHandler(
			CompletionPrefix prefix, Property property) {

		if (StringUtils.isEmpty(property.getUnescapedKey()) || StringUtils.isEmpty(property.getUnescapedValue())) {
			return (a, b) -> Optional.empty();
		}
		return (release, parameters) -> Optional.of(new WrapperInsertHandler(prefix, release,
				SmartPointerManager.createPointer(property)));
	}

	/**
	 * Snapshot of a wrapper version-completion request.
	 * <p>Retains the original value for restoring tentative edits. Do not reuse it
	 * across requests.
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
		 * Capture the current completion request.
		 * @param getVersionRanges supplies file-absolute ranges.
		 * @return an absent snapshot outside supported version segments.
		 */
		public static CompletionPrefix from(CompletionParameters parameters,
				Function<Property, List<TextRange>> getVersionRanges) {
			return from(parameters.getPosition(), parameters.getOffset(), getVersionRanges);
		}

		private static CompletionPrefix from(PsiElement element, int caretOffset,
				Function<Property, List<TextRange>> getVersionRanges) {

			Property property = PropertyUtils.findProperty(element);
			PsiElement value = PropertyUtils.findPropertyValue(element);
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
		 * Return the file-absolute caret offset where completion started.
		 */
		public int getStartOffset() {
			return startOffset;
		}

		/**
		 * Return the property value before the completion placeholder was added.
		 */
		public String getOriginalText() {
			return originalText;
		}

		/**
		 * Return the captured file-absolute version ranges.
		 */
		public List<TextRange> getRanges() {
			return ranges;
		}

		public boolean isPresent() {
			return !ranges.isEmpty();
		}

		@Override
		public String toString() {
			return prefix;
		}

	}

	private class WrapperInsertHandler implements InsertHandler<LookupElement> {

		private final CompletionPrefix prefix;

		private final ArtifactRelease release;

		private final SmartPsiElementPointer<Property> pointer;

		private WrapperInsertHandler(CompletionPrefix prefix, ArtifactRelease release,
				SmartPsiElementPointer<Property> pointer) {
			this.prefix = prefix;
			this.release = release;
			this.pointer = pointer;
		}

		@Override
		public void handleInsert(InsertionContext context, LookupElement lookupElement) {

			Property property = pointer.getElement();
			if (property == null || !property.isValid()) {
				return;
			}

			int caretRangeIndex = caretRangeIndex();
			context.commitDocument();
			property.setValue(prefix.getOriginalText(), PropertyKeyValueFormat.FILE);

			Property freshProperty = pointer.getElement();
			if (freshProperty == null || !freshProperty.isValid()) {
				return;
			}

			applyVersionUpdate(freshProperty, DependencyUpdate.create(release.artifactId(), release.getVersion()));

			LookupElementInsertHandler.moveCaretTo(context, caretOffsetAfterUpdate(freshProperty, caretRangeIndex));
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

		private int caretOffsetAfterUpdate(Property freshProperty, int caretRangeIndex) {

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
