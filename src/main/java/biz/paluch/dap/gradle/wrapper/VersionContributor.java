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

import biz.paluch.dap.assistant.completion.VersionContributorSupport;
import biz.paluch.dap.support.DependencyUpdate;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;

/**
 * Completion contributor for the version segment in Gradle wrapper URLs.
 *
 * @author Mark Paluch
 */
class VersionContributor extends VersionContributorSupport {

	/**
	 * Matches a caret position inside a Gradle wrapper version segment.
	 * <pre class="code">
	 * distributionUrl=https\://services.gradle.org/distributions/gradle-8.&lt;caret&gt;14.3-bin.zip
	 * </pre>
	 */
	static final PsiElementPattern.Capture<PsiElement> WRAPPER_VERSION = PROPERTY_VALUE
			.inside(PlatformPatterns.psiElement(PropertyImpl.class).withName(WrapperProperty.propertyNames()));

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		return super.invokeAutoPopup(position, typeChar) && WRAPPER_VERSION.accepts(position);
	}

	@Override
	protected List<TextRange> getVersionRanges(PropertyImpl property) {
		return GradleWrapperUtils.getVersionRanges(property);
	}

	@Override
	protected void applyVersionUpdate(PsiElement versionLiteral, DependencyUpdate update) {
		UpdateGradleWrapperProperties.applyUpdate(versionLiteral, update);
	}

}
