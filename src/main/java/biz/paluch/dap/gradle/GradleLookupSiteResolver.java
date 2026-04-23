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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.LookupSite.ResolvedSite;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.PropertyResolver;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

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

	public ArtifactReference resolve(LookupSite site) {

		Assert.notNull(site, "Lookup site must not be null");
		if (site.isAbsent()) {
			return ArtifactReference.unresolved();
		}

		if (site instanceof LookupSite.PropertyLookupSite propertySite) {
			return ArtifactReferenceUtils.resolve(propertySite, projectState);
		}

		if (site instanceof LookupSite.DependencyLookupSite(GradleDependency dependency, PsiElement declarationElement, PsiElement versionElement)) {
			return ArtifactReferenceUtils.resolve(dependency, declarationElement, versionElement, propertyResolver);
		}

		if (site instanceof LookupSite.ArtifactIdLookupSite(ArtifactId artifactId, VersionSource versionSource, PsiElement declarationElement, @Nullable PsiElement versionElement)) {
			return ArtifactReferenceUtils.resolve(artifactId, versionSource, declarationElement, versionElement,
					propertyResolver);
		}

		if (site instanceof LookupSite.TomlCatalogLookupSite(TomlReference reference, PsiElement declarationElement)) {
			return tomlResolver.resolveReference(reference, declarationElement);
		}

		if (site instanceof ResolvedSite resolvedSite) {
			return ArtifactReferenceUtils.resolve(resolvedSite);
		}

		throw new IllegalStateException("Unsupported dependency site: " + site);
	}

}
