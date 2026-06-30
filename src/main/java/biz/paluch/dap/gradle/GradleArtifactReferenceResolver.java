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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.lookup.ArtifactReferenceResolver;
import biz.paluch.dap.lookup.DependencySearchResults;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.lookup.DependencySiteSearchHit;
import biz.paluch.dap.lookup.LookupContext;
import biz.paluch.dap.lookup.SiteRole;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jspecify.annotations.Nullable;
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

	private final GradlePropertyResolver propertyResolver;

	private final VersionCatalogRegistry registry;

	private final GroovyArtifactReferenceLocator groovyLocator;

	private final KotlinArtifactReferenceLocator kotlinLocator;

	private final TomlArtifactReferenceLocator tomlLocator;

	private final @Nullable ProjectState projectState;

	/**
	 * Create a resolver for the given context and build context.
	 *
	 * @param context the shared per-file resolution environment.
	 * @param file the Gradle-related file to inspect.
	 */
	GradleArtifactReferenceResolver(LookupContext context, PsiFile file) {

		this.file = file;
		this.candidate = GradleUtils.isGradleFile(file);
		this.projectState = context.projectState();
		this.propertyResolver = GradlePropertyResolver.create(file);
		this.registry = VersionCatalogRegistry.from(file);
		this.groovyLocator = new GroovyArtifactReferenceLocator(this.propertyResolver, this.registry,
				context.projectState());
		this.kotlinLocator = new KotlinArtifactReferenceLocator(this.propertyResolver, this.registry,
				context.projectState());
		this.tomlLocator = new TomlArtifactReferenceLocator(context.projectState());
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

		if (!candidate) {
			return ArtifactReference.unresolved();
		}

		return findArtifactReference(element);
	}

	@Override
	public DependencySearchResults search(DependencySiteQuery query) {

		if (!candidate) {
			return DependencySearchResults.empty();
		}

		List<DependencySiteSearchHit> hits = siteHits(query);
		List<DependencySiteSearchHit> deduplicated = new ArrayList<>(hits.size());
		Set<PsiElement> seen = new HashSet<>();
		for (DependencySiteSearchHit hit : hits) {
			if (seen.add(hit.element())) {
				deduplicated.add(hit);
			}
		}

		return DependencySearchResults.of(deduplicated);
	}

	private List<DependencySiteSearchHit> siteHits(DependencySiteQuery query) {

		if (GradleUtils.isVersionCatalog(file)) {
			List<DependencySiteSearchHit> hits = new ArrayList<>(
					propertyDefinitionHits(query.versionProperties(), TomlParser.parseTomlVersions(file)::get));
			hits.addAll(declarationHits(TomlParser.parseVersionCatalog(file), query));
			return hits;
		}

		if (GradleUtils.isGradlePropertiesFile(file)) {
			return propertyDefinitionHits(query.versionProperties(),
					GradlePropertiesParser.parseGradleProperties(file)::get);
		}

		if (GradleUtils.KOTLIN_AVAILABLE && GradleUtils.isKotlinDsl(file)) {
			KotlinDslFileParser parser = new KotlinDslFileParser(file, propertyResolver, registry);
			List<DependencySiteSearchHit> hits = new ArrayList<>(declarationHits(parser.parseDeclarations(), query));
			hits.addAll(propertyDefinitionHits(localNames(query, parser.getExtraPropertyNames()),
					parser::getPropertyValue));
			return hits;
		}

		if (GradleUtils.isGroovyDsl(file)) {
			GroovyDslFileParser parser = new GroovyDslFileParser(file, propertyResolver, registry);
			List<DependencySiteSearchHit> hits = new ArrayList<>(declarationHits(parser.parseDeclarations(), query));
			hits.addAll(propertyDefinitionHits(localNames(query, parser.getDeclaredPropertyNames()),
					parser::getPropertyValue));
			return hits;
		}

		return List.of();
	}

	/**
	 * Reconstruct declaration and version-usage hits from forward-parsed
	 * declarations.
	 */
	private static List<DependencySiteSearchHit> declarationHits(List<ArtifactDeclaration> declarations,
			DependencySiteQuery query) {

		List<DependencySiteSearchHit> hits = new ArrayList<>();
		for (ArtifactDeclaration declaration : declarations) {
			ProgressManager.checkCanceled();

			if (!matches(declaration, query)) {
				continue;
			}

			PsiElement declarationElement = declaration.getDeclarationElement();
			PsiElement versionLiteral = declaration.getVersionLiteral();
			VersionSource versionSource = declaration.getVersionSource();

			if (versionLiteral != null && declaration.isVersionDefinedInSameFile()) {
				hits.add(DependencySiteSearchHit.declaration(versionLiteral,
						labelOf(SiteRole.DECLARATION, versionLiteral, declaration)));

				if (!PsiTreeUtil.isAncestor(declarationElement, versionLiteral, false)) {
					hits.add(DependencySiteSearchHit.usage(declarationElement,
							labelOf(SiteRole.VERSION_USAGE, declarationElement, declaration)));
				}
			} else if (versionSource.isProperty() || versionSource instanceof VersionSource.VersionCatalog) {
				hits.add(DependencySiteSearchHit.usage(declarationElement,
						labelOf(SiteRole.VERSION_USAGE, declarationElement, declaration)));
			}
		}

		return hits;
	}

	/**
	 * Report each named version property defined in this file as a
	 * {@link SiteRole#DECLARATION} anchored on its value literal. Shared by the
	 * TOML {@code [versions]}, {@code gradle.properties}, and
	 * {@code ext}/{@code extra} definition sources.
	 */
	private static List<DependencySiteSearchHit> propertyDefinitionHits(Collection<String> names,
			Function<String, biz.paluch.dap.support.@Nullable Property> lookup) {

		List<DependencySiteSearchHit> hits = new ArrayList<>();
		for (String name : names) {
			ProgressManager.checkCanceled();

			biz.paluch.dap.support.Property property = lookup.apply(name);
			if (property != null) {
				hits.add(DependencySiteSearchHit.declaration(property.getValueLiteral(), property.getValue()));
			}
		}

		return hits;
	}

	/**
	 * The queried version properties that are declared locally in this file, so an
	 * {@code ext}/{@code extra} property consumed only in another module still
	 * surfaces as a definition.
	 */
	private static Set<String> localNames(DependencySiteQuery query, Set<String> declaredNames) {

		Set<String> names = new HashSet<>(query.versionProperties());
		names.retainAll(declaredNames);
		return names;
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
				&& query.matches(property);
	}

	/**
	 * The concise display label: the version for a definition, the version property
	 * name for a usage.
	 */
	private static String labelOf(SiteRole role, PsiElement element, ArtifactDeclaration declaration) {

		if (role == SiteRole.DECLARATION) {
			return declaration.isVersionDefined() ? declaration.getVersion().toString() : element.getText();
		}

		return declaration.getVersionSource() instanceof VersionSource.VersionProperty property ? property.getProperty()
				: element.getText();
	}

	/**
	 * Find the Gradle artifact reference represented by the given element.
	 * @param element the PSI element under inspection.
	 */
	private ArtifactReference findArtifactReference(PsiElement element) {

		PsiFile file = element.getContainingFile();
		if (GradleUtils.isVersionCatalog(file) && element instanceof TomlLiteral literal) {
			return tomlLocator.locate(literal);
		}

		if (GradleUtils.isGradlePropertiesFile(file)) {
			if (GradlePropertiesParser.isPropertyValueElement(element)) {
				return locateGradlePropertySite(element);
			}
			if (element instanceof Property property) {
				return locateGradlePropertySite(property);
			}
			return ArtifactReference.unresolved();
		}

		if (GradleUtils.isGroovyDsl(file) && element instanceof GroovyPsiElement groovyElement) {
			return groovyLocator.locate(groovyElement);
		}

		if (GradleUtils.KOTLIN_AVAILABLE && element instanceof KtElement ktElement) {
			return kotlinLocator.locate(ktElement);
		}

		return ArtifactReference.unresolved();
	}

	private ArtifactReference locateGradlePropertySite(PsiElement element) {

		Property property = GradlePropertiesParser.getProperty(element);
		if (property == null || StringUtils.isEmpty(property.getUnescapedKey())) {
			return ArtifactReference.unresolved();
		}

		return locateGradlePropertySite(property);
	}

	private ArtifactReference locateGradlePropertySite(Property property) {

		if (StringUtils.isEmpty(property.getUnescapedKey())) {
			return ArtifactReference.unresolved();
		}

		if (StringUtils.hasText(property.getName())) {
			return ArtifactReferenceUtils.resolve(property.getName(), property.getUnescapedValue(), property, property,
					projectState);
		}

		return ArtifactReference.unresolved();
	}

}
