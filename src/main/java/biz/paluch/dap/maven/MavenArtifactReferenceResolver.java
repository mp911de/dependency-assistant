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

package biz.paluch.dap.maven;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.lookup.ArtifactReferenceResolver;
import biz.paluch.dap.lookup.DependencySearchResults;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.lookup.DependencySiteSearchHit;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jspecify.annotations.Nullable;

/**
 * Maven implementation of {@link ArtifactReferenceResolver}.
 *
 * <p>Resolves version and property tags in {@code pom.xml} files into an
 * {@link ArtifactReference}. Property-expression versions are resolved through
 * the PSI {@link PropertyResolver} and, as a fallback, the resolved Maven
 * project model so versions defined outside the inspected file are reported.
 *
 * @author Mark Paluch
 */
class MavenArtifactReferenceResolver implements ArtifactReferenceResolver {

	private final ProjectState projectState;

	private final MavenProjectContext buildContext;

	private final SmartPsiElementPointer<XmlFile> pom;

	private final boolean candidate;

	/**
	 * Create a resolver for a POM and its current imported Maven project context.
	 *
	 * @param project the IntelliJ project owning the POM.
	 * @param pomFile the POM to resolve references in.
	 * @param projectContext the Maven model and property context for the POM.
	 */
	MavenArtifactReferenceResolver(Project project, XmlFile pomFile,
			MavenProjectContext projectContext) {
		StateService service = StateService.getInstance(project);
		this.projectState = service.getProjectState(projectContext.getProjectId());
		this.buildContext = projectContext;
		this.pom = SmartPointerManager.createPointer(pomFile);
		this.candidate = MavenUtils.isMavenPomFile(pomFile);
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

		if (isResolvableElement(element) && canResolve()) {

			if (XmlUtil.findVersionTag(element) instanceof XmlTag versionTag) {
				return resolveVersionTag(versionTag);
			}

			if (XmlUtil.findPropertyTag(element) instanceof XmlTag propertyTag) {
				return resolveProperty(propertyTag);
			}
		}

		return ArtifactReference.unresolved();
	}

	@Override
	public DependencySearchResults search(DependencySiteQuery query) {

		XmlFile pomFile = getPom();
		if (!candidate || pomFile == null) {
			return DependencySearchResults.empty();
		}

		List<DependencySiteSearchHit> hits = new ArrayList<>(
				findPropertyDefinitions(pomFile, query.versionProperties()));
		hits.addAll(findVersionSites(pomFile, query));

		// A property-backed artifact hit and a queried property definition can name
		// the same tag; keep the first hit per element.
		List<DependencySiteSearchHit> deduplicated = new ArrayList<>(hits.size());
		Set<PsiElement> seen = new HashSet<>();
		for (DependencySiteSearchHit hit : hits) {
			if (seen.add(hit.element())) {
				deduplicated.add(hit);
			}
		}
		return DependencySearchResults.of(deduplicated);
	}

	/**
	 * Collect every root and profile {@code <properties>} entry whose name is part
	 * of the query, as a version-property definition. A property redefined in
	 * several sections yields one hit per definition.
	 */
	private static List<DependencySiteSearchHit> findPropertyDefinitions(XmlFile pomFile, Set<String> properties) {

		if (properties.isEmpty()) {
			return List.of();
		}

		List<DependencySiteSearchHit> hits = new ArrayList<>();
		MavenPomSupport.doWithRoot(pomFile, root -> {
			MavenPomSupport.PomTag pomTag = MavenPomSupport.PomTag.of(root);
			pomTag.subtags(MavenPomSupport.PROPERTIES)
					.forEach(section -> collectPropertyDefinitions(section, properties, hits));
			MavenPomSupport.doWithProfiles(pomTag, profile -> profile.subtags(MavenPomSupport.PROPERTIES)
					.forEach(section -> collectPropertyDefinitions(section, properties, hits)));
		});

		return hits;
	}

	private static void collectPropertyDefinitions(MavenPomSupport.PomTag section, Set<String> properties,
			List<DependencySiteSearchHit> hits) {

		for (XmlTag property : section.getTag().getSubTags()) {
			if (properties.contains(property.getLocalName())) {
				hits.add(DependencySiteSearchHit.declaration(property, property.getValue().getTrimmedText().trim()));
			}
		}
	}

