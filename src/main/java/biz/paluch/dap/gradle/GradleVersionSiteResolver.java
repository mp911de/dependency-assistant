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

import biz.paluch.dap.gradle.GradleVersionSite.BackingProperty;
import biz.paluch.dap.gradle.GradleVersionSite.TomlCatalogAlias;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyResolver;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Resolves semantic Gradle {@link GradleVersionSite version sites} to
 * {@link ArtifactReference}s.
 *
 * @author Mark Paluch
 */
class GradleVersionSiteResolver {

	private final PropertyResolver propertyResolver;

	private final @Nullable ProjectState projectState;

	private final TomlArtifactResolver tomlResolver;

	GradleVersionSiteResolver(PropertyResolver propertyResolver, @Nullable ProjectState projectState,
			TomlArtifactResolver tomlResolver) {
		this.propertyResolver = propertyResolver;
		this.projectState = projectState;
		this.tomlResolver = tomlResolver;
	}

	/**
	 * Resolve the given version site to an artifact reference.
	 */
	public ArtifactReference resolve(GradleVersionSite site) {

		Assert.notNull(site, "Gradle version site must not be null");
		if (site.isAbsent()) {
			return ArtifactReference.unresolved();
		}

		if (site instanceof BackingProperty backingProperty) {
			return ArtifactReferenceUtils.resolve(backingProperty, projectState);
		}

		if (site instanceof TomlCatalogAlias tomlAlias) {
			return tomlResolver.resolveReference(tomlAlias.reference(), tomlAlias.declarationElement());
		}

		if (site instanceof DependencySite dependencySite) {
			return ArtifactReferenceUtils.resolve(dependencySite, () -> propertyResolver);
		}

		throw new IllegalStateException("Unsupported version site: " + site);
	}

}
