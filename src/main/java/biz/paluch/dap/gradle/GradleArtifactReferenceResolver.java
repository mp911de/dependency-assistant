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
 * <p>Supports version lookups in Groovy and Kotlin Gradle scripts,
 * {@code gradle.properties}, and TOML version catalogs. Version catalog
 * accessors are resolved back to the catalog entry that owns the version.
 *
 * @author Mark Paluch
 */
class GradleArtifactReferenceResolver implements ArtifactReferenceResolver {

	private final boolean candidate;

	private final PsiFile file;

	private final GradlePropertyResolver propertyResolver;

	private final VersionCatalogRegistry registry;

	private final ArtifactReferenceLocator<GroovyPsiElement> groovyLocator;

	private final @Nullable ArtifactReferenceLocator<KtElement> kotlinLocator;

	private final ArtifactReferenceLocator<TomlLiteral> tomlLocator;

	private final @Nullable ProjectState projectState;

	/**
	 * Create a resolver for the given project state and file.
	 *
	 * @param projectState the project dependency state, or {@literal null} if it is
	 * unavailable.
	 * @param file the Gradle-related file to inspect.
	 */
	GradleArtifactReferenceResolver(@Nullable ProjectState projectState, PsiFile file) {

		this.file = file;
		this.candidate = GradleUtils.isGradleFile(file);
		this.projectState = projectState;
		this.propertyResolver = GradlePropertyResolver.create(file);
		this.registry = VersionCatalogRegistry.from(file);
		this.groovyLocator = new GroovyArtifactReferenceLocator(this.propertyResolver, this.registry,
				projectState);

		if (GradleUtils.KOTLIN_AVAILABLE) {
			this.kotlinLocator = new KotlinArtifactReferenceLocator(this.propertyResolver, this.registry,
					projectState);
		} else {
			this.kotlinLocator = null;
		}
		this.tomlLocator = new TomlArtifactReferenceLocator(projectState);
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {
		return candidate ? findArtifactReference(element) : ArtifactReference.unresolved();
	}

	@Override
	public DependencySearchResults search(DependencySiteQuery query) {

		if (!candidate) {
			return DependencySearchResults.empty();
		}

		List<DependencySiteSearchHit> hits = doSearch(query);
		List<DependencySiteSearchHit> deduplicated = new ArrayList<>(hits.size());
		Set<PsiElement> seen = new HashSet<>();
		for (DependencySiteSearchHit hit : hits) {
			if (seen.add(hit.element())) {
				deduplicated.add(hit);
			}
		}

		return DependencySearchResults.of(deduplicated);
	}

	private List<DependencySiteSearchHit> doSearch(DependencySiteQuery query) {

		if (GradleUtils.isVersionCatalog(file)) {
			List<DependencySiteSearchHit> hits = new ArrayList<>(
					propertyDefinitionHits(query.versionProperties(), TomlParser.parseTomlVersions(file)::get));
			hits.addAll(toDeclarationHits(TomlParser.parseVersionCatalog(file), query));
			return hits;
		}

		if (GradleUtils.isGradlePropertiesFile(file)) {
			return propertyDefinitionHits(query.versionProperties(),
					GradlePropertiesParser.parseGradleProperties(file)::get);
		}

		if (GradleUtils.KOTLIN_AVAILABLE && GradleUtils.isKotlinDsl(file) && kotlinLocator != null) {
			KotlinDslFileParser parser = new KotlinDslFileParser(file, propertyResolver, registry);
			List<DependencySiteSearchHit> hits = new ArrayList<>(toDeclarationHits(parser.parseDeclarations(), query));
			hits.addAll(propertyDefinitionHits(retainLocalNames(query, parser.getExtraPropertyNames()),
					parser::getPropertyValue));
			return hits;
		}

		if (GradleUtils.isGroovyDsl(file)) {
			GroovyDslFileParser parser = new GroovyDslFileParser(file, propertyResolver, registry);
			List<DependencySiteSearchHit> hits = new ArrayList<>(toDeclarationHits(parser.parseDeclarations(), query));
			hits.addAll(propertyDefinitionHits(retainLocalNames(query, parser.getDeclaredPropertyNames()),
					parser::getPropertyValue));
			return hits;
		}

		return List.of();
	}

	private static List<DependencySiteSearchHit> toDeclarationHits(List<ArtifactDeclaration> declarations,
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
				hits.add(DependencySiteSearchHit.declaration(versionLiteral, declaration));

				if (!PsiTreeUtil.isAncestor(declarationElement, versionLiteral, false)) {
					hits.add(DependencySiteSearchHit.usage(declarationElement, declaration));
				}
			} else if (versionSource.isProperty() || versionSource instanceof VersionSource.VersionCatalog) {
				hits.add(DependencySiteSearchHit.usage(declarationElement, declaration));
			}
		}

		return hits;
	}

	private static boolean matches(ArtifactDeclaration declaration, DependencySiteQuery query) {

		if (query.artifacts().contains(declaration.getArtifactId())) {
			return true;
		}

		return declaration.getVersionSource() instanceof VersionSource.VersionProperty property
				&& query.matches(property);
	}

	private static List<DependencySiteSearchHit> propertyDefinitionHits(Collection<String> names,
			Function<String, biz.paluch.dap.support.@Nullable Property> lookup) {

		List<DependencySiteSearchHit> hits = new ArrayList<>();
		for (String name : names) {

			biz.paluch.dap.support.Property property = lookup.apply(name);
			if (property != null) {
				hits.add(DependencySiteSearchHit.declaration(property.getValueLiteral(), property.getValue()));
			}
		}

		return hits;
	}

	private static Set<String> retainLocalNames(DependencySiteQuery query, Set<String> declaredNames) {

		Set<String> names = new HashSet<>(query.versionProperties());
		names.retainAll(declaredNames);
		return names;
	}

	/**
	 * Find the Gradle artifact reference represented by the given element.
	 * @param element the PSI element under inspection.
	 */
	private ArtifactReference findArtifactReference(PsiElement element) {

		PsiFile file = element.getContainingFile();
		if (GradleUtils.isVersionCatalog(file) && TomlParser.isVersionElement(element)
				&& element instanceof TomlLiteral literal) {
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

		if (GradleUtils.isGroovyDsl(file) && element instanceof GroovyPsiElement groovyElement
				&& GroovyArtifactReferenceLocator.isVersionElement(groovyElement)) {
			return groovyLocator.locate(groovyElement);
		}

		if (GradleUtils.KOTLIN_AVAILABLE && kotlinLocator != null && element instanceof KtElement ktElement) {
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

		if (StringUtils.isEmpty(property.getUnescapedKey()) || StringUtils.isEmpty(property.getName())
				|| StringUtils.isEmpty(property.getUnescapedValue())) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReferenceUtils.resolve(property.getName(), property.getUnescapedValue(), property, property,
				projectState);
	}

}
