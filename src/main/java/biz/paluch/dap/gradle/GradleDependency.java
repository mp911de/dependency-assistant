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

package biz.paluch.dap.gradle;

import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.HasPackageIdentity;
import biz.paluch.dap.artifact.HasPackageSystem;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Parsed Gradle dependency with version ownership retained for updates.
 * <p>Artifact identity excludes the version. {@link VersionSource}
 * distinguishes local values from properties and catalog entries whose editable
 * source lives elsewhere.
 *
 * @author Mark Paluch
 * @see GradleArtifactId
 * @see GradlePluginId
 */
interface GradleDependency extends HasPackageSystem, HasPackageIdentity {

	ArtifactId getId();

	@Override
	default PackageIdentity getPackageIdentity() {
		return PackageIdentity.of(getId(), getPackageSystem());
	}

	@Override
	default PackageSystem getPackageSystem() {
		return PackageSystem.MAVEN;
	}

	VersionSource getVersionSource();

	DeclarationSource getDeclarationSource();

	/**
	 * Parse compact module notation without a property context.
	 * @see #parse(String, DeclarationSource, PropertyResolver)
	 */
	static @Nullable GradleDependency parse(String gav, DeclarationSource declarationSource) {
		return parse(gav, declarationSource, PropertyResolver.empty());
	}

	/**
	 * Parse compact module notation using the given property context.
	 * <p>Property-backed versions retain their source so updates target the owner.
	 * @return the dependency, or {@literal null} for an invalid coordinate
	 * candidate.
	 */
	static @Nullable GradleDependency parse(String gav, DeclarationSource declarationSource,
			PropertyResolver propertyResolver) {

		if (!GradleArtifactId.isValid(gav)) {
			return null;
		}

		return of(GradleArtifactId.from(gav), declarationSource, propertyResolver);
	}

	/**
	 * Resolve coordinates while preserving version ownership.
	 */
	static GradleDependency of(GradleArtifactId gav, DeclarationSource declarationSource, PropertyResolver resolver) {

		if (StringUtils.hasText(gav.version())) {

			Expression expression = Expression.from(gav.version());
			if (expression.isProperty()) {
				ArtifactId id = gav.resolve(resolver);
				return new PropertyManagedDependency(id, expression.getPropertyName(),
						expression.asVersionSource(), declarationSource);
			}

			return new SimpleDependency(gav.resolveAll(resolver), declarationSource);
		}

		return new DependencyReference(gav.resolve(resolver), declarationSource);
	}

	static GradleDependency of(ArtifactId artifactId, Expression versionExpression,
			DeclarationSource declarationSource) {

		if (versionExpression.isProperty()) {
			return new PropertyManagedDependency(artifactId, versionExpression.getPropertyName(),
					versionExpression.asVersionSource(), declarationSource);
		}
		return new SimpleDependency(artifactId, versionExpression.toString(),
				versionExpression.asVersionSource(), declarationSource);
	}

	/**
	 * Create a dependency from named fields. A version property takes precedence
	 * over the supplied version.
	 * @return {@literal null} if identity or version information is missing.
	 */
	static @Nullable GradleDependency fromNamed(@Nullable String group, @Nullable String artifact,
			@Nullable String versionProperty, @Nullable String version, DeclarationSource declarationSource,
			PropertyResolver resolver) {

		if (!StringUtils.hasText(versionProperty) && !StringUtils.hasText(version)) {
			return null;
		}

		Expression expression = StringUtils.hasText(versionProperty) ? Expression.property(versionProperty)
				: Expression.from(version);
		return fromNamed(group, artifact, expression, declarationSource, resolver);
	}

	/**
	 * Create a dependency from named identity fields and a version expression.
	 * @return {@literal null} if group or artifact is missing.
	 */
	static @Nullable GradleDependency fromNamed(@Nullable String group, @Nullable String artifact,
			Expression version, DeclarationSource declarationSource, PropertyResolver resolver) {

		if (!StringUtils.hasText(group) || !StringUtils.hasText(artifact)) {
			return null;
		}

		ArtifactId artifactId = GradleArtifactId.from(ArtifactId.of(group, artifact), "").resolve(resolver);
		return of(artifactId, version, declarationSource);
	}

	default GradleDependency withVersion(Expression expression) {
		return of(getId(), expression, getDeclarationSource());
	}

	default DependencySite toDependencySite(PsiElement declaration) {
		return DependencySite.of(this, getVersionSource(), getDeclarationSource(), declaration);
	}

	/**
	 * Create a dependency site with a separate version anchor.
	 * <p>The default ignores the version anchor. Implementations with an editable
	 * local version override this behavior.
	 */
	default DependencySite toDependencySite(PsiElement declaration, PsiElement version) {
		return toDependencySite(declaration);
	}

	/**
	 * Dependency declaration whose artifact is known but whose version is not owned
	 * by the declaration site.
	 */
	record DependencyReference(ArtifactId id, DeclarationSource declarationSource) implements GradleDependency {

		@Override
		public ArtifactId getId() {
			return id();
		}

		@Override
		public VersionSource getVersionSource() {
			return VersionSource.none();
		}

		@Override
		public DeclarationSource getDeclarationSource() {
			return declarationSource();
		}

	}

	/**
	 * Dependency with locally owned version text. Rich version syntax is retained
	 * even when it cannot be represented as a concrete {@link ArtifactVersion}.
	 */
	record SimpleDependency(ArtifactId id, String version, VersionSource versionSource,
			DeclarationSource declarationSource) implements GradleDependency {

		SimpleDependency(GradleArtifactId gav, DeclarationSource declarationSource) {
			this(gav, gav.version(), VersionSource.declared(gav.version()), declarationSource);
		}

		@Override
		public ArtifactId getId() {
			return id();
		}

		@Override
		public VersionSource getVersionSource() {
			return versionSource();
		}

		@Override
		public DeclarationSource getDeclarationSource() {
			return declarationSource();
		}

		@Override
		public DependencySite toDependencySite(PsiElement declaration) {
			return toDependencySite(declaration, declaration);
		}

		@Override
		public DependencySite toDependencySite(PsiElement declaration, PsiElement version) {

			DependencySite dependencySite = DependencySite.of(this, getVersionSource(),
					getDeclarationSource(), declaration);
			Optional<ArtifactVersion> concreteVersion = GradleRichVersion.parse(version());
			if (concreteVersion.isEmpty()) {
				return dependencySite;
			}
			return dependencySite.withVersion(concreteVersion.get(), version);
		}

	}

	/**
	 * Dependency whose editable version is owned by a named property.
	 */
	record PropertyManagedDependency(ArtifactId id, String property,
			VersionSource versionSource, DeclarationSource declarationSource) implements GradleDependency {

		@Override
		public ArtifactId getId() {
			return id();
		}

		@Override
		public VersionSource getVersionSource() {
			return versionSource();
		}

		@Override
		public DeclarationSource getDeclarationSource() {
			return declarationSource();
		}

	}

}
