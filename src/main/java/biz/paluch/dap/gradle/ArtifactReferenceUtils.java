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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.artifact.VersionSource.VersionProperty;
import biz.paluch.dap.gradle.GradleVersionSite.BackingProperty;
import biz.paluch.dap.gradle.GradleVersionSite.DirectCoordinate;
import biz.paluch.dap.gradle.GradleVersionSite.MapLiteralVersion;
import biz.paluch.dap.gradle.GradleVersionSite.MapPropertyVersion;
import biz.paluch.dap.gradle.GradleVersionSite.PluginVersion;
import biz.paluch.dap.gradle.GradleVersionSite.VersionBlockPreferLiteral;
import biz.paluch.dap.gradle.GradleVersionSite.VersionBlockPreferProperty;
import biz.paluch.dap.gradle.GradleVersionSite.VersionBlockStrictlyLiteral;
import biz.paluch.dap.gradle.GradleVersionSite.VersionBlockStrictlyProperty;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.ProjectProperty;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
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
	public static ArtifactReference resolve(BackingProperty site, @Nullable ProjectState projectState) {

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
	 * Resolve a {@link DependencySite} to an {@link ArtifactReference}.
	 *
	 * <p>
	 * When the supplied site is a {@link GradleVersionSite} variant the
	 * version-literal PSI carried by the variant is preserved; otherwise the
	 * fallback path mirrors the legacy {@link VersionedDependencySite} resolution.
	 */
	public static ArtifactReference resolve(DependencySite dependencySite,
			Supplier<PropertyResolver> propertyResolverSupplier) {

		if (dependencySite instanceof VersionedDependencySite versioned) {
			return ArtifactReference.from(versioned);
		}

		PsiElement versionLiteral = versionLiteralOf(dependencySite);
		ArtifactVersion resolvedVersion = dependencySite instanceof GradleVersionSite.VersionAware aware
				? aware.version()
				: null;
		VersionSource versionSource = dependencySite.getVersionSource();

		if (versionSource instanceof VersionProperty property) {

			PropertyValue element = propertyResolverSupplier.get().getPropertyValue(property.getProperty());

			if (element != null) {
				return ArtifactReference.from(it -> {
					it.artifact(dependencySite.getArtifactId())
							.versionSource(versionSource)
							.declarationElement(dependencySite.getDeclarationElement())
							.versionLiteral(element.getValueLiteral());
					ArtifactVersion.from(element.getValue()).ifPresent(it::version);
				});
			}
		}

		return ArtifactReference.from(it -> {
			it.artifact(dependencySite.getArtifactId())
					.versionSource(versionSource)
					.declarationElement(dependencySite.getDeclarationElement());
			if (resolvedVersion != null) {
				it.version(resolvedVersion);
			} else if (versionSource.isDefined()) {
				ArtifactVersion.from(versionSource.toString()).ifPresent(it::version);
			}
			if (versionLiteral != null) {
				it.versionLiteral(versionLiteral);
			}
		});
	}

	private static @Nullable PsiElement versionLiteralOf(DependencySite site) {
		return switch (site) {
		case DirectCoordinate direct -> direct.versionLiteral();
		case MapLiteralVersion map -> map.versionLiteral();
		case MapPropertyVersion mapProperty -> mapProperty.versionReferenceElement();
		case VersionBlockPreferLiteral prefer -> prefer.versionLiteral();
		case VersionBlockPreferProperty prefer -> prefer.versionReferenceElement();
		case VersionBlockStrictlyLiteral strictly -> strictly.versionLiteral();
		case VersionBlockStrictlyProperty strictly -> strictly.versionReferenceElement();
		case PluginVersion plugin -> plugin.versionLiteral();
		default -> null;
		};
	}

	private static @Nullable CachedArtifact getFirstArtifact(@Nullable ProjectProperty property) {

		if (property == null || property.property().artifacts().isEmpty()) {
			return null;
		}

		return property.property().artifacts().getFirst();
	}

}
