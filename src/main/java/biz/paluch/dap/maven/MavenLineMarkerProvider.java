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

package biz.paluch.dap.maven;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.assistant.UpgradeAvailableLineMarkerProvider;
import biz.paluch.dap.maven.MavenAssistant.MavenInterface;
import biz.paluch.dap.support.MessageBundle;
import com.intellij.psi.PsiElement;

/**
 * Gutter configurable for Maven.
 * 
 * @author Mark Paluch
 */
public class MavenLineMarkerProvider extends UpgradeAvailableLineMarkerProvider {

	@Override
	public String getName() {
		return MessageBundle.message("gutter.upgrade-available.name", MavenInterface.INSTANCE.getDisplayName());
	}

	@Override
	public Icon getIcon() {
		return DependencyAssistantIcons.UPGRADE_MAVEN_ICON;
	}

	@Override
	protected ProjectDependencyContext getContext(PsiElement element) {

		ProjectDependencyContext context = super.getContext(element);
		if (context instanceof MavenAssistant.MavenDependencyContext
				|| context instanceof MavenWrapperAssistant.MavenWrapperDependencyContext) {
			return context;
		}

		return ProjectDependencyContext.absent();
	}

}
