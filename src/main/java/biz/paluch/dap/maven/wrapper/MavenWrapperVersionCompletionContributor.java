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

package biz.paluch.dap.maven.wrapper;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.assistant.ReleaseCompletionProvider;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
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
 * Completion contributor for version segments in Maven wrapper URLs.
 *
 * <p>The contributor is limited to supported Maven wrapper properties and
 * offers release versions only when the caret is inside a supported version
 * segment of the property value.
 *
 * @author Mark Paluch
 */
class MavenWrapperVersionCompletionContributor extends CompletionContributor {

	private static final PsiElementPattern.Capture<PsiElement> WRAPPER_VERSION = PlatformPatterns.psiElement()
			.inside(PlatformPatterns.psiElement(PropertyValueImpl.class))
			.inside(PlatformPatterns.psiElement(PropertyImpl.class).withName(WrapperProperty.propertyNames()));

	private static final ReleaseCompletionProvider PROVIDER = new ReleaseCompletionProvider() {

		@Override
		protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
				CompletionResultSet result) {

			if (CompletionPrefix.from(parameters).isPresent()) {
				super.addCompletions(parameters, context, result);
			}
		}

		@Override
		protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters, CompletionResultSet result) {

			if (parameters.getInvocationCount() > 1) {
				return result.withPrefixMatcher("");
			}

			CompletionPrefix prefix = CompletionPrefix.from(parameters);
			return result.withPrefixMatcher(prefix.toString());
		}

		@Override
		protected LookupElementBuilder postProcess(CompletionParameters parameters, LookupElementBuilder builder,
				PsiElement element, ArtifactRelease option) {

			CompletionPrefix prefix = CompletionPrefix.from(parameters);
			return MavenWrapperUtils.doWithProperty(element, it -> {

				return WrapperInsertHandler.from(prefix, it).apply(option, parameters).map(builder::withInsertHandler)
						.orElse(builder);

			}, () -> builder);
		}

	};

	MavenWrapperVersionCompletionContributor() {
		extend(CompletionType.BASIC, WRAPPER_VERSION, PROVIDER);
	}

	/**
	 * Return whether completion should open automatically after typing a version
	 * character in a supported wrapper URL.
	 * @param position the PSI position at the caret.
	 * @param typeChar the typed character.
	 * @return {@code true} if completion should open automatically.
	 */
	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		return ReleaseCompletionProvider.isVersionCharacter(typeChar) && WRAPPER_VERSION.accepts(position);
	}

	/**
	 * Completion prefix for the wrapper URL version segment at the caret.
	 *
	 * <p>The prefix also keeps the original value text and all version ranges so
	 * the insert handler can update both Maven wrapper URL version occurrences.
	 */
	static class CompletionPrefix {

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
		 * Return the completion prefix for the current parameters.
		 * @param parameters the completion parameters.
		 * @return the calculated prefix, or an empty prefix when completion is outside
		 * a supported version segment.
		 */
		public static CompletionPrefix from(CompletionParameters parameters) {
			return CachedValuesManager.getProjectPsiDependentCache(parameters.getPosition(),
					it -> from(it, parameters.getOffset()));
		}

		private static CompletionPrefix from(PsiElement element, int caretOffset) {

			PropertyImpl property = MavenWrapperUtils.findProperty(element);
			PropertyValueImpl value = MavenWrapperUtils.findPropertyValue(element);
			if (property == null || value == null) {
				return NONE;
			}

			String text = value.getText();
			if (StringUtils.isEmpty(text)) {
				return NONE;
			}

			List<TextRange> ranges = MavenWrapperUtils.getVersionRanges(property);
			String originalText = text.replace(MavenWrapperUtils.COMPLETION_PLACEHOLDER, "");


			// TODO reuse utility?
			for (TextRange updateRange : ranges) {
				if (updateRange.containsOffset(caretOffset)) {
					int end = caretOffset - updateRange.getStartOffset();
					TextRange range = updateRange.shiftLeft(value.getTextRange().getStartOffset());
					String prefix = originalText.substring(range.getStartOffset(), range.getStartOffset() + end);
					return new CompletionPrefix(prefix, caretOffset, originalText, ranges);
				}
			}

			return NONE;
		}

		/**
		 * Return the caret offset at which completion started.
		 * @return the completion start offset.
		 */
		public int getStartOffset() {
			return startOffset;
		}

		/**
		 * Return the property value text before the completion placeholder was added.
		 * @return the original property value text.
		 */
		public String getOriginalText() {
			return originalText;
		}

		/**
		 * Return all version ranges in the wrapper URL.
		 * @return the file-absolute version ranges.
		 */
		public List<TextRange> getRanges() {
			return ranges;
		}

		/**
		 * Return the length of the prefix at the caret.
		 * @return the prefix length.
		 */
		public int length() {
			return prefix.length();
		}

		/**
		 * Return whether the caret is inside a supported version range.
		 * @return {@code true} if a completion prefix is available.
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
	 * Insert handler that applies the selected release to both version segments of
	 * the wrapper URL.
	 */
	static class WrapperInsertHandler implements InsertHandler<LookupElement> {

		private final CompletionPrefix prefix;

		private final ArtifactRelease release;

		private final SmartPsiElementPointer<PropertyImpl> pointer;

		private WrapperInsertHandler(CompletionPrefix prefix, ArtifactRelease release,
				SmartPsiElementPointer<PropertyImpl> pointer) {
			this.prefix = prefix;
			this.release = release;
			this.pointer = pointer;
		}

		/**
		 * Return a factory for insert handlers bound to the given property.
		 * @param prefix the completion prefix at the caret.
		 * @param property the property to update after insertion.
		 * @return a factory that creates insert handlers for release options.
		 */
		public static BiFunction<ArtifactRelease, CompletionParameters, Optional<WrapperInsertHandler>> from(
				CompletionPrefix prefix, PropertyImpl property) {

			if (StringUtils.isEmpty(property.getUnescapedKey()) || StringUtils.isEmpty(property.getUnescapedValue())) {
				return (a, b) -> Optional.empty();
			}
			return (release, parameters) -> Optional.of(create(prefix, release, property));
		}

		/**
		 * Create an insert handler for the selected release.
		 * @param prefix the completion prefix at the caret.
		 * @param release the selected release.
		 * @param property the property to update after insertion.
		 * @return the insert handler.
		 */
		public static WrapperInsertHandler create(CompletionPrefix prefix, ArtifactRelease release,
				PropertyImpl property) {

			SmartPsiElementPointer<PropertyImpl> pointer = SmartPointerManager.createPointer(property);
			return new WrapperInsertHandler(prefix, release, pointer);
		}

		/**
		 * Apply the selected release and restore the caret near the edited version
		 * segment.
		 */
		@Override
		public void handleInsert(InsertionContext context, LookupElement lookupElement) {

			PropertyImpl property = pointer.getElement();
			if (property == null || !property.isValid()) {
				return;
			}

			int newCaretOffset = getCaretOffset();
			context.commitDocument();
			property.setValue(prefix.getOriginalText(), PropertyKeyValueFormat.FILE);

			PropertyImpl freshProperty = pointer.getElement();
			if (freshProperty == null || !freshProperty.isValid()) {
				return;
			}

			UpdateMavenWrapperProperties.applyUpdate(freshProperty,
					DependencyUpdate.create(release.artifactId(), release.getVersion()));

			context.getEditor().getCaretModel().moveToOffset(newCaretOffset);
			context.setTailOffset(newCaretOffset);
		}

		/**
		 * Return the caret offset after both wrapper URL version segments have been
		 * updated.
		 */
		private int getCaretOffset() {

			int versionLengthDifference = 0;
			int newVersionLength = release.getVersion().toString().length();
			for (TextRange range : prefix.getRanges()) {
				versionLengthDifference += (newVersionLength - range.getLength());
				if (range.containsOffset(prefix.getStartOffset())) {
					return range.getEndOffset() + versionLengthDifference;
				}
			}

			return prefix.getStartOffset();
		}

	}

}