	/**
	 * Collect every dependency or plugin declaration that contributes to the query.
	 * A property-backed declaration is a {@code ${property}} usage whether it is
	 * matched by artifact or by property name; its in-file property definition is
	 * reported for artifact matches as well. An inline version is a definition.
	 */
	private List<DependencySiteSearchHit> findVersionSites(XmlFile pomFile, DependencySiteQuery query) {

		List<DependencySiteSearchHit> hits = new ArrayList<>();
		MavenParser parser = new MavenParser(buildContext.getPomProperties());
		for (ArtifactDeclaration declaration : parser.parsePomFile(pomFile)) {
			ProgressManager.checkCanceled();

			boolean artifactMatch = query.artifacts().contains(declaration.getArtifactId());
			VersionSource versionSource = declaration.getVersionSource();
			if (versionSource instanceof VersionSource.VersionProperty property) {
				if (artifactMatch || query.matches(property)) {
					hits.add(DependencySiteSearchHit.usage(declaration.getDeclarationElement(), declaration));
				}
				if (artifactMatch && declaration.getVersionLiteral() != null
						&& declaration.isVersionDefinedInSameFile()) {
					hits.add(DependencySiteSearchHit.declaration(declaration.getRequiredVersionLiteral(),
							declaration));
				}
			} else if (artifactMatch && declaration.getVersionLiteral() != null) {
				hits.add(DependencySiteSearchHit.declaration(declaration.getRequiredVersionLiteral(), declaration));
			}
		}

		return hits;
	}

	private boolean canResolve() {
		return candidate && getPom() != null
				&& buildContext.isAvailable();
	}

	private @Nullable XmlFile getPom() {
		return pom.getElement();
	}

	/**
	 * Resolution is anchored to the {@link XmlText} value of a version or property
	 * tag. Line markers and highlighting fire on every element of a tag (the angle
	 * brackets, the tag name, the value text, and the surrounding text node).
	 * Pinning to the single text node keeps the gutter from duplicating across
	 * them. Completion and documentation resolve against this same text node.
	 */
	private boolean isResolvableElement(PsiElement element) {

		if (!element.isValid()) {
			return false;
		}

		return (element instanceof XmlText || element instanceof XmlTag tag);
	}

	private ArtifactReference resolveVersionTag(XmlTag versionTag) {

		XmlTag parentTag = versionTag.getParentTag();
		XmlFile pomFile = getPom();
		if (parentTag == null || pomFile == null) {
			return ArtifactReference.unresolved();
		}
		MavenParser parser = new MavenParser(buildContext.getPomProperties());
		ArtifactDeclaration artifactDeclaration = parser.parseDeclaration(parentTag);
		return artifactDeclaration != null ? ArtifactReference.from(artifactDeclaration)
				: ArtifactReference.unresolved();
	}

	@Nullable
	private ResolvedProperty resolveProperty(Expression expression) {

		XmlFile pomFile = getPom();
		if (pomFile == null) {
			return null;
		}
		PropertyResolver propertyResolver = buildContext.getPomProperties();
		Property propertyValue = null;
		Set<String> visited = new HashSet<>();
		while (expression.isProperty() && visited.add(expression.getPropertyName())) {

			String propertyName = expression.getPropertyName();
			propertyValue = propertyResolver.getPropertyValue(propertyName);
			if (propertyValue != null) {
				expression = Expression.from(propertyValue.getValue());
			} else {
				return null;
			}
		}

		if (propertyValue == null) {
			return null;
		}

		return new ResolvedProperty(propertyValue.getValue(), propertyValue);
	}

	private ArtifactReference resolveProperty(XmlTag propertyTag) {

		VersionProperty property = projectState.findProperty(propertyTag.getLocalName());
		if (property == null) {
			return ArtifactReference.unresolved();
		}

		ArtifactVersion currentVersion = getCurrentVersion(property, propertyTag);

		String tagName = propertyTag.getLocalName();
		ResolvedProperty resolvedProperty = resolveProperty(Expression.property(tagName));
		CachedArtifact firstArtifact = property.artifacts().getFirst();

		return ArtifactReference.from(it -> {
			it.artifact(firstArtifact.toArtifactId())
					.packageSystem(PackageSystem.MAVEN)
					.declarationElement(propertyTag)
					.versionSource(VersionSource.property(tagName))
					.declarationSource(DeclarationSource.dependency());

			if (currentVersion != null) {
				it.version(currentVersion);
			}

			if (resolvedProperty != null) {
				it.versionLiteral(resolvedProperty.propertyValue().getValueLiteral());
			} else {
				it.versionLiteral(propertyTag);
			}
		});
	}

	private @Nullable ArtifactVersion getCurrentVersion(VersionProperty property, XmlTag propertyTag) {

		Expression expression = Expression.from(propertyTag.getValue().getText().trim());
		if (expression.isProperty()) {
			return null;
		}

		if (expression.toString().contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) {
			return getCurrentVersion(property);
		}

		return ArtifactVersion.from(expression.toString()).orElse(null);
	}

	private @Nullable ArtifactVersion getCurrentVersion(VersionProperty property) {

		if (property.artifacts().isEmpty()) {
			return null;
		}

		ArtifactId artifactId = property.artifacts().getFirst().toArtifactId();
		Dependency dependency = projectState.findDependency(artifactId);
		return dependency != null ? dependency.getCurrentVersion() : null;
	}

	private record ResolvedProperty(String value, Property propertyValue) {
	}

}
