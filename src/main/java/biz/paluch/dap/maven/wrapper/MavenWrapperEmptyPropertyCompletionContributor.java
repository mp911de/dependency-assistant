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

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.assistant.ReleasesCompletionProvider;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.PatternConditions;
import biz.paluch.dap.util.PsiElements;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ProcessingContext;

/**
 * Completion contributor for Maven wrapper URLs.
 *
 * <p>The contributor is limited to supported Maven wrapper properties and
 * offers a default URL for empty values or release versions when the caret is
 * inside a supported version segment of the property value.
 *
 * @author Mark Paluch
 */
class MavenWrapperEmptyPropertyCompletionContributor extends CompletionContributor {

	private static final String DISTRIBUTION_FALLBACK_VERSION = "3.9.15";

	private static final String WRAPPER_FALLBACK_VERSION = "3.3.4";

	private static final PsiElementPattern.Capture<PsiElement> PROPERTY = PlatformPatterns.psiElement()
			.inside(PlatformPatterns.psiElement(PropertyValueImpl.class)
					.with(PatternConditions.conditional("empty", it -> {
						String test = it.getText()
								.replace(MavenWrapperUtils.COMPLETION_PLACEHOLDER, "");
						return StringUtils.isEmpty(test);
					})))
			.inside(PlatformPatterns.psiElement(PropertyImpl.class).withName(WrapperProperty.propertyNames()));

	private static final CompletionProvider<CompletionParameters> DEFAULT_URL_PROVIDER = new DefaultUrlCompletionProvider();

	MavenWrapperEmptyPropertyCompletionContributor() {
		extend(CompletionType.BASIC, PROPERTY, DEFAULT_URL_PROVIDER);
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
		if (typeChar == '=') {
			return DefaultUrlCompletion.from(position).isPresent();
		}
		return ReleasesCompletionProvider.isVersionCharacter(typeChar) && PROPERTY.accepts(position);
	}

	/**
	 * Completion provider for empty supported Maven wrapper property values.
	 */
	static class DefaultUrlCompletionProvider extends CompletionProvider<CompletionParameters> {

		@Override
		protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
				CompletionResultSet result) {

			Optional<DefaultUrlCompletion> defaultUrl = DefaultUrlCompletion.from(parameters);
			if (defaultUrl.isEmpty()) {
				return;
			}

			result.withPrefixMatcher("").addElement(defaultUrl.get().lookupElement());
			result.stopHere();
		}

	}

	/**
	 * Completion for an empty supported Maven wrapper property value.
	 */
	record DefaultUrlCompletion(SmartPsiElementPointer<PropertyImpl> pointer, String url) {

		static Optional<DefaultUrlCompletion> from(CompletionParameters parameters) {

			PsiElement position = parameters.getPosition();
			return from(PsiElements.unleaf(position));
		}

		static Optional<DefaultUrlCompletion> from(PsiElement element) {

			PropertyImpl property = MavenWrapperUtils.findProperty(element);
			if (property == null) {
				return Optional.empty();
			}

			WrapperProperty kind = WrapperProperty.forKey(property.getUnescapedKey());
			if (kind == null || !isEmptyValue(property)) {
				return Optional.empty();
			}

			String version = resolveCompletionVersion(property, kind);
			SmartPsiElementPointer<PropertyImpl> pointer = SmartPointerManager.createPointer(property);
			return Optional.of(new DefaultUrlCompletion(pointer, MavenWrapperUrlRewriter.canonicalUrl(kind, version)));
		}

		private static boolean isEmptyValue(PropertyImpl property) {

			String value = property.getUnescapedValue();
			if (value == null) {
				return false;
			}
			return value.replace(MavenWrapperUtils.COMPLETION_PLACEHOLDER, "").isEmpty();
		}

		private static String resolveCompletionVersion(PropertyImpl property, WrapperProperty kind) {

			Release release = StateService.getInstance(property.getProject()).getCache().getReleases(kind.artifactId())
					.stream()
					.filter(Predicate.not(Release::isPreview))
					.sorted(Comparator.reverseOrder())
					.findFirst()
					.orElse(null);
			return release != null ? release.getVersion().toString() : fallbackVersion(kind);
		}

		private static String fallbackVersion(WrapperProperty kind) {
			return switch (kind) {
			case DISTRIBUTION -> DISTRIBUTION_FALLBACK_VERSION;
			case WRAPPER -> WRAPPER_FALLBACK_VERSION;
			};
		}

		LookupElement lookupElement() {
			return LookupElementBuilder.create(url)
					.withTypeText("Default URL", true)
					.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
		}

	}

}
