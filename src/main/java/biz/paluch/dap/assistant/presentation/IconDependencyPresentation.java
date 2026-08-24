/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.assistant.presentation;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.HasPackageSystem;

/**
 * A {@link DependencyPresentation} paired with the integration-selected table
 * icon for the dependency.
 *
 * <p>Instances with resolved optional names are created through
 * {@link DependencyPresentationFactory#create(Dependency, biz.paluch.dap.rule.DependencyRule, InterfaceAssistant)};
 * {@link #from(Dependency, InterfaceAssistant)} creates a coordinate-only
 * variant. Package-system identity comes from the retained
 * {@link #getPackageIdentity() package identity}, not from the assistant.
 *
 * @author Mark Paluch
 * @see DependencyPresentationFactory
 * @see DependencyIcons
 */
public interface IconDependencyPresentation extends DependencyPresentation, DependencyIcons, HasPackageSystem {

	/**
	 * Create a coordinate-only presentation with the assistant-selected table icon.
	 *
	 * <p>The result retains the selected icon and package identity, but not the
	 * dependency or assistant. It carries no dependency or project name. Use
	 * {@link DependencyPresentationFactory#create(Dependency, biz.paluch.dap.rule.DependencyRule, InterfaceAssistant)}
	 * to resolve names from dependency rules and cached artifact metadata.
	 *
	 * @param dependency the dependency to present.
	 * @param assistant the interface assistant that selects the table icon.
	 * @return a detached coordinate-only presentation for the given dependency.
	 */
	static IconDependencyPresentation from(Dependency dependency, InterfaceAssistant assistant) {
		return new DefaultIconDependencyPresentation(assistant.getTableIcon(dependency),
				DependencyPresentation.of(dependency.getPackageIdentity()));
	}

}
