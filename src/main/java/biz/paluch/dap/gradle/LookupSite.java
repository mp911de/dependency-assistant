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
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.support.VersionedDependencySite;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Descriptor for a semantic dependency lookup site in a Gradle build.
 *
 * <p>{@code LookupSite} decouples PSI traversal from artifact resolution.
 * Locator implementations identify the most meaningful source location for a
 * dependency declaration, property indirection, or version catalog reference
 * and expose it in one of the supported forms. {@link GradleLookupSiteResolver}
 * can then resolve that semantic description without depending on the traversal
 * logic that produced it.
 *
 * <p>Instances are typically created through the static factory methods on this
 * interface or adapted from {@link DependencySite} handles obtained from parser
 * infrastructure. Callers are expected to treat lookup sites as immutable
 * snapshots of the relevant PSI state and pass them directly to
 * {@link GradleLookupSiteResolver#resolve(LookupSite)}.
 *
 * <p>The {@link #declarationElement()} always identifies the owning declaration
 * or reference site.
 *
 * @author Mark Paluch
 * @see KotlinLookupSiteLocator
 * @see GroovyLookupSiteLocator
 * @see TomlLookupSiteLocator
 * @see GradleLookupSiteResolver
 */
interface LookupSite {

	/**
	 * Return whether this instance represents an actual lookup site.
	 *
	 * <p>A present lookup site carries sufficient semantic context to be handed to
	 * {@link GradleLookupSiteResolver} for further resolution. This flag applies to
	 * the lookup site as a whole and does not imply that artifact resolution will
	 * necessarily succeed.
	 *
	 * @return {@code true} if this instance represents an actual lookup site.
	 * @see #isAbsent()
	 */
	boolean isPresent();

	/**
	 * Return whether this instance represents an absent lookup site (i.e.
	 * {@code isPresent() == false}).
	 *
	 * @return {@code true} if this instance represents an absent lookup site;
	 * {@code false} otherwise.
	 * @see #isPresent()
	 */
	default boolean isAbsent() {
		return !isPresent();
	}

	/**
	 * Return the PSI element that owns this lookup site.
	 * <p>The declaration element typically represents the dependency declaration,
	 * property declaration, or catalog reference that should be used as the primary
	 * navigation and resolution anchor.
	 *
	 * @return the owning declaration element.
	 */
	PsiElement declarationElement();

	/**
	 * Return an absent lookup site.
	 *
	 * @return an absent lookup site.
	 */
	static LookupSite absent() {
		return AbsentLookupSite.ABSENT;
	}

	/**
	 * Create a {@link PropertyLookupSite} from a resolved {@link PropertyValue}.
	 *
	 * @param propertyValue the property value descriptor.
	 * @param declarationElement the PSI element owning the property declaration.
	 * @return the lookup site.
	 */
	static LookupSite ofProperty(PropertyValue propertyValue, PsiElement declarationElement) {
		return ofProperty(propertyValue.propertyKey(), propertyValue.propertyValue(), declarationElement,
				propertyValue.element());
	}

	/**
	 * Create a {@link PropertyLookupSite} for a version property declaration or
	 * property-backed version usage.
	 *
	 * @param propertyName the property name.
	 * @param version the current property value.
	 * @param declarationElement the PSI element owning the property declaration.
	 * @param versionElement the PSI element exposing the version literal.
	 * @return the lookup site.
	 */
	static LookupSite ofProperty(String propertyName, String version, PsiElement declarationElement,
			PsiElement versionElement) {

		Assert.hasText(propertyName, "Property name must not be empty");
		Assert.notNull(declarationElement, "Declaration element must not be null");
		Assert.notNull(versionElement, "Version element must not be null");

		return new PropertyLookupSite(propertyName, version, declarationElement, versionElement);
	}

	/**
	 * Create a {@link TomlCatalogLookupSite} for a version catalog alias reference.
	 *
	 * @param reference the catalog reference to resolve.
	 * @param declarationElement the PSI element containing the alias usage.
	 * @return the lookup site.
	 */
	static LookupSite ofTomlReference(TomlReference reference, PsiElement declarationElement) {
		return new TomlCatalogLookupSite(reference, declarationElement);
	}

	/**
	 * Adapt the given {@link DependencySite} to a {@link LookupSite}.
	 * <p>A {@code null} input results in {@link LookupSite#absent()}.
	 *
	 * @param dependencySite the dependency site to adapt.
	 * @return the adapted lookup site, or {@link LookupSite#absent()} if
	 * {@code dependencySite} is {@code null}.
	 */
	static LookupSite from(@Nullable DependencySite dependencySite) {

		if (dependencySite == null) {
			return LookupSite.absent();
		}

		if (dependencySite instanceof VersionedDependencySite versioned) {
			return new ResolvedSite(versioned);
		}

		return new ArtifactIdLookupSite(dependencySite.getArtifactId(), dependencySite.getVersionSource(),
				dependencySite.getDeclarationElement(), null);
	}

	/**
	 * Lookup site backed by a version catalog alias reference.
	 * <p>The version is obtained from the referenced TOML entry.
	 */
	record TomlCatalogLookupSite(TomlReference reference, PsiElement declarationElement) implements LookupSite {

		@Override
		public boolean isPresent() {
			return true;
		}

	}

	/**
	 * Lookup site backed by a version property declaration or usage.
	 *
	 * <p>Exposes the property name together with the current raw version text so
	 * that property-based version resolution can continue without re-reading the
	 * originating PSI node.
	 */
	record PropertyLookupSite(String propertyName, String version, PsiElement declarationElement,
			PsiElement versionElement) implements LookupSite {

		@Override
		public boolean isPresent() {
			return true;
		}

	}

	/**
	 * Lookup site backed by artifact coordinates and a {@link VersionSource}.
	 *
	 * <p>Used for parser-derived dependency descriptors that may not yet carry a
	 * resolved version value or a dedicated version PSI element.
	 */
	record ArtifactIdLookupSite(ArtifactId artifactId, VersionSource versionSource,
			PsiElement declarationElement, @Nullable PsiElement versionElement)
			implements LookupSite {

		@Override
		public boolean isPresent() {
			return true;
		}

	}

	/**
	 * Lookup site backed by an already resolved {@link VersionedDependencySite}.
	 *
	 * <p>Preserves the fully resolved version and PSI metadata from parser
	 * infrastructure while exposing it through the {@link LookupSite} contract.
	 */
	record ResolvedSite(VersionedDependencySite site)
			implements LookupSite {

		@Override
		public boolean isPresent() {
			return true;
		}

		@Override
		public PsiElement declarationElement() {
			return site.getDeclarationElement();
		}

	}

	/**
	 * Singleton marker for absent lookup sites.
	 */
	enum AbsentLookupSite implements LookupSite {

		ABSENT;


		@Override
		public boolean isPresent() {
			return false;
		}

		@Override
		public PsiElement declarationElement() {
			throw new IllegalStateException("Absent lookup site");
		}

	}

}
