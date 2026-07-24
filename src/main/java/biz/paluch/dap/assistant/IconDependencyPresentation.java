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

package biz.paluch.dap.assistant;

import biz.paluch.dap.DependencyPresentation;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.Dependency;

/**
 * A {@link DependencyPresentation} enriched with table rendering concerns: the
 * icon shown next to the dependency and the identity of the ecosystem the
 * dependency originates from.
 *
 * <p>Instances with fully resolved names are created through
 * {@link DependencyPresentationFactory#create(Dependency, biz.paluch.dap.rule.DependencyRule, InterfaceAssistant)};
 * {@link #from(Dependency, InterfaceAssistant)} creates a coordinate-only
 * variant.
 *
 * @author Mark Paluch
 * @see DependencyPresentationFactory
 * @see DependencyIcons
 */
public interface IconDependencyPresentation extends DependencyPresentation, DependencyIcons {

	/**
	 * Create a presentation from the dependency's coordinates and the assistant's
	 * table icon.
	 *
	 * <p>The resulting presentation carries no dependency or project name. Use
	 * {@link DependencyPresentationFactory#create(Dependency, biz.paluch.dap.rule.DependencyRule, InterfaceAssistant)}
	 * to resolve names from dependency rules and cached artifact metadata.
	 *
	 * @param dependency the dependency to present.
	 * @param assistant the assistant declaring the dependency; supplies the table
	 * icon and the ecosystem identity.
	 * @return a coordinate-only presentation for the given dependency.
	 */
	static IconDependencyPresentation from(Dependency dependency, InterfaceAssistant assistant) {
		return new DefaultIconDependencyPresentation(assistant.getTableIcon(dependency),
				assistant.getClass().getName(), DependencyPresentation.of(dependency.getArtifactId()));
	}

	/**
	 * Return the identifier of the ecosystem the dependency originates from.
	 * Presentations produced by the same assistant share the same identifier; the
	 * value discriminates upgrade grouping across ecosystems.
	 *
	 * @return the ecosystem identifier.
	 */
	String getEcosystem();

}
