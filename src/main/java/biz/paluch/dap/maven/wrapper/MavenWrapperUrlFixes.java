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

import java.util.function.Function;
import java.util.function.UnaryOperator;

import biz.paluch.dap.support.MessageBundle;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.properties.psi.PropertyKeyValueFormat;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;

/**
 * Quick-fix actions for {@link MavenWrapperUrlProblem} variants.
 *
 * <p>Each factory returns a fresh {@link PsiUpdateModCommandAction} bound to
 * {@link PropertyImpl} via the {@code Class}-based super constructor; the
 * action resolves its target property from the caret offset at execution time
 * and therefore holds no PSI reference of its own.
 *
 * @author Mark Paluch
 */
class MavenWrapperUrlFixes {

	private MavenWrapperUrlFixes() {
	}

	/**
	 * Create a fix that strips embedded credentials from the wrapper URL.
	 * @return a fresh fix instance.
	 */
	static PsiUpdateModCommandAction<PropertyImpl> stripCredentials() {
		return new WrapperUrlFix(
				MavenWrapperUrlRewriter::stripCredentials,
				"inspection.maven-wrapper.credentials-in-url.fix");
	}

	/**
	 * Create a fix that rewrites both version segments of the wrapper URL to the
	 * given version.
	 * @param version the canonical version to apply.
	 * @return a fresh fix instance.
	 */
	static PsiUpdateModCommandAction<PropertyImpl> replaceVersion(String version) {
		return new WrapperUrlFix(
				url -> MavenWrapperUrlRewriter.replaceVersion(url, version),
				"inspection.maven-wrapper.inconsistent-version.fix",
				version);
	}

	/**
	 * Create a fix that rewrites both artifact-id segments of the wrapper URL to
	 * the canonical artifact for the given property.
	 * @param property the wrapper property.
	 * @return a fresh fix instance.
	 */
	static PsiUpdateModCommandAction<PropertyImpl> replaceArtifact(WrapperProperty property) {

		String canonicalArtifactId = property.canonicalArtifactId();
		return new WrapperUrlFix(
				url -> MavenWrapperUrlRewriter.replaceArtifact(url, canonicalArtifactId),
				"inspection.maven-wrapper.inconsistent-artifact.fix",
				canonicalArtifactId);
	}

	/**
	 * Create a fix that rewrites the trailing segments of the captured group path
	 * to the canonical group-path tail for the given property, preserving any
	 * mirror prefix.
	 * @param property the wrapper property.
	 * @return a fresh fix instance.
	 */
	static PsiUpdateModCommandAction<PropertyImpl> replaceGroupPath(WrapperProperty property) {

		String canonicalGroupPath = property.canonicalGroupPath();
		return new WrapperUrlFix(
				url -> MavenWrapperUrlRewriter.replaceGroupPath(url, canonicalGroupPath),
				"inspection.maven-wrapper.improper-group-id.fix",
				canonicalGroupPath);
	}

	/**
	 * Create a fix that rewrites the file-name segment of the wrapper URL to the
	 * canonical name for the given property and version.
	 * @param property the wrapper property.
	 * @param version the canonical version.
	 * @return a fresh fix instance.
	 */
	static PsiUpdateModCommandAction<PropertyImpl> replaceFileName(WrapperProperty property, String version) {
		return new WrapperUrlFix(
				url -> MavenWrapperUrlRewriter.replaceFileName(url, property, version),
				"inspection.maven-wrapper.malformed-file-name.fix",
				url -> new Object[] {MavenWrapperUrlRewriter.replaceFileNameSuggestion(url, property, version)});
	}

	/**
	 * Create a fix that replaces the wrapper URL with the canonical Maven Central
	 * URL for the given property and version.
	 * @param property the wrapper property.
	 * @param version the canonical version.
	 * @return a fresh fix instance.
	 */
	static PsiUpdateModCommandAction<PropertyImpl> useDefaultUrl(WrapperProperty property, String version) {
		return new WrapperUrlFix(
				url -> MavenWrapperUrlRewriter.canonicalUrl(property, version),
				"inspection.maven-wrapper.default-url.fix", version);
	}

	static class WrapperUrlFix extends PsiUpdateModCommandAction<PropertyImpl> {

		private final UnaryOperator<String> rewrite;

		private final String messageKey;

		private final Function<String, Object[]> messageArgs;

		WrapperUrlFix(UnaryOperator<String> rewrite, String messageKey, Object... staticArgs) {
			super(PropertyImpl.class);
			this.rewrite = rewrite;
			this.messageKey = messageKey;
			this.messageArgs = value -> staticArgs;
		}

		WrapperUrlFix(UnaryOperator<String> rewrite, String messageKey, Function<String, Object[]> messageArgs) {
			super(PropertyImpl.class);
			this.rewrite = rewrite;
			this.messageKey = messageKey;
			this.messageArgs = messageArgs;
		}

		@Override
		protected void invoke(ActionContext context, PropertyImpl property, ModPsiUpdater updater) {
			property.setValue(rewrite.apply(property.getUnescapedValue()), PropertyKeyValueFormat.FILE);
		}

		@Override
		protected Presentation getPresentation(ActionContext context, PropertyImpl property) {
			Object[] args = messageArgs.apply(property.getUnescapedValue());
			return Presentation.of(MessageBundle.message(messageKey, args));
		}

		@Override
		public @IntentionFamilyName String getFamilyName() {
			return MessageBundle.message("inspection.maven-wrapper.display-name");
		}

	}

}
