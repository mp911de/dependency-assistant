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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectCache;
import biz.paluch.dap.state.Property;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;

/**
 * Shared version-upgrade lookup used by both {@link NewerVersionLineMarkerProvider} and {@link NewerVersionAnnotator}.
 */
class VersionUpgradeLookupService extends VersionUpgradeLookupSupport {

	private final MavenProjectContext buildContext;
	private final @Nullable XmlFile pom;
	private final boolean candidate;
	private final Cache cache;
	private final MavenProperties properties;

	public VersionUpgradeLookupService(Project project, MavenProjectContext context, PsiFile pom) {

		super(project, context);

		this.properties = new MavenProperties(project);
		this.buildContext = context;
		this.pom = pom instanceof XmlFile xmlFile ? xmlFile : null;
		this.candidate = MavenUtils.isMavenPomFile(pom);

		DependencyAssistantService service = DependencyAssistantService.getInstance(project);
		this.cache = service.getCache();
	}

	static VersionUpgradeLookupService create(PsiElement element) {
		return create(element.getProject(), element.getContainingFile());
	}

	static VersionUpgradeLookupService create(Project project, PsiFile pom) {
		return new VersionUpgradeLookupService(project, MavenProjectContext.of(project, pom), pom);
	}

	@Override
	public biz.paluch.dap.support.UpgradeSuggestion suggestUpgrades(PsiElement element) {

		if (element.getNode().getElementType() != XmlTokenType.XML_DATA_CHARACTERS || !candidate || pom == null
				|| !buildContext.isAvailable()) {
			return biz.paluch.dap.support.UpgradeSuggestion.none();
		}

		ProjectCache cache = this.cache.getProject(buildContext.getProjectId());

		XmlTag versionTag = PomUtil.findVersionTag(element);
		if (versionTag != null) {
			return suggestUpgrades(resolveArtifactDeclaration(versionTag));
		}

		XmlTag propertyTag = PomUtil.findPropertyTag(element);
		if (propertyTag != null) {
			return suggestUpgrades(resolveArtifactDeclaration(cache, propertyTag));
		}

		return biz.paluch.dap.support.UpgradeSuggestion.none();
	}

	private ArtifactReference resolveArtifactDeclaration(XmlTag versionTag) {

		XmlTag parentTag = versionTag.getParentTag();
		ArtifactId artifactId = PomUtil.getArtifactId(parentTag);
		if (artifactId == null) {
			return ArtifactReference.unresolved();
		}

		String version = versionTag.getValue().getText().trim();
		PropertyExpression expression = PropertyExpression.from(version);

		if (expression.isProperty()) {

			ResolvedProperty property = resolveProperty(expression);
			return ArtifactReference.from(it -> {
				it.artifact(artifactId).declarationElement(parentTag)
						.versionSource(VersionSource.property(expression.getPropertyName()));
				if (property != null) {
					ArtifactVersion.from(property.value()).ifPresent(it::version);
					it.versionLiteral(property.valueLiteral());
				}
			});
		}

		return ArtifactReference.from(it -> {
			it.artifact(artifactId).declarationElement(parentTag)
					.versionSource(StringUtils.hasText(version) ? VersionSource.declared(version) : VersionSource.none());
			ArtifactVersion.from(version).ifPresent(it::version);
			it.versionLiteral(versionTag);
		});
	}

	@Nullable
	private ResolvedProperty resolveProperty(PropertyExpression expression) {

		XmlTag propertyValue = null;
		while (expression.isProperty()) {

			String propertyName = expression.getPropertyName();
			propertyValue = properties.findProperty(buildContext.getMavenProject(), propertyName);
			if (propertyValue != null) {
				expression = PropertyExpression.from(propertyValue.getValue().getTrimmedText());
			} else {
				return null;
			}
		}

		if (propertyValue == null) {
			return null;
		}

		return new ResolvedProperty(propertyValue.getValue().getTrimmedText(), propertyValue);
	}

