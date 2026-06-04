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
 * the caret and by the parsers when enumerating an entire build file. Variants
 * that carry a resolvable artifact identity also implement
 * {@link DependencySite}; {@link BackingProperty}, {@link TomlCatalogAlias},
 * and {@link Absent} do not because their artifact identity is either
 * established only through resolution or absent altogether.
 *
 * <p>Gradle-internal abstraction.
 *
 * @author Mark Paluch
 * @see GroovyVersionSiteLocator
 * @see KotlinVersionSiteLocator
 * @see TomlVersionSiteLocator
 * @see GradleVersionSiteResolver
 */
sealed interface GradleVersionSite permits GradleVersionSite.DirectCoordinate, GradleVersionSite.MapLiteralVersion,
		GradleVersionSite.MapPropertyVersion, GradleVersionSite.VersionBlockPreferLiteral,
		GradleVersionSite.VersionBlockPreferProperty, GradleVersionSite.VersionBlockStrictlyLiteral,
		GradleVersionSite.VersionBlockStrictlyProperty, GradleVersionSite.PluginVersion,
		GradleVersionSite.BackingProperty, GradleVersionSite.TomlCatalogAlias, GradleVersionSite.Absent {

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
	 * Marker exposing the resolved {@link ArtifactVersion} for variants that carry
	 * one in addition to the raw {@link VersionSource}.
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
	 * Direct compact-string dependency notation such as {@code 'g:a:v'}.
	 */
	record DirectCoordinate(ArtifactId artifactId, VersionSource versionSource, DeclarationSource declarationSource,
			PsiElement declarationElement, PsiElement versionLiteral, @Nullable ArtifactVersion version)
			implements GradleVersionSite, DependencySite, VersionAware {

		DirectCoordinate(ArtifactId artifactId, VersionSource versionSource, DeclarationSource declarationSource,
				PsiElement declarationElement, PsiElement versionLiteral) {
			this(artifactId, versionSource, declarationSource, declarationElement, versionLiteral, null);
		}

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
	 * Map-style declaration with a literal version such as
	 * {@code group: 'g', name: 'a', version: '1.0'}.
	 */
	record MapLiteralVersion(ArtifactId artifactId, VersionSource versionSource, DeclarationSource declarationSource,
			PsiElement declarationElement, PsiElement versionLiteral, @Nullable ArtifactVersion version)
			implements GradleVersionSite, DependencySite, VersionAware {

		MapLiteralVersion(ArtifactId artifactId, VersionSource versionSource, DeclarationSource declarationSource,
				PsiElement declarationElement, PsiElement versionLiteral) {
			this(artifactId, versionSource, declarationSource, declarationElement, versionLiteral, null);
		}

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
	 * Map-style declaration with a property-backed version such as
	 * {@code group: 'g', name: 'a', version: propVar}.
	 */
	record MapPropertyVersion(ArtifactId artifactId, String propertyName, VersionSource versionSource,
			DeclarationSource declarationSource, PsiElement declarationElement, PsiElement versionReferenceElement,
			@Nullable ArtifactVersion version) implements GradleVersionSite, DependencySite, VersionAware {

		MapPropertyVersion(ArtifactId artifactId, String propertyName, VersionSource versionSource,
				DeclarationSource declarationSource, PsiElement declarationElement,
				PsiElement versionReferenceElement) {
			this(artifactId, propertyName, versionSource, declarationSource, declarationElement,
					versionReferenceElement, null);
		}

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
	 * Version-block declaration with a literal preferred version such as
	 * {@code version { prefer '1.2.3' }}.
	 */
	record VersionBlockPreferLiteral(ArtifactId artifactId, VersionSource versionSource,
			DeclarationSource declarationSource, PsiElement declarationElement, PsiElement versionLiteral,
			@Nullable ArtifactVersion version)
			implements GradleVersionSite, DependencySite, VersionAware {

		VersionBlockPreferLiteral(ArtifactId artifactId, VersionSource versionSource,
				DeclarationSource declarationSource, PsiElement declarationElement,
				PsiElement versionLiteral) {
			this(artifactId, versionSource, declarationSource, declarationElement, versionLiteral, null);
		}

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
	 * Version-block declaration with a property-backed preferred version such as
	 * {@code version { prefer springVersion }}.
	 */
	record VersionBlockPreferProperty(ArtifactId artifactId, String propertyName, VersionSource versionSource,
			DeclarationSource declarationSource, PsiElement declarationElement, PsiElement versionReferenceElement,
			@Nullable ArtifactVersion version)
			implements GradleVersionSite, DependencySite, VersionAware {

		VersionBlockPreferProperty(ArtifactId artifactId, String propertyName, VersionSource versionSource,
				DeclarationSource declarationSource, PsiElement declarationElement,
				PsiElement versionReferenceElement) {
			this(artifactId, propertyName, versionSource, declarationSource, declarationElement,
					versionReferenceElement, null);
		}

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
	 * Version-block declaration with a literal strict version such as
	 * {@code version { strictly '1.2.3' }}.
	 */
	record VersionBlockStrictlyLiteral(ArtifactId artifactId, VersionSource versionSource,
			DeclarationSource declarationSource, PsiElement declarationElement, PsiElement versionLiteral,
			@Nullable ArtifactVersion version) implements GradleVersionSite, DependencySite, VersionAware {

		VersionBlockStrictlyLiteral(ArtifactId artifactId, VersionSource versionSource,
				DeclarationSource declarationSource, PsiElement declarationElement, PsiElement versionLiteral) {
			this(artifactId, versionSource, declarationSource, declarationElement, versionLiteral, null);
		}

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
	 * Version-block declaration with a property-backed strict version such as
	 * {@code version { strictly springVersion }}.
	 */
	record VersionBlockStrictlyProperty(ArtifactId artifactId, String propertyName, VersionSource versionSource,
			DeclarationSource declarationSource, PsiElement declarationElement, PsiElement versionReferenceElement,
			@Nullable ArtifactVersion version)
			implements GradleVersionSite, DependencySite, VersionAware {

		VersionBlockStrictlyProperty(ArtifactId artifactId, String propertyName, VersionSource versionSource,
				DeclarationSource declarationSource, PsiElement declarationElement,
				PsiElement versionReferenceElement) {
			this(artifactId, propertyName, versionSource, declarationSource, declarationElement,
					versionReferenceElement, null);
		}

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
	 * Gradle plugin declaration such as {@code id 'p' version 'v'} or
	 * {@code id("p") version "v"}. The plugin id is used as both group and artifact
	 * id, matching the convention shared with the rest of the Gradle support code.
	 */
	record PluginVersion(ArtifactId artifactId, VersionSource versionSource, PsiElement declarationElement,
			PsiElement versionLiteral, @Nullable ArtifactVersion version)
			implements GradleVersionSite, DependencySite, VersionAware {

		PluginVersion(ArtifactId artifactId, VersionSource versionSource, PsiElement declarationElement,
				PsiElement versionLiteral) {
			this(artifactId, versionSource, declarationElement, versionLiteral, null);
		}

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
			return DeclarationSource.plugin();
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
