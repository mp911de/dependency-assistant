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

package biz.paluch.dap.gradle.wrapper;

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
 * Quick-fix actions for {@link GradleWrapperUrlProblem} variants.
 *
 * @author Mark Paluch
 */
class GradleWrapperUrlFixes {

	private GradleWrapperUrlFixes() {
	}

	static PsiUpdateModCommandAction<PropertyImpl> replaceFileName(String version) {
		return new WrapperUrlFix(url -> GradleWrapperUrlRewriter.replaceFileName(url, version),
				"inspection.gradle-wrapper.malformed-file-name.fix",
				url -> new Object[] {GradleWrapperUrlRewriter.replaceFileNameSuggestion(url, version)});
	}

	static PsiUpdateModCommandAction<PropertyImpl> useDefaultUrl(String version) {
		return new WrapperUrlFix(url -> GradleWrapperUrlRewriter.canonicalUrl(version),
				"inspection.gradle-wrapper.default-url.fix", version);
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
			return Presentation.of(MessageBundle.message(messageKey, messageArgs.apply(property.getUnescapedValue())));
		}

		@Override
		public @IntentionFamilyName String getFamilyName() {
			return MessageBundle.message("inspection.gradle-wrapper.display-name");
		}

	}

}
