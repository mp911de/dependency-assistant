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

import java.util.function.Supplier;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.artifact.VersionSource.VersionProperty;
import biz.paluch.dap.gradle.GradleDependency.PropertyManagedDependency;
import biz.paluch.dap.gradle.GradleDependency.SimpleDependency;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.ProjectProperty;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.support.VersionedDependencySite;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Utility methods for resolving Gradle {@link ArtifactReference}s.
 *
 * @author Mark Paluch
 */
class ArtifactReferenceUtils {

	public static ArtifactReference resolve(LookupSite.PropertyLookupSite site, @Nullable ProjectState projectState) {

		if (projectState == null) {
			return ArtifactReference.unresolved();
		}

		ProjectProperty projectProperty = projectState.findProjectProperty(site.propertyName());
		CachedArtifact artifact = getFirstArtifact(projectProperty);
		if (artifact == null) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReference.from(it -> {
			it.artifact(artifact.toArtifactId()).declarationElement(site.declarationElement())
					.versionSource(VersionSource.versionCatalogProperty(site.propertyName()));
			ArtifactVersion.from(site.version()).ifPresent(it::version);
			it.versionLiteral(site.versionElement());
		});
	}

	/**
	 * Resolve the given {@link GradleDependency} to an {@link ArtifactReference}.
	 * @param dependency the dependency.
	 * @param declaration the declaration (usage) element.
	 * @param version the version literal element.
	 * @param propertyResolver property resolver to resolve property-managed
	 * dependencies.
	 * @return the resolved artifact reference.
	 */
	public static ArtifactReference resolve(GradleDependency dependency, PsiElement declaration, PsiElement version,
			PropertyResolver propertyResolver) {

		if (dependency instanceof PropertyManagedDependency managed) {

			PropertyValue element = propertyResolver.getElement(managed.property());
			return fromPropertyManaged(managed, declaration, element == null ? null : element.propertyValue(),
					element == null ? null : element.element());
		}

		if (dependency instanceof SimpleDependency simple) {
			return fromSimple(simple, declaration, version);
		}

		return ArtifactReference.unresolved();
	}

	/**
	 * Resolve the given {@link GradleDependency} to an {@link ArtifactReference}.
	 * @param declaration the declaration (usage) element.
	 * @param version the version literal element.
	 * @param propertyResolver property resolver to resolve property-managed
	 * dependencies.
	 * @return the resolved artifact reference.
	 */
	public static ArtifactReference resolve(ArtifactId artifactId, VersionSource versionSource, PsiElement declaration,
			@Nullable PsiElement version, PropertyResolver propertyResolver) {

		if (versionSource instanceof VersionProperty property) {

			PropertyValue propertyValue = propertyResolver.getElement(property.getProperty());

			if (propertyValue == null) {
				return ArtifactReference.unresolved();
			}

			return ArtifactReference.from(it -> {
				it.artifact(artifactId).declarationElement(declaration)
						.versionSource(versionSource);
				if (StringUtils.hasText(propertyValue.propertyValue())) {
					ArtifactVersion.from(propertyValue.propertyValue()).ifPresent(it::version);
				}
				it.versionLiteral(propertyValue.element());
			});
		}

		return ArtifactReference.from(it -> {
			it.artifact(artifactId).declarationElement(declaration).versionSource(versionSource);
			if (versionSource.isDefined()) {
				ArtifactVersion.from(versionSource.toString()).ifPresent(it::version);
			}
			if (version != null) {
				it.versionLiteral(version);
			}
		});
	}

	public static ArtifactReference resolve(DependencySite dependencySite,
			Supplier<PropertyResolver> propertyResolverSupplier) {

		if (dependencySite instanceof VersionedDependencySite versioned) {
			return ArtifactReference.from(versioned);
		}

		if (dependencySite.getVersionSource() instanceof VersionProperty property) {

			PropertyValue element = propertyResolverSupplier.get()
					.getElement(property.getProperty());

			if (element != null) {
				return ArtifactReference.from(it -> {
					it.artifact(dependencySite.getArtifactId())
							.versionSource(dependencySite.getVersionSource())
							.declarationElement(dependencySite.getDeclarationElement())
							.versionLiteral(element.element());
					ArtifactVersion.from(element.propertyValue()).ifPresent(it::version);
				});
			}
		}

		return ArtifactReference.from(it -> {
			it.artifact(dependencySite.getArtifactId())
					.versionSource(dependencySite.getVersionSource())
					.declarationElement(dependencySite.getDeclarationElement());
		});
	}

	/**
	 * Create a resolved {@link ArtifactReference} from a property-managed
	 * dependency.
	 */
	private static ArtifactReference fromPropertyManaged(PropertyManagedDependency managed,
			PsiElement declarationElement, @Nullable String resolvedVersion, @Nullable PsiElement versionPsi) {

		return ArtifactReference.from(it -> {
			it.artifact(managed.getId()).declarationElement(declarationElement)
					.versionSource(managed.getVersionSource());
			if (StringUtils.hasText(resolvedVersion)) {
				ArtifactVersion.from(resolvedVersion).ifPresent(it::version);
			}
			if (versionPsi != null) {
				it.versionLiteral(versionPsi);
			}
		});
	}

	/**
	 * Create a resolved {@link ArtifactReference} from a simple dependency.
	 */
	private static ArtifactReference fromSimple(SimpleDependency simple, PsiElement declarationElement,
			@Nullable PsiElement versionPsi) {

		return ArtifactReference.from(it -> {
			it.artifact(simple.getId()).declarationElement(declarationElement).versionSource(simple.getVersionSource());
			ArtifactVersion.from(simple.version()).ifPresent(it::version);
			if (versionPsi != null) {
				it.versionLiteral(versionPsi);
			}
		});
	}

	private static @Nullable CachedArtifact getFirstArtifact(@Nullable ProjectProperty property) {

		if (property == null || property.property().artifacts().isEmpty()) {
			return null;
		}

		return property.property().artifacts().getFirst();
	}

}
