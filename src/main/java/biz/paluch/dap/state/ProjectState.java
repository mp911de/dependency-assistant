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

package biz.paluch.dap.state;

import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import org.jspecify.annotations.Nullable;

/**
 * Facade over runtime dependencies for one project identity and persisted
 * property correlations known to the project service.
 *
 * <p>Dependency operations are scoped to the facade's project identity.
 * Property lookups may return a correlation owned by another cached project
 * entry and therefore expose that owner through {@link ProjectProperty}. Unless
 * stated otherwise, lookup methods return {@literal null} when no matching
 * state is available.
 *
 * @author Mark Paluch
 */
public interface ProjectState {

	@Nullable
	Dependency findDependency(ArtifactId artifactId);

	/**
	 * Replace runtime dependencies and persist their property correlations and
	 * resolved BOM memberships.
	 */
	void setDependencies(DependencyCollector collector);

	/**
	 * Whether runtime dependencies have been supplied and not invalidated.
	 */
	boolean hasDependencies();

	void invalidateDependencies();

	/**
	 * Remove both runtime dependencies and persisted project state.
	 */
	void remove();

	/**
	 * Find a property with at least one artifact association.
	 */
	default @Nullable VersionProperty findProperty(String propertyName) {
		return findProperty(propertyName, VersionProperty::hasArtifacts);
	}

	@Nullable
	VersionProperty findProperty(String propertyName, Predicate<VersionProperty> filter);

	/**
	 * Find a property with at least one artifact association, retaining its owner.
	 */

	default @Nullable ProjectProperty findProjectProperty(String propertyName) {
		return findProjectProperty(propertyName, VersionProperty::hasArtifacts);
	}

	@Nullable
	ProjectProperty findProjectProperty(String propertyName, Predicate<VersionProperty> filter);

}
