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
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.VersionedDependencySite;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Interface representing a Gradle version declaration site.
 *
 * <p>Produced by per-language locators (Groovy, Kotlin, TOML) for the PSI under
 * the caret and by the parsers when enumerating an entire build file. A
 * {@link CoordinateSite} carries a resolvable artifact identity and therefore
 * also implements {@link DependencySite}; {@link BackingProperty},
 * {@link TomlCatalogAlias}, and {@link Absent} do not, because their artifact
 * identity is either established only through resolution or absent altogether.
 *
 * <p>Gradle-internal abstraction.
 *
 * @author Mark Paluch
 * @see GroovyVersionSiteLocator
 * @see KotlinVersionSiteLocator
 * @see TomlVersionSiteLocator
 * @see GradleVersionSiteResolver
 */
sealed interface GradleVersionSite permits GradleVersionSite.CoordinateSite, GradleVersionSite.BackingProperty,
		GradleVersionSite.TomlCatalogAlias, GradleVersionSite.Absent {

	/**
	 * Return the artifact identifier for this site.
	 *
	 * @return the artifact identifier.
	 * @throws UnsupportedOperationException if the site does not define an artifact
	 * id (such as a backing-property or TOML-catalog-alias site).
	 */
	ArtifactId getArtifactId();

	/**
	 * Return the PSI element that owns this declaration site.
	 */
	PsiElement getDeclarationElement();

	/**
	 * Return whether this instance represents an actual declaration site.
	 */
	default boolean isPresent() {
		return true;
	}

	/**
	 * Return whether this site is absent (i.e. not {@link #isPresent()}.
	 */
	default boolean isAbsent() {
		return !isPresent();
	}

	/**
	 * Return the declaration source.
	 */
	DeclarationSource getDeclarationSource();

	/**
	 * Return an absent version site.
	 */
	static GradleVersionSite absent() {
		return Absent.INSTANCE;
	}

	/**
	 * Marker exposing the resolved {@link ArtifactVersion} for sites that carry one
	 * in addition to the raw {@link VersionSource}.
	 */
	interface VersionAware {

		@Nullable
		ArtifactVersion version();

	}

	/**
	 * Extract the resolved version anchor from a parser-derived
	 * {@link DependencySite}, returning {@literal null} when only a
	 * {@link VersionSource} is available.
	 */
	static @Nullable ArtifactVersion versionOf(DependencySite site) {
		return site instanceof VersionedDependencySite versioned ? versioned.getVersion() : null;
	}

	/**
	 * A resolvable Gradle coordinate declaration, regardless of the surface
	 * notation that produced it (direct {@code 'g:a:v'} strings, map-style named
	 * arguments, {@code version { prefer/strictly }} blocks, or plugin
	 * declarations).
	 *
	 * <p>The {@code versionAnchor} is the PSI element to which a version edit or
	 * lookup applies; it may be a version literal or a property reference.
	 */
	// TODO: could this be directly ArtifactDeclaration?
	record CoordinateSite(ArtifactId artifactId, VersionSource versionSource, DeclarationSource declarationSource,
			PsiElement declarationElement, PsiElement versionAnchor, @Nullable ArtifactVersion version)
			implements GradleVersionSite, DependencySite, VersionAware {

		@Override
		public ArtifactId getArtifactId() {
			return artifactId;
		}

		@Override
		public VersionSource getVersionSource() {
			return versionSource;
		}

		@Override
		public DeclarationSource getDeclarationSource() {
			return declarationSource;
		}

		@Override
		public PsiElement getDeclarationElement() {
			return declarationElement;
		}

	}

	/**
	 * Property declaration (a {@code gradle.properties} key, an
	 * {@code ext.springVersion = '…'} assignment, or an
	 * {@code extra["springVersion"] = "…"} entry) that backs versions referenced
	 * elsewhere.
	 *
	 * <p>Does not implement {@link DependencySite} because the artifact identity is
	 * established through resolution against declarations that reference the
	 * property.
	 */
	record BackingProperty(String propertyName, String version, PsiElement declarationElement,
			PsiElement versionElement) implements GradleVersionSite {

		/**
		 * This site type does not define an artifact id.
		 */
		@Override
		public ArtifactId getArtifactId() {
			throw new UnsupportedOperationException();
		}

		@Override
		public DeclarationSource getDeclarationSource() {
			throw new UnsupportedOperationException();
		}

		@Override
		public PsiElement getDeclarationElement() {
			return declarationElement;
		}

	}

	/**
	 * Reference to a Gradle version-catalog alias such as {@code libs.spring.core}.
	 *
	 * <p>Does not implement {@link DependencySite} because the artifact identity is
	 * established through resolution against the referenced TOML catalog.
	 */
	record TomlCatalogAlias(TomlReference reference, PsiElement declarationElement) implements GradleVersionSite {

		/**
		 * This site type does not define an artifact id.
		 */
		@Override
		public ArtifactId getArtifactId() {
			throw new UnsupportedOperationException("TOML catalog aliases do not define an artifact id");
		}

		@Override
		public PsiElement getDeclarationElement() {
			return declarationElement;
		}

		@Override
		public DeclarationSource getDeclarationSource() {
			return DeclarationSource.managed();
		}

	}

	/**
	 * Singleton instance representing an absent version site.
	 */
	enum Absent implements GradleVersionSite {

		INSTANCE;

		@Override
		public boolean isPresent() {
			return false;
		}

		@Override
		public ArtifactId getArtifactId() {
			throw new IllegalStateException("Absent version site has no declaration element");
		}

		@Override
		public DeclarationSource getDeclarationSource() {
			throw new IllegalStateException("Absent version site has no declaration element");
		}

		@Override
		public PsiElement getDeclarationElement() {
			throw new IllegalStateException("Absent version site has no declaration element");
		}

	}

}
