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

import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Gradle-facing dependency descriptor used between parser extraction and the
 * common dependency-site model.
 *
 * <p>This contract intentionally models less than Gradle's runtime dependency
 * API. It captures the artifact identity and the origin of the version value so
 * Groovy DSL, Kotlin DSL, version catalogs, dependency constraints, and plugin
 * declarations can feed the same upgrade lookup infrastructure.
 *
 * <p>The artifact identity is already normalized when exposed through
 * {@link #getId()}: module dependencies use their group/name coordinates, while
 * plugin declarations can use {@link GradlePluginId}. Version information is
 * kept as a {@link VersionSource} so downstream code can distinguish inline
 * literals, properties, version-catalog entries, and declarations without
 * re-parsing PSI.
 *
 * <p>Implementations represent the way the version is owned by the Gradle
 * declaration: a dependency may have no local version, a literal version at the
 * dependency site, or a property-backed version whose editable source lives
 * elsewhere.
 *
 * @author Mark Paluch
 * @see GradleArtifactId
 * @see GradlePluginId
 */
interface GradleDependency {

	/**
	 * Return the dependency identity used by release lookup, caching, and UI
	 * grouping.
	 * <p>Version information is deliberately excluded from this identity and is
	 * represented through {@link #getVersionSource()}.
	 */
	ArtifactId getId();

	/**
	 * Return the source that owns the version value for this declaration.
	 * <p>The source determines both how the current version is resolved and where
	 * an update should be applied.
	 */
	VersionSource getVersionSource();

	/**
	 * Return the declaration source for this dependency.
	 */
	DeclarationSource getDeclarationSource();

	/**
	 * Parse compact Gradle module notation into a dependency descriptor.
	 * <p>This convenience variant is suitable for parser paths that have no
	 * property context available. Property expressions in the version position are
	 * still preserved as property-backed version sources.
	 *
	 * @param gav compact Gradle module notation.
	 * @param declarationSource the structural origin of the declaration to attach
	 * to the resulting descriptor.
	 * @return the dependency descriptor, or {@literal null} if the text is not a
	 * compact module coordinate candidate.
	 */
	static @Nullable GradleDependency parse(String gav, DeclarationSource declarationSource) {
		return parse(gav, declarationSource, PropertyResolver.empty());
	}

	/**
	 * Parse compact Gradle module notation into a dependency descriptor using the
	 * given property context.
	 * <p>Coordinate expressions are resolved before the dependency crosses into the
	 * common artifact model. Property-backed versions remain represented as a
	 * {@link VersionSource} rather than being collapsed into an inline declaration
	 * so the update path can still target the property owner.
	 *
	 * @param gav compact Gradle module notation.
	 * @param declarationSource the structural origin of the declaration to attach
	 * to the resulting descriptor.
	 * @param propertyResolver property resolver to use for coordinate and version
	 * expressions.
	 * @return the dependency descriptor, or {@literal null} if the text is not a
	 * compact module coordinate candidate.
	 */
	static @Nullable GradleDependency parse(String gav, DeclarationSource declarationSource,
			PropertyResolver propertyResolver) {

		if (!GradleArtifactId.isValid(gav)) {
			return null;
		}

		return of(GradleArtifactId.from(gav), declarationSource, propertyResolver);
	}

	/**
	 * Adapt Gradle module coordinates into the dependency descriptor model.
	 * <p>The coordinate's version segment determines which descriptor variant is
	 * created: no version keeps the dependency as a reference, a property
	 * expression keeps the version externally owned, and a literal version stays
	 * with the declaration.
	 * @param gav Gradle module coordinates extracted from a declaration.
	 * @param declarationSource the structural origin of the declaration to attach
	 * to the resulting descriptor.
	 * @param resolver property resolver to use for coordinate and version
	 * expressions.
	 * @return the dependency descriptor.
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

	/**
	 * Create a dependency descriptor when artifact identity and version expression
	 * were parsed separately.
	 * <p>This factory is used by declaration forms such as plugin DSL entries,
	 * named dependency declarations, and version catalogs where the version does
	 * not originate from compact {@code group:name:version} notation.
	 *
	 * @param artifactId artifact identifier.
	 * @param versionExpression version expression associated with the declaration.
	 * @param declarationSource the structural origin of the declaration to attach
	 * to the resulting descriptor.
	 * @return the dependency descriptor.
	 */
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
	 * Create a dependency descriptor from separately parsed named declaration
	 * fields.
	 * @param group artifact group.
	 * @param artifact artifact name.
	 * @param versionProperty optional version property name.
	 * @param version optional resolved or literal version.
	 * @param declarationSource structural origin of the declaration.
	 * @param resolver property resolver used for coordinate placeholders.
	 * @return the dependency descriptor, or {@literal null} when the declaration is
	 * incomplete.
	 */
	static @Nullable GradleDependency fromNamed(@Nullable String group, @Nullable String artifact,
			@Nullable String versionProperty, @Nullable String version, DeclarationSource declarationSource,
			PropertyResolver resolver) {

		if (!StringUtils.hasText(group) || !StringUtils.hasText(artifact)
				|| !StringUtils.hasText(versionProperty) && !StringUtils.hasText(version)) {
			return null;
		}

		ArtifactId artifactId = GradleArtifactId.from(ArtifactId.of(group, artifact), "").resolve(resolver);
		Expression expression = StringUtils.hasText(versionProperty) ? Expression.property(versionProperty)
				: Expression.from(version);
		return of(artifactId, expression, declarationSource);
	}

	/**
	 * Return a dependency descriptor for the same artifact with a version obtained
	 * from a separately parsed declaration component.
	 * <p>Use this when a parser first discovers the module identity and later
	 * associates a version block, catalog entry, or named version argument with
	 * that identity.
	 *
	 * @param expression the version expression to apply.
	 * @return the dependency descriptor with the updated version source.
	 */
	default GradleDependency withVersion(Expression expression) {
		return of(getId(), expression, getDeclarationSource());
	}

	/**
	 * Adapt this descriptor to a PSI-backed dependency site using the declaration
	 * element as its primary anchor.
	 * @param declaration PSI element representing the dependency declaration.
	 * @return the dependency site.
	 */
	default DependencySite toDependencySite(PsiElement declaration) {
		return DependencySite.of(getId(), getVersionSource(), getDeclarationSource(), declaration);
	}

	/**
	 * Adapt this descriptor to a PSI-backed dependency site, offering a separate
	 * version anchor.
	 * <p>The default implementation ignores {@code version} and anchors the site on
	 * {@code declaration} only. Implementations whose editable version literal is a
	 * distinct PSI element (for example {@link SimpleDependency}) override this to
	 * anchor the version separately.
	 * @param declaration PSI element representing the dependency declaration.
	 * @param version PSI element representing the editable version location.
	 * @return the dependency site.
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
	 * Dependency declaration with version text owned by the declaration site.
	 * <p>The version is parsed opportunistically into an {@link ArtifactVersion}
	 * when the descriptor is converted to a {@link DependencySite}; rich Gradle
	 * version syntax that cannot be represented as a concrete artifact version is
	 * still retained through the {@link VersionSource}.
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

			DependencySite dependencySite = DependencySite.of(getId(), getVersionSource(), getDeclarationSource(),
					declaration);
			Optional<ArtifactVersion> concreteVersion = GradleRichVersion.parse(version());
			if (concreteVersion.isEmpty()) {
				return dependencySite;
			}
			return dependencySite.withVersion(concreteVersion.get(), version);
		}

	}

	/**
	 * Dependency declaration whose editable version value is owned by a named
	 * property-like source.
	 * <p>The dependency keeps both the artifact identity and the property name so
	 * lookup code can resolve the current value and anchor updates at the property
	 * declaration instead of the dependency usage.
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
