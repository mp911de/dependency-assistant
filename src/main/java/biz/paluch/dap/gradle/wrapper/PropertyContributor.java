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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.assistant.PropertyContributorSupport;
import biz.paluch.dap.state.Cache;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFileFactory;

/**
 * {@link CompletionContributor} for Gradle {@link WrapperProperty wrapper
 * properties}.
 *
 * @author Mark Paluch
 */
class PropertyContributor extends PropertyContributorSupport {

	@Override
	protected void addCompletions(CompletionResultSet result, Cache cache, PsiFileFactory factory,
			PropertyImpl propertyPosition) {

		for (WrapperProperty kind : WrapperProperty.values()) {
			ArtifactRelease release = kind.getLatestArtifactRelease(cache);
			String url = GradleWrapperUrlRewriter.canonicalUrl(release);
			addPropertyLineCompletion(result, factory, propertyPosition, kind.key(), release, url);
		}
	}

	@Override
	protected List<TextRange> getVersionRanges(PropertyImpl property) {
		return GradleWrapperUtils.getVersionRanges(property);
	}

	@Override
	protected boolean supports(PropertyImpl property) {
		return WrapperProperty.isWrapperProperty(property);
	}

	@Override
	protected boolean isPropertyKeyTrigger(char typeChar) {
		for (String name : WrapperProperty.propertyNames()) {
			if (name.charAt(0) == typeChar) {
				return true;
			}
		}
		return false;
	}

}
