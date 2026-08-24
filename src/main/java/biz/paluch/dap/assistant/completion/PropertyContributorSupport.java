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
 * Base {@link CompletionProvider} for {@code .properties} files that offers
 * completions inside property keys, property values, and on blank lines in the
 * properties list (see {@link #PATTERNS}).
 *
 * <p>Subclasses contribute format-specific lookup elements through
 * {@link #addCompletions(CompletionResultSet, Cache, PsiFileFactory, Property)},
 * typically using {@link #addPropertyLineCompletion}. The
 * {@link #supports(Property)} hook controls automatic invocation after an
 * equals sign; it does not filter explicit completion locations.
 *
 * @author Mark Paluch
 */
public abstract class PropertyContributorSupport extends CompletionProvider<CompletionParameters> {

	/**
	 * Matches a caret position inside a property key. <pre class="code">
	 * distri&lt;caret&gt;butionUrl=https://...
	 * </pre>
	 */
	private static final PsiElementPattern.Capture<PsiElement> PROPERTY_KEY = PlatformPatterns.psiElement()
			.inside(PlatformPatterns.psiElement().withElementType(PropertiesTokenTypes.KEY_CHARACTERS));

	/**
	 * Matches a caret position inside a property value. <pre class="code">
	 * distributionUrl=https&lt;caret&gt;://...
	 * </pre>
	 */
	private static final PsiElementPattern.Capture<PsiElement> PROPERTY_VALUE = PlatformPatterns.psiElement()
			.inside(PlatformPatterns.psiElement().withElementType(PropertiesTokenTypes.VALUE_CHARACTERS));

	/**
	 * Matches a caret position inside the properties list body, including blank
	 * lines between or before properties. <pre class="code">
	 * &lt;caret&gt;
	 * </pre>
	 */
	private static final PsiElementPattern.Capture<PsiElement> PROPERTY_LIST = PlatformPatterns.psiElement()
			.withLanguage(com.intellij.lang.properties.PropertiesLanguage.INSTANCE);

	/**
	 * Matches any caret position handled by this contributor: inside a property
	 * key, inside a property value, or on a blank line in the properties list.
	 * <pre class="code">
	 * distri&lt;caret&gt;butionUrl=https://...
	 * distributionUrl=https&lt;caret&gt;://...
	 * &lt;caret&gt;
	 * </pre>
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
	 * Add format-specific completions for the property at the caret.
	 *
	 * <p>On the first invocation, the result set matches the property text from its
	 * start through the caret. Repeated invocation uses an empty prefix matcher.
	 *
	 * @param result the result set to receive lookup elements.
	 * @param cache the project release cache.
	 * @param factory the project PSI file factory.
	 * @param propertyPosition the property resolved at the completion position.
	 */
	protected abstract void addCompletions(CompletionResultSet result, Cache cache, PsiFileFactory factory,
			Property propertyPosition);

	/**
	 * Add a wrapper {@code key=url} line completion item to {@code result}.
	 *
	 * <p>The item is never auto-inserted. Selecting it replaces the complete line
	 * containing the completion position and moves the caret to the end of the
	 * replacement.
	 *
	 * @param result the completion result set.
	 * @param factory the file factory used to materialize the synthetic property.
	 * @param propertyPosition the property element at the caret.
	 * @param key the wrapper property key.
	 * @param release the artifact release represented by {@code url}.
	 * @param url the canonical download URL for {@code release}.
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
	 * Return whether the typed character should open completion at the given
	 * position.
	 *
	 * <p>An equals sign triggers completion only for a property accepted by
	 * {@link #supports(Property)}. Version characters trigger inside property
	 * values, while property-key triggers are delegated to
	 * {@link #isPropertyKeyTrigger(char)}.
	 *
	 * @param position the PSI position at the caret.
	 * @param typeChar the typed character.
	 * @return {@literal true} if completion should open automatically;
	 * {@literal false} otherwise.
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
	 * Return whether completion should open after an equals sign for the given
	 * property.
	 *
	 * @param property the property at the caret.
	 * @return {@literal true} if the property supports value completion;
	 * {@literal false} otherwise.
	 */
	protected abstract boolean supports(Property property);

	/**
	 * Return whether the typed character should open property-key completion.
	 *
	 * @param typeChar the typed character.
	 * @return {@literal true} if the character begins a supported property key;
	 * {@literal false} otherwise.
	 */
	protected abstract boolean isPropertyKeyTrigger(char typeChar);

	/**
	 * Insert handler that replaces the complete document line containing the
	 * completion start offset.
	 */
	protected static class PropertyLineInsertHandler implements InsertHandler<LookupElement> {

		private final String replacement;

		/**
		 * Create an insert handler for the given complete property line.
		 *
		 * @param replacement the complete property line to insert.
		 */
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
