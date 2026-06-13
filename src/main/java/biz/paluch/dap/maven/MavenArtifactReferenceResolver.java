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

package biz.paluch.dap.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.lookup.ArtifactReferenceResolver;
import biz.paluch.dap.lookup.DependencySearchResults;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.lookup.DependencySiteSearchHit;
import biz.paluch.dap.lookup.LookupContext;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
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

	private final LookupContext context;

	private final MavenProjectContext buildContext;

	private final @Nullable XmlFile pom;

	private final boolean candidate;

	private final PropertyResolver propertyResolver;

	/**
	 * Create a resolver for the given context and build context.
	 * @param context the shared per-file resolution environment.
	 * @param pomFile the {@code pom.xml} file to inspect.
	 * @param projectContext the Maven project context.
	 */
	MavenArtifactReferenceResolver(LookupContext context, PsiFile pomFile, MavenProjectContext projectContext) {

		this.context = context;
		this.buildContext = projectContext;
		this.pom = pomFile instanceof XmlFile xmlFile ? xmlFile : null;
		this.candidate = MavenUtils.isMavenPomFile(pomFile);
		this.propertyResolver = this.pom != null ? MavenPropertyResolver.create(projectContext, this.pom)
				: PropertyResolver.empty();
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

		if (isResolvableElement(element) && canResolve()) {

			if (XmlUtil.findVersionTag(element) instanceof XmlTag versionTag) {
				return resolveDirect(versionTag);
			}

			if (XmlUtil.findPropertyTag(element) instanceof XmlTag propertyTag) {
				return resolveProperty(propertyTag);
			}
		}

		return ArtifactReference.unresolved();
	}

	@Override
	public DependencySearchResults search(DependencySiteQuery query) {

		XmlFile pomFile = this.pom;
		if (!candidate || pomFile == null) {
			return DependencySearchResults.empty();
		}

		List<DependencySiteSearchHit> hits = new ArrayList<>(
				findPropertyDefinitions(pomFile, query.versionProperties()));
		hits.addAll(findVersionSites(pomFile, query));
		return DependencySearchResults.of(hits);
	}

	/**
	 * Collect every {@code <properties>} entry whose name is part of the query, as
	 * a version-property definition.
	 */
	private List<DependencySiteSearchHit> findPropertyDefinitions(XmlFile pomFile, Set<String> properties) {

		if (properties.isEmpty()) {
			return List.of();
		}

		List<DependencySiteSearchHit> hits = new ArrayList<>();
		Map<String, PropertyValue> defined = MavenParser.parseProperties(pomFile);
		for (String property : properties) {

			PropertyValue value = defined.get(property);
			if (value != null) {
				hits.add(DependencySiteSearchHit.declaration(value.getValueLiteral(), value.getValue()));
			}
		}

		return hits;
	}

	/**
	 * Collect every dependency or plugin {@code <version>} tag that contributes to
	 * the query, as a {@code ${property}} usage or an inline definition.
	 */
	private List<DependencySiteSearchHit> findVersionSites(XmlFile pomFile, DependencySiteQuery query) {

		List<DependencySiteSearchHit> hits = new ArrayList<>();
		for (XmlTag versionTag : PsiTreeUtil.findChildrenOfType(pomFile, XmlTag.class)) {

			if (!"version".equals(versionTag.getName()) || !isDependencyVersion(versionTag)) {
				continue;
			}

			String versionText = versionTag.getValue().getText().trim();
			Expression expression = Expression.from(versionText);
			XmlTag pluginOrDependency = versionTag.getParentTag();
			if (expression.isProperty()) {
				if (query.versionProperties().contains(expression.getPropertyName())) {

					hits.add(DependencySiteSearchHit.usage(pluginOrDependency != null ? pluginOrDependency : versionTag,
							versionText));
				}
				continue;
			}

			ArtifactId artifactId = MavenParser.parseArtifactId(pluginOrDependency, propertyResolver);
			if (artifactId != null && query.artifacts().contains(artifactId)) {
				hits.add(DependencySiteSearchHit.declaration(pluginOrDependency, versionText));
			}
		}

		return hits;
	}

	private static boolean isDependencyVersion(XmlTag versionTag) {

		XmlTag parent = versionTag.getParentTag();
		return parent != null && ("dependency".equals(parent.getName()) || "plugin".equals(parent.getName()));
	}

	private boolean canResolve() {
		return candidate && pom != null
				&& buildContext.isAvailable();
	}

	/**
	 * Resolution is anchored to the {@link XmlText} value of a version or property
	 * tag. Line markers and highlighting fire on every element of a tag (the angle
	 * brackets, the tag name, the value text, and the surrounding text node);
	 * pinning to the single text node keeps the gutter from duplicating across
	 * them. Completion and documentation pre-unleaf to this same text node.
	 */
	private boolean isResolvableElement(PsiElement element) {
		return element.isValid() && element instanceof XmlText;
	}

	private ArtifactReference resolveDirect(XmlTag versionTag) {

		XmlTag parentTag = versionTag.getParentTag();
		ArtifactId artifactId = MavenParser.parseArtifactId(parentTag, propertyResolver);
		if (artifactId == null) {
			return ArtifactReference.unresolved();
		}
		DeclarationSource declarationSource = MavenParser.getDeclarationSource(parentTag);

		String version = versionTag.getValue().getText().trim();
		Expression expression = Expression.from(version);

		if (expression.isProperty()) {

			ResolvedProperty property = resolveProperty(expression);
			return ArtifactReference.from(it -> {
				it.artifact(artifactId).declarationElement(parentTag)
						.versionSource(VersionSource.property(expression.getPropertyName()))
						.declarationSource(declarationSource);
				if (property != null) {
					ArtifactVersion.from(property.value()).ifPresent(it::version);
					it.versionLiteral(property.propertyValue().getValueLiteral());
					return;
				}

				String projectProperty = buildContext.getMavenProject().getProperties()
						.getProperty(expression.getPropertyName());
				if (StringUtils.hasText(projectProperty)) {
					ArtifactVersion.from(projectProperty).ifPresent(it::version);
				}
			});
		}

		return ArtifactReference.from(it -> {
			it.artifact(artifactId).declarationElement(parentTag)
					.declarationSource(declarationSource)
					.versionSource(VersionSource.from(version));
			ArtifactVersion.from(version).ifPresent(it::version);
			it.versionLiteral(versionTag);
		});
	}

	@Nullable
	private ResolvedProperty resolveProperty(Expression expression) {

		PropertyValue propertyValue = null;
		while (expression.isProperty()) {

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

		VersionProperty property = this.context.findProperty(propertyTag.getLocalName());
		if (property == null) {
			return ArtifactReference.unresolved();
		}

		ArtifactVersion currentVersion = getCurrentVersion(property, propertyTag);

		String tagName = propertyTag.getLocalName();
		ResolvedProperty resolvedProperty = resolveProperty(Expression.property(tagName));
		CachedArtifact firstArtifact = property.artifacts().getFirst();

		return ArtifactReference.from(it -> {
			it.artifact(firstArtifact.toArtifactId())
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
		return context.findCurrentVersion(artifactId);
	}

	private record ResolvedProperty(String value, PropertyValue propertyValue) {
	}

}
