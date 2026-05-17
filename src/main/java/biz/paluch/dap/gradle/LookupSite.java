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
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.support.VersionedDependencySite;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Descriptor for a semantic dependency lookup site in a Gradle build.
 *
 * <p>
 * Locator implementations create these descriptors from PSI. The
 * {@link GradleLookupSiteResolver} then resolves them to artifact metadata. The
 * {@link #declarationElement()} identifies the owning declaration or reference
 * site.
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
	 * @see #isAbsent()
	 */
	boolean isPresent();

	/**
	 * Return whether this instance represents an absent lookup site.
	 * @see #isPresent()
	 */
	default boolean isAbsent() {
		return !isPresent();
	}

	/**
	 * Return the PSI element that owns this lookup site.
	 */
	PsiElement declarationElement();

	/**
	 * Return an absent lookup site.
	 */
	static LookupSite absent() {
		return AbsentLookupSite.ABSENT;
	}

	/**
	 * Create a {@link PropertyLookupSite} from a resolved {@link PropertyValue}.
	 * @param property the property value descriptor.
	 * @param declarationElement the PSI element owning the property declaration.
	 * @return the lookup site.
	 */
	static LookupSite ofProperty(Property property, PsiElement declarationElement) {
		return ofProperty(property.getKey(), property.getValue(), declarationElement,
				property.getValueLiteral());
	}

	/**
	 * Create a {@link PropertyLookupSite} for a version property declaration or
	 * property-backed version usage.
	 * @param propertyName the property name.
	 * @param version the current property value.
	 * @param declarationElement the PSI element owning the property declaration.
	 * @param versionElement the PSI element exposing the version literal.
	 * @return the lookup site.
	 */
	static LookupSite ofProperty(String propertyName, String version, PsiElement declarationElement,
			PsiElement versionElement) {

		Assert.hasText(propertyName, "VersionProperty name must not be empty");
		Assert.notNull(declarationElement, "Declaration element must not be null");
		Assert.notNull(versionElement, "Version element must not be null");

		return new PropertyLookupSite(propertyName, version, declarationElement, versionElement);
	}

	/**
	 * Create a {@link TomlCatalogLookupSite} for a version catalog alias reference.
	 * @param reference the catalog reference to resolve.
	 * @param declarationElement the PSI element containing the alias usage.
	 * @return the lookup site.
	 */
	static LookupSite ofTomlReference(TomlReference reference, PsiElement declarationElement) {
		return new TomlCatalogLookupSite(reference, declarationElement);
	}

	/**
	 * Adapt the given {@link DependencySite} to a {@link LookupSite}.
	 * <p>
	 * A {@literal null} input results in {@link LookupSite#absent()}.
	 * @param dependencySite the dependency site to adapt.
	 * @return the adapted lookup site, or {@link LookupSite#absent()} if
	 * {@code dependencySite} is {@literal null}.
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
	 * <p>
	 * Exposes the property name together with the current raw version text so
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
	 * <p>
	 * Used for parser-derived dependency descriptors that may not yet carry a
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
	 * <p>
	 * Preserves the fully resolved version and PSI metadata from parser
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
