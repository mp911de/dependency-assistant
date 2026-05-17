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

import java.util.LinkedHashSet;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.assistant.ArtifactReleaseAccessor;
import biz.paluch.dap.assistant.ReleaseCompletionProvider;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import com.intellij.codeInsight.completion.CompletionContributor;
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
 * {@link CompletionContributor} for {@link WrapperProperty wrapper properties}.
 *
 * @author Mark Paluch
 */
class PropertyContributor extends CompletionProvider<CompletionParameters> {

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
		PropertyImpl propertyPosition = MavenWrapperUtils.findProperty(position);

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

		for (WrapperProperty kind : WrapperProperty.values()) {

			ArtifactRelease release = kind.getLatestArtifactRelease(cache);
			String url = MavenWrapperUrlRewriter.canonicalUrl(kind, release);

			Set<String> lookupStrings = new LinkedHashSet<>();
			String lookupString = kind.key() + "=" + url;

			lookupStrings.add(lookupString);
			lookupStrings.add(kind.key());
			lookupStrings.add(url);

			PsiFile file = factory.createFileFromText(
					"dummy.properties", PropertiesFileType.INSTANCE, lookupString);
			PropertyImpl property = PsiTreeUtil.findChildOfType(file, PropertyImpl.class);
			ArtifactReleaseAccessor.putRelease(release, property);

			LookupElement element = LookupElementBuilder.create(property, lookupString)
					.withLookupStrings(lookupStrings)
					.withPsiElement(propertyPosition)
					.withInsertHandler(new PropertyLineInsertHandler(lookupString))
					.withRenderer(PropertyRenderer.INSTANCE)
					.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);

			result.addElement(element);
		}
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
		PropertyImpl property = MavenWrapperUtils.findProperty(position);
		if (property == null) {
			return false;
		}
		return WrapperProperty.isWrapperProperty(property);
	}

	private static boolean isPropertyKeyTrigger(char typeChar) {
		for (String name : WrapperProperty.propertyNames()) {
			if (name.charAt(0) == typeChar) {
				return true;
			}
		}
		return false;
	}

	static class PropertyLineInsertHandler implements InsertHandler<LookupElement> {

		private final String replacement;

		PropertyLineInsertHandler(String replacement) {
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
