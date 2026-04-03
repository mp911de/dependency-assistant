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

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.support.NewerVersionLineMarkerProviderSupport;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import icons.MavenIcons;

import com.intellij.psi.PsiElement;

/**
 * Gutter line marker that indicates a newer Maven dependency or plugin version in a {@code pom.xml}.
 * <p>
 * The marker appears on the line of the version value — either a literal {@code <version>} tag inside a
 * {@code <dependency>} or {@code <plugin>}, or a {@code <properties>} child tag whose name maps to a known artifact in
 * the cache. The icon reflects the highest available upgrade tier: patch, minor, or major.
 * <p>
 * Version resolution is delegated to {@link VersionUpgradeLookupService}. Clicking the gutter icon invokes the
 * {@link UpdateDependenciesAction}.
 */
public class NewerVersionLineMarkerProvider extends NewerVersionLineMarkerProviderSupport {

	public NewerVersionLineMarkerProvider() {
		super("biz.paluch.dap.maven.UpdateDependencies", DependencyAssistantIcons.MAVEN_TRANSPARENT_ICON,
				MavenIcons.ParentProject);
	}

	@Override
	protected VersionUpgradeLookupSupport getVersionLookupSupport(PsiElement element) {
		return VersionUpgradeLookupService.create(element);
	}

}
