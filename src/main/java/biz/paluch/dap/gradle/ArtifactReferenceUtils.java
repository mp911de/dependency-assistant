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

import java.util.function.Consumer;
import java.util.function.Supplier;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.artifact.VersionSource.VersionProperty;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.ProjectProperty;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.VersionedDependencySite;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Utility methods for resolving Gradle {@link ArtifactReference}s.
 *
 * @author Mark Paluch
 */
class ArtifactReferenceUtils {

	/**
	 * Resolve a backing-property site through the project state.
	 */
	static ArtifactReference resolve(String propertyName, String version,
			PsiElement declarationElement, PsiElement versionElement, @Nullable ProjectState projectState) {

		if (projectState == null) {
			return ArtifactReference.unresolved();
		}

		ProjectProperty projectProperty = projectState.findProjectProperty(propertyName);
		CachedArtifact artifact = getFirstArtifact(projectProperty);
		if (artifact == null) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReference.from(it -> {
			it.artifact(artifact.toArtifactId())
					.declarationElement(declarationElement)
					.declarationSource(DeclarationSource.managed())
					.versionSource(VersionSource.versionCatalogProperty(propertyName));
			ArtifactVersion.from(version).ifPresent(it::version);
			it.versionLiteral(versionElement);
		});
	}

	/**
	 * Resolve a {@link DependencySite} to an {@link ArtifactReference}.
	 *
	 * <p>Resolved sites preserve their version-literal PSI. Property-backed sites
	 * are enriched through the supplied property resolver.
	 */
	static ArtifactReference resolve(DependencySite dependencySite,
			Supplier<PropertyResolver> propertyResolverSupplier) {

		if (dependencySite instanceof VersionedDependencySite versioned) {
			return ArtifactReference.from(versioned);
		}

		VersionSource versionSource = dependencySite.getVersionSource();

		if (versionSource instanceof VersionProperty property) {

			PropertyResolver propertyResolver = propertyResolverSupplier.get();
			Property element = propertyResolver.getPropertyValue(property.getProperty());

			if (element != null) {
				return reference(dependencySite, it -> {
					it.versionLiteral(element.getValueLiteral());
					ArtifactVersion.from(element.getValue()).ifPresent(it::version);
				});
			}

			String value = propertyResolver.getProperty(property.getProperty());
			if (value != null) {
				return reference(dependencySite, it -> ArtifactVersion.from(value).ifPresent(it::version));
			}
		}

		return reference(dependencySite, it -> {
			if (versionSource.isDefined()) {
				ArtifactVersion.from(versionSource.toString()).ifPresent(it::version);
			}
		});
	}

	/**
	 * Build a reference carrying the structural identity of the given site, with
	 * the version-specific builder steps applied on top.
	 */
	private static ArtifactReference reference(DependencySite dependencySite,
			Consumer<ArtifactDeclaration.Builder> version) {

		return ArtifactReference.from(it -> {
			it.artifact(dependencySite.getArtifactId())
					.versionSource(dependencySite.getVersionSource())
					.declarationSource(dependencySite.getDeclarationSource())
					.declarationElement(dependencySite.getDeclarationElement());
			version.accept(it);
		});
	}

	private static @Nullable CachedArtifact getFirstArtifact(@Nullable ProjectProperty property) {

		if (property == null || property.property().artifacts().isEmpty()) {
			return null;
		}

		return property.property().artifacts().getFirst();
	}

}
