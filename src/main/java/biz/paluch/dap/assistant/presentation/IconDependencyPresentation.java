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
 * Dependency presentation with an integration-selected table icon.
 * <p>Package-system identity comes from the dependency, not the assistant.
 *
 * @author Mark Paluch
 */
public interface IconDependencyPresentation extends DependencyPresentation, DependencyIcons, HasPackageSystem {

	/**
	 * Create a coordinate-only presentation with the selected table icon.
	 * <p>Use {@link DependencyPresentationFactory} to resolve optional names.
	 */
	static IconDependencyPresentation from(Dependency dependency, InterfaceAssistant assistant) {
		return new DefaultIconDependencyPresentation(assistant.getTableIcon(dependency),
				DependencyPresentation.of(dependency.getPackageIdentity()));
	}

}
