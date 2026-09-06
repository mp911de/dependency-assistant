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

import java.util.LinkedHashSet;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.PropertyRenderer;
import biz.paluch.dap.util.PropertyUtils;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.ProcessingContext;

/**
 * Base completion provider for wrapper properties.
 * <p>{@link #supports(Property)} controls automatic completion after an equals
 * sign. It does not filter explicit completion.
 *
 * @author Mark Paluch
 */
public abstract class PropertyContributorSupport extends CompletionProvider<CompletionParameters> {

	private static final PsiElementPattern.Capture<PsiElement> PROPERTY_KEY = PlatformPatterns.psiElement()
			.inside(PlatformPatterns.psiElement().withElementType(PropertiesTokenTypes.KEY_CHARACTERS));

	private static final PsiElementPattern.Capture<PsiElement> PROPERTY_VALUE = PlatformPatterns.psiElement()
			.inside(PlatformPatterns.psiElement().withElementType(PropertiesTokenTypes.VALUE_CHARACTERS));

	private static final PsiElementPattern.Capture<PsiElement> PROPERTY_LIST = PlatformPatterns.psiElement()
			.withLanguage(com.intellij.lang.properties.PropertiesLanguage.INSTANCE);

	/**
	 * Completion positions in property keys, values, and the properties list.
	 */
	public static final PsiElementPattern.Capture<PsiElement> PATTERNS = PlatformPatterns.psiElement()
			.andOr(PROPERTY_KEY, PROPERTY_VALUE, PROPERTY_LIST);

	@Override
	protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
			CompletionResultSet result) {

		PsiElement position = parameters.getPosition();
		Property propertyPosition = PropertyUtils.findProperty(position);

		if (propertyPosition == null) {
			return;
		}

		Project project = position.getProject();
		Cache cache = StateService.getInstance(project).getCache();
		PsiFileFactory factory = PsiFileFactory.getInstance(project);

		int rawLength = parameters.getOffset() - propertyPosition.getTextOffset();
		int length = Math.max(0, Math.min(propertyPosition.getTextLength(), rawLength));
		TextRange range = TextRange.create(0, length);
		String prefix = range.substring(propertyPosition.getText());
		result = parameters.getInvocationCount() > 1 ? result.withPrefixMatcher("")
				: result.withPrefixMatcher(prefix);

		addCompletions(result, cache, factory, propertyPosition);
	}

	/**
	 * Add completions for the property at the caret.
	 * <p>The first invocation matches property text up to the caret. Repeated
	 * invocation uses an empty prefix.
	 */
	protected abstract void addCompletions(CompletionResultSet result, Cache cache, PsiFileFactory factory,
			Property propertyPosition);

	/**
	 * Offer a complete wrapper {@code key=url} line.
	 * <p>Selection replaces the current line and moves the caret to its end. The
	 * item is never inserted automatically.
	 */
	protected static void addPropertyLineCompletion(CompletionResultSet result, PsiFileFactory factory,
			Property propertyPosition, String key, ArtifactRelease release, String url) {

		String lookupString = key + "=" + url;
		Set<String> lookupStrings = new LinkedHashSet<>();
		lookupStrings.add(lookupString);
		lookupStrings.add(key);
		lookupStrings.add(url);

		PsiFile file = factory.createFileFromText("dummy.properties", PropertiesFileType.INSTANCE, lookupString);
		IProperty candidate = file instanceof PropertiesFile propertiesFile
				? propertiesFile.findPropertyByKey(key)
				: null;
		Property property = candidate instanceof Property value ? value : null;
		if (property == null) {
			return;
		}

		result.addElement(LookupElementBuilder.create(property, lookupString)
				.withLookupStrings(lookupStrings)
				.withPsiElement(propertyPosition)
				.withInsertHandler(new PropertyLineInsertHandler(lookupString))
				.withRenderer(PropertyRenderer.INSTANCE)
				.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
	}

	/**
	 * Return whether typing should open completion.
	 * <p>Equals-sign completion is limited by {@link #supports(Property)}.
	 */
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		if (typeChar == '=') {
			return autoPopupAfterEquals(position);
		}
		if (ReleaseCompletionProvider.isVersionCharacter(typeChar) && PROPERTY_VALUE.accepts(position)) {
			return true;
		}
		return isPropertyKeyTrigger(typeChar);
	}

	private boolean autoPopupAfterEquals(PsiElement position) {
		Property property = PropertyUtils.findProperty(position);
		if (property == null) {
			return false;
		}
		return supports(property);
	}

	/**
	 * Return whether completion should open after an equals sign for this property.
	 */
	protected abstract boolean supports(Property property);

	/**
	 * Return whether typing this character should open property-key completion.
	 */
	protected abstract boolean isPropertyKeyTrigger(char typeChar);

	/**
	 * Replaces the complete line containing the completion start offset.
	 */
	protected static class PropertyLineInsertHandler implements InsertHandler<LookupElement> {

		private final String replacement;

		public PropertyLineInsertHandler(String replacement) {
			this.replacement = replacement;
		}

		@Override
		public void handleInsert(InsertionContext context, LookupElement item) {

			int offset = Math.min(context.getStartOffset(), Math.max(0, context.getDocument().getTextLength() - 1));
			int line = context.getDocument().getLineNumber(offset);
			int start = context.getDocument().getLineStartOffset(line);
			int end = context.getDocument().getLineEndOffset(line);

			context.getDocument().replaceString(start, end, replacement);
			context.getEditor().getCaretModel().moveToOffset(start + replacement.length());
		}

	}

}
