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
package biz.paluch.dap.state;

import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import org.jspecify.annotations.Nullable;

/**
 * Runtime view of dependency and property state for a single analyzed project.
 * <p>Implementations combine transient dependency analysis results with
 * persistent property correlations from the plugin cache. Unless stated
 * otherwise, lookup methods return {@code null} when no matching state is
 * currently available.
 *
 * @author Mark Paluch
 */
public interface ProjectState {

	/**
	 * Find a dependency by its artifact coordinates.
	 *
	 * @param artifactId the dependency coordinates to locate.
	 * @return the matching dependency, or {@code null} if the current runtime state
	 * does not contain it.
	 */
	@Nullable
	Dependency findDependency(ArtifactId artifactId);

	/**
	 * Replace the current runtime dependency state of this project.
	 *
	 * @param collector the freshly analyzed dependency collector.
	 */
	void setDependencies(DependencyCollector collector);

	/**
	 * Return whether dependencies are available.
	 *
	 * @return {@code true} if dependencies were set and not yet invalidated.
	 */
	boolean hasDependencies();

	/**
	 * Discard the current runtime dependency state.
	 */
	void invalidateDependencies();

	/**
	 * Find a property by name that is associated with at least one artifact.
	 *
	 * @param propertyName the property name to locate.
	 * @return the matching property, or {@code null} if none is known or no
	 * artifact correlation exists.
	 */
	@Nullable
	default VersionProperty findProperty(String propertyName) {
		return findProperty(propertyName, VersionProperty::hasArtifacts);
	}

	/**
	 * Find a property by name using the given filter.
	 *
	 * @param propertyName the property name to locate.
	 * @param filter the predicate that must accept the matching property.
	 * @return the matching property, or {@code null}.
	 */
	@Nullable
	VersionProperty findProperty(String propertyName, Predicate<VersionProperty> filter);

	/**
	 * Find a project property by name that is associated with at least one
	 * artifact.
	 *
	 * @param propertyName the property name to locate.
	 * @return the matching project property, or {@code null} if none is known or no
	 * artifact correlation exists.
	 */

	default @Nullable ProjectProperty findProjectProperty(String propertyName) {
		return findProjectProperty(propertyName, VersionProperty::hasArtifacts);
	}

	/**
	 * Find a project property by name using the given filter.
	 *
	 * @param propertyName the property name to locate.
	 * @param filter the predicate that must accept the matching property.
	 * @return the matching project property, or {@code null}.
	 */
	@Nullable
	ProjectProperty findProjectProperty(String propertyName, Predicate<VersionProperty> filter);

}
