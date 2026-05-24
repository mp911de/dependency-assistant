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

import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.ArtifactReferenceResolver;
import biz.paluch.dap.support.LookupContext;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.toml.lang.psi.TomlLiteral;

/**
 * Gradle implementation of {@link ArtifactReferenceResolver}.
 *
 * <p>Supports version lookups in Groovy and Kotlin build scripts,
 * {@code gradle.properties}, and {@code libs.versions.toml}. Version catalog
 * accessors are resolved back to the catalog entry that owns the version.
 *
 * @author Mark Paluch
 */
class GradleArtifactReferenceResolver implements ArtifactReferenceResolver {

	private final boolean candidate;

	private final TomlArtifactResolver tomlResolver;

	private final GradlePropertyResolver propertyResolver;

	private final VersionCatalogRegistry registry;

	private final GroovyVersionSiteLocator groovySiteLocator;

	private final KotlinVersionSiteLocator kotlinSiteLocator;

	private final TomlVersionSiteLocator tomlSiteLocator;

	private final GradleVersionSiteResolver lookupSiteResolver;

	/**
	 * Create a resolver for the given context and build context.
	 *
	 * @param context the shared per-file resolution environment.
	 * @param file the Gradle-related file to inspect.
	 */
	GradleArtifactReferenceResolver(LookupContext context, PsiFile file) {

		Project project = context.project();

		this.candidate = GradleUtils.isGradleFile(file);
		this.propertyResolver = GradlePropertyResolver.create(file);
		this.registry = VersionCatalogRegistry.from(file);
		this.tomlResolver = new TomlArtifactResolver(project, file, this.registry);
		this.groovySiteLocator = new GroovyVersionSiteLocator(this.propertyResolver, this.registry);
		this.kotlinSiteLocator = new KotlinVersionSiteLocator(this.propertyResolver, this.registry);
		this.tomlSiteLocator = new TomlVersionSiteLocator();
		this.lookupSiteResolver = new GradleVersionSiteResolver(this.propertyResolver, context.projectState(),
				this.tomlResolver);
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

		if (!candidate) {
			return ArtifactReference.unresolved();
		}

		GradleVersionSite versionSite = findVersionSite(element);
		return versionSite.isPresent() ? lookupSiteResolver.resolve(versionSite) : ArtifactReference.unresolved();
	}

	/**
	 * Find the Gradle version site represented by the given element.
	 * @param element the PSI element under inspection.
	 */
	public GradleVersionSite findVersionSite(PsiElement element) {

		PsiFile file = element.getContainingFile();
		if (GradleUtils.isVersionCatalog(file) && element instanceof TomlLiteral literal) {
			return tomlSiteLocator.locate(literal);
		}

		if (GradleUtils.isGradlePropertiesFile(file)) {
			if (GradlePropertiesParser.isPropertyValueElement(element)) {
				return locateGradlePropertySite(element);
			}
			if (element instanceof Property property) {
				return locateGradlePropertySite(property);
			}
			return GradleVersionSite.absent();
		}

		if (GradleUtils.isGroovyDsl(file) && element instanceof GroovyPsiElement groovyElement) {
			return groovySiteLocator.locate(groovyElement);
		}

		if (GradleUtils.KOTLIN_AVAILABLE && element instanceof KtElement ktElement) {
			return kotlinSiteLocator.locate(ktElement);
		}

		return GradleVersionSite.absent();
	}

	private GradleVersionSite locateGradlePropertySite(PsiElement element) {

		Property property = GradlePropertiesParser.getProperty(element);
		if (property == null || StringUtils.isEmpty(property.getUnescapedKey())) {
			return GradleVersionSite.absent();
		}

		return locateGradlePropertySite(property);
	}

	private GradleVersionSite locateGradlePropertySite(Property property) {

		if (StringUtils.isEmpty(property.getUnescapedKey())) {
			return GradleVersionSite.absent();
		}

		if (StringUtils.hasText(property.getName())) {
			return new GradleVersionSite.BackingProperty(property.getName(), property.getUnescapedValue(), property,
					property);
		}

		return GradleVersionSite.absent();
	}

}
