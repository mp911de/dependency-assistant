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

import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.assistant.ArtifactReleaseAccessor;
import biz.paluch.dap.assistant.ReleaseCompletionProvider;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.PsiElements;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
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
 * IntelliJ completion contributor for Maven wrapper property keys and empty URL
 * values.
 *
 * <p>This contributor is scoped to {@code maven-wrapper.properties}. It opens
 * completion for supported wrapper property key prefixes, for empty supported
 * wrapper URL values after {@code =}, and for version characters inside
 * property values so {@link MavenWrapperVersionCompletionContributor} can offer
 * version segment completions.
 *
 * @author Mark Paluch
 * @see MavenWrapperVersionCompletionContributor
 * @see WrapperProperty
 */
class MavenWrapperPropertyCompletionContributor extends CompletionContributor {

	/**
	 * Shared renderer for wrapper property line lookup elements.
	 */
	public static final PropertyRenderer RENDERER = new PropertyRenderer();

	private static final PsiElementPattern.Capture<PsiElement> WRAPPER_PROPERTIES = PlatformPatterns.psiElement()
			.inFile(PlatformPatterns.psiFile().withName(MavenWrapperUtils.WRAPPER_FILENAME));

	private static final PsiElementPattern.Capture<PsiElement> PROPERTY_VALUE = PlatformPatterns.psiElement()
			.inside(PlatformPatterns.psiElement(PropertyValueImpl.class))
			.inFile(PlatformPatterns.psiFile().withName(MavenWrapperUtils.WRAPPER_FILENAME));

	private static final CompletionProvider<CompletionParameters> LINE_PROVIDER = new WrapperPropertyLineCompletionProvider();

	MavenWrapperPropertyCompletionContributor() {
		extend(CompletionType.BASIC, WRAPPER_PROPERTIES, LINE_PROVIDER);
	}

	/**
	 * Return whether completion should open automatically after typing a Maven
	 * wrapper property character.
	 *
	 * <p>The popup opens for supported property key prefixes, for {@code =} after
	 * an empty supported wrapper URL property, and for version characters inside a
	 * property value.
	 * @param position the PSI position at the caret.
	 * @param typeChar the typed character.
	 * @return {@literal true} if completion should open automatically;
	 * {@literal false} otherwise.
	 */
	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		if (typeChar == '=') {
			return DefaultUrlCompletion.from(position).isPresent();
		}
		if (ReleaseCompletionProvider.isVersionCharacter(typeChar) && PROPERTY_VALUE.accepts(position)) {
			return true;
		}
		return isPropertyKeyTrigger(typeChar) && WRAPPER_PROPERTIES.accepts(position);
	}

	private static boolean isPropertyKeyTrigger(char typeChar) {
		for (String name : WrapperProperty.propertyNames()) {
			if (name.charAt(0) == typeChar) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Completion provider for full Maven wrapper property lines.
	 *
	 * <p>Each lookup element is backed by a synthetic {@link PropertyImpl} carrying
	 * the selected release so {@link PropertyRenderer} can render the property key
	 * and URL consistently.
	 */
	static class WrapperPropertyLineCompletionProvider extends CompletionProvider<CompletionParameters> {

		@Override
		protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
				CompletionResultSet result) {

			PsiElement position = parameters.getPosition();
			Project project = position.getProject();
			Cache cache = StateService.getInstance(project).getCache();
			PsiFileFactory factory = PsiFileFactory.getInstance(project);
			PropertyImpl propertyPosition = MavenWrapperUtils.findProperty(position);

			if (propertyPosition == null) {
				return;
			}

			int length = parameters.getOffset() - propertyPosition.getTextOffset();
			TextRange range = TextRange.create(0, length);
			String prefix = range.substring(propertyPosition.getText());

			result = parameters.getInvocationCount() > 1 ? result.withPrefixMatcher("")
					: result.withPrefixMatcher(prefix);

			for (WrapperProperty kind : WrapperProperty.values()) {

				ArtifactRelease release = kind.getLatestArtifactRelease(cache);
				String url = MavenWrapperUrlRewriter.canonicalUrl(kind, release);

				String lookupString = kind.key() + "=" + url;
				PsiFile file = factory.createFileFromText(
						"dummy.properties", PropertiesFileType.INSTANCE, lookupString);
				PropertyImpl property = PsiTreeUtil.findChildOfType(file, PropertyImpl.class);
				ArtifactReleaseAccessor.putRelease(release, property);

				LookupElement element = LookupElementBuilder.create(property, lookupString)
						.withLookupString(kind.key()).withLookupString(url).withPsiElement(propertyPosition)
						.withRenderer(RENDERER).withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);

				result.addElement(element);
			}
		}

	}

	/**
	 * Default URL completion available for an empty supported Maven wrapper
	 * property value.
	 *
	 * @param url the default wrapper URL to suggest.
	 */
	record DefaultUrlCompletion(String url) {

		/**
		 * Return the default URL completion for the current completion position.
		 * @param parameters the completion parameters.
		 * @return the default URL completion, or an empty {@link Optional}.
		 */
		static Optional<DefaultUrlCompletion> from(CompletionParameters parameters) {

			PsiElement position = parameters.getPosition();
			return from(PsiElements.unleaf(position));
		}

		/**
		 * Return the default URL completion for the property containing the given
		 * element.
		 * @param element the PSI element inside or near a Maven wrapper property.
		 * @return the default URL completion, or an empty {@link Optional}.
		 */
		static Optional<DefaultUrlCompletion> from(PsiElement element) {

			PropertyImpl property = MavenWrapperUtils.findProperty(element);
			if (property == null) {
				return Optional.empty();
			}

			WrapperProperty kind = WrapperProperty.forKey(property.getUnescapedKey());
			if (kind == null || !isEmptyValue(property)) {
				return Optional.empty();
			}
			Cache cache = StateService.getInstance(property.getProject()).getCache();
			Release release = kind.getLatestRelease(cache);
			return Optional.of(
					new DefaultUrlCompletion(MavenWrapperUrlRewriter.canonicalUrl(kind, release.version().toString())));
		}

		private static boolean isEmptyValue(PropertyImpl property) {

			String value = property.getUnescapedValue();
			if (value == null) {
				return false;
			}
			return value.replace(MavenWrapperUtils.COMPLETION_PLACEHOLDER, "").isEmpty();
		}

	}

}
