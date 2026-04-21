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

import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.PropertyResolver;
import org.jspecify.annotations.Nullable;

/**
 * Resolves semantic Gradle lookup sites to {@link ArtifactReference}s.
 *
 * @author Mark Paluch
 */
class GradleLookupSiteResolver {

	private final PropertyResolver propertyResolver;

	private final @Nullable ProjectState projectState;

	private final TomlArtifactResolver tomlResolver;

	GradleLookupSiteResolver(PropertyResolver propertyResolver, @Nullable ProjectState projectState,
			TomlArtifactResolver tomlResolver) {
		this.propertyResolver = propertyResolver;
		this.projectState = projectState;
		this.tomlResolver = tomlResolver;
	}

	ArtifactReference resolve(@Nullable GradleLookupSite site) {

		if (site == null) {
			return ArtifactReference.unresolved();
		}

		if (site instanceof GradleLookupSite.GradleVersionSite versionSite) {
			return ArtifactReferenceUtils.resolve(versionSite, propertyResolver);
		}

		if (site instanceof GradlePropertySite propertySite) {
			return ArtifactReferenceUtils.resolve(propertySite, projectState);
		}

		if (site instanceof GradleCatalogReferenceSite catalogReferenceSite) {
			return tomlResolver.resolveReference(catalogReferenceSite.reference(),
					catalogReferenceSite.declarationElement());
		}

		return ArtifactReference.unresolved();
	}

}
