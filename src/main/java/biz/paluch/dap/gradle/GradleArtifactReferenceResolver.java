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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.lookup.ArtifactReferenceResolver;
import biz.paluch.dap.lookup.DependencySearchResults;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.lookup.DependencySiteSearchHit;
import biz.paluch.dap.lookup.LookupContext;
import biz.paluch.dap.lookup.SiteRole;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
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

	private final PsiFile file;

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

		this.file = file;
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

	@Override
	public DependencySearchResults search(DependencySiteQuery query) {

		if (!candidate) {
			return DependencySearchResults.empty();
		}

		GradleArtifactReferenceVisitor visitor = new GradleArtifactReferenceVisitor(this);
		file.accept(visitor);

		List<DependencySiteSearchHit> findings = new ArrayList<>();
		Set<PsiElement> seen = new HashSet<>();
		for (GradleArtifactReferenceVisitor.Match match : visitor.getMatches()) {

			ArtifactDeclaration declaration = match.reference().getDeclaration();
			if (!matches(declaration, query) || !seen.add(declaration.getDeclarationElement())) {
				continue;
			}

			SiteRole role = roleOf(match.element(), declaration);
			String label = labelOf(role, match.element(), declaration);
			findings.add(role == SiteRole.DECLARATION ? DependencySiteSearchHit.declaration(match.element(), label)
					: DependencySiteSearchHit.usage(match.element(), label));
		}

		return DependencySearchResults.of(findings);
	}

	/**
	 * Whether the resolved declaration contributes to the query, by artifact id or
	 * by a version property the query names.
	 */
	private static boolean matches(ArtifactDeclaration declaration, DependencySiteQuery query) {

		if (query.artifacts().contains(declaration.getArtifactId())) {
			return true;
		}

		return declaration.getVersionSource() instanceof VersionSource.VersionProperty property
				&& query.versionProperties().contains(property.getProperty());
	}

	/**
	 * A site is a definition when the resolving element is the version literal
	 * itself; otherwise it references the version from elsewhere.
	 */
	private static SiteRole roleOf(PsiElement element, ArtifactDeclaration declaration) {

		PsiElement versionLiteral = declaration.getVersionLiteral();
		boolean definition = versionLiteral != null && (versionLiteral == element
				|| PsiTreeUtil.isAncestor(versionLiteral, element, false)
				|| PsiTreeUtil.isAncestor(element, versionLiteral, false));
		return definition ? SiteRole.DECLARATION : SiteRole.VERSION_USAGE;
	}

	/**
	 * The concise display label: the version for a definition, the version property
	 * name for a usage.
	 */
	private static String labelOf(SiteRole role, PsiElement element, ArtifactDeclaration declaration) {

		if (role == SiteRole.DECLARATION) {
			return declaration.getVersion() != null ? declaration.getVersion().toString() : element.getText();
		}

		return declaration.getVersionSource() instanceof VersionSource.VersionProperty property ? property.getProperty()
				: element.getText();
	}

	/**
	 * Find the Gradle version site represented by the given element.
	 * @param element the PSI element under inspection.
	 */
	private GradleVersionSite findVersionSite(PsiElement element) {

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