	private ArtifactReference resolveArtifactDeclaration(ProjectCache cache, XmlTag propertyTag) {

		Property property = findProperty(cache, propertyTag);
		if (property == null) {
			return ArtifactReference.unresolved();
		}

		ArtifactVersion currentVersion = getCurrentVersion(cache, propertyTag);
		if (currentVersion == null || !property.hasArtifacts()) {
			return ArtifactReference.unresolved();
		}

		String tagName = propertyTag.getLocalName();
		ResolvedProperty resolvedProperty = resolveProperty(PropertyExpression.property(tagName));
		CachedArtifact firstArtifact = property.artifacts().getFirst();

		return ArtifactReference.from(it -> {
			it.artifact(firstArtifact.toArtifactId()).declarationElement(propertyTag)
					.versionSource(VersionSource.property(tagName)).version(currentVersion);

			if (resolvedProperty != null) {
				it.versionLiteral(resolvedProperty.valueLiteral());
			}
		});
	}

	private VersionUpgradeLookupService.@Nullable UpgradeSuggestion findSuggestions(ArtifactReference lookupResult) {

		if (!lookupResult.isResolved()) {
			return null;
		}

		ArtifactDeclaration declaration = lookupResult.getDeclaration();
		if (!declaration.hasVersionSource() || !declaration.isVersionDefined()) {
			return null;
		}

		List<Release> options = this.cache.getReleases(declaration.getArtifactId(), false);
		if (options.isEmpty()) {
			return null;
		}

		return determineUpgrade(declaration.getVersion(), options);
	}

	private biz.paluch.dap.support.UpgradeSuggestion suggestUpgrades(ArtifactReference artifactReference) {

		if (!artifactReference.isResolved()) {
			return biz.paluch.dap.support.UpgradeSuggestion.none();
		}

		ArtifactDeclaration declaration = artifactReference.getDeclaration();
		if (!declaration.hasVersionSource() || !declaration.isVersionDefined()) {
			return biz.paluch.dap.support.UpgradeSuggestion.none();
		}

		List<Release> options = this.cache.getReleases(declaration.getArtifactId(), false);
		if (options.isEmpty()) {
			return biz.paluch.dap.support.UpgradeSuggestion.none();
		}

		UpgradeSuggestion upgradeSuggestion = determineUpgrade(declaration.getVersion(), options);
		if (upgradeSuggestion == null) {
			return biz.paluch.dap.support.UpgradeSuggestion.none();
		}

		return biz.paluch.dap.support.UpgradeSuggestion.of(upgradeSuggestion.strategy(), upgradeSuggestion.bestOption(),
				artifactReference);
	}

	private @Nullable Property findProperty(ProjectCache cache, XmlTag propertyTag) {

		String propertyName = propertyTag.getLocalName();
		Property property = cache.getProperty(propertyName);
		if (property == null || property.artifacts().isEmpty()) {
			return null;
		}
		return property;
	}

	@Nullable
	ArtifactVersion getCurrentVersion(ProjectCache cache, XmlTag propertyTag) {

		String propertyName = propertyTag.getLocalName();
		Property property = cache.getProperty(propertyName);
		if (property == null || property.artifacts().isEmpty()) {
			return null;
		}

		return getCurrentVersion(property, propertyTag);
	}

	private @Nullable ArtifactVersion getCurrentVersion(Property property, XmlTag propertyTag) {

		String currentVersionText = propertyTag.getValue().getText().trim();
		if (currentVersionText.contains("${")) {
			return null;
		}

		if (currentVersionText.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED) && hasCachedState()) {
			return getCurrentVersion(property);
		}

		try {
			return ArtifactVersion.of(currentVersionText);
		} catch (Exception e) {
			return null;
		}
	}

	private record ResolvedProperty(String value, PsiElement valueLiteral) {

	}

}
