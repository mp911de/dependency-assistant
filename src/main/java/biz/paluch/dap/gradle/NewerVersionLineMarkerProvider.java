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
package biz.paluch.dap.gradle;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.support.NewerVersionLineMarkerProviderSupport;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import icons.GradleIcons;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * Gutter line marker for Gradle build files that indicates a newer dependency version is available. Clicking the icon
 * invokes the "Update Gradle Dependencies" action.
 *
 * @author Mark Paluch
 */
public class NewerVersionLineMarkerProvider extends NewerVersionLineMarkerProviderSupport {

	public NewerVersionLineMarkerProvider() {
		super("biz.paluch.dap.gradle.UpdateDependencies", DependencyAssistantIcons.GRADLE_TRANSPARENT_ICON,
				GradleIcons.GradleNavigate);
	}

	@Override
	protected VersionUpgradeLookupSupport getVersionLookupSupport(PsiElement element) {
		return new VersionUpgradeLookupService(element.getProject(), element.getContainingFile());
	}

	@Override
	protected TextRange getTextRange(PsiElement element) {
		return NewerVersionAnnotator.getRange(element);
	}
}
