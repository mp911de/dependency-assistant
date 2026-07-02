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
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.impl.PropertiesListImpl;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;

/**
 * Base {@link CompletionProvider} for {@code .properties} files that offers
 * completions inside property keys, property values, and on blank lines in the
 * properties list (see {@link #PATTERNS}).
 *
 * <p>Subclasses implement {@link #supports(PropertyImpl)} and the
 * {@link #addCompletions(CompletionResultSet, Cache, PsiFileFactory, PropertyImpl)}
 * hook to contribute format-specific lookup elements, typically through
 * {@link #addPropertyLineCompletion}.
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
			.inside(PlatformPatterns.psiElement(PropertyKeyImpl.class));

	/**
	 * Matches a caret position inside a property value. <pre class="code">
	 * distributionUrl=https&lt;caret&gt;://...
	 * </pre>
	 */
	private static final PsiElementPattern.Capture<PsiElement> PROPERTY_VALUE = PlatformPatterns.psiElement()
			.inside(PlatformPatterns.psiElement(PropertyValueImpl.class));

	/**
	 * Matches a caret position inside the properties list body, including blank
	 * lines between or before properties. <pre class="code">
	 * &lt;caret&gt;
	 * </pre>
	 */
	private static final PsiElementPattern.Capture<PsiElement> PROPERTY_LIST = PlatformPatterns.psiElement()
			.inside(PlatformPatterns.psiElement(PropertiesListImpl.class));

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
		PropertyImpl propertyPosition = PropertyUtils.findProperty(position);

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

	protected abstract void addCompletions(CompletionResultSet result, Cache cache, PsiFileFactory factory,
			PropertyImpl propertyPosition);

	/**
	 * Add a wrapper {@code key=url} line completion item to {@code result}.
	 *
	 * @param result the completion result set.
	 * @param factory the file factory used to materialise the synthetic property;
	 * must not be {@literal null}.
	 * @param propertyPosition the property element at the caret; must not be
	 * {@literal null}.
	 * @param key the wrapper property key.
	 * @param release the artifact release associated with {@code url}; must not be
	 * {@literal null}.
	 * @param url the canonical download URL for {@code release}; must not be
	 * {@literal null}.
	 */
	protected static void addPropertyLineCompletion(CompletionResultSet result, PsiFileFactory factory,
			PropertyImpl propertyPosition, String key, ArtifactRelease release, String url) {

		String lookupString = key + "=" + url;
		Set<String> lookupStrings = new LinkedHashSet<>();
		lookupStrings.add(lookupString);
		lookupStrings.add(key);
		lookupStrings.add(url);

		PsiFile file = factory.createFileFromText("dummy.properties", PropertiesFileType.INSTANCE, lookupString);
		PropertyImpl property = PsiTreeUtil.findChildOfType(file, PropertyImpl.class);

		result.addElement(LookupElementBuilder.create(property, lookupString)
				.withLookupStrings(lookupStrings)
				.withPsiElement(propertyPosition)
				.withInsertHandler(new PropertyLineInsertHandler(lookupString))
				.withRenderer(PropertyRenderer.INSTANCE)
				.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
	}

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
		PropertyImpl property = PropertyUtils.findProperty(position);
		if (property == null) {
			return false;
		}
		return supports(property);
	}

	protected abstract boolean supports(PropertyImpl property);

	protected abstract boolean isPropertyKeyTrigger(char typeChar);

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
