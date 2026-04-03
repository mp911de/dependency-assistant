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

import biz.paluch.dap.ProjectBuildContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectCache;
import biz.paluch.dap.state.Property;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]*)\\}");
	private final ProjectBuildContext buildContext;
	private final @Nullable XmlFile pom;
	private final boolean candidate;
	private final Cache cache;

	public VersionUpgradeLookupService(Project project, ProjectBuildContext context, PsiFile pom) {

		super(project, context);

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

	/**
	 * Resolves the version upgrade result for a PSI element, or returns {@code null} if the element does not represent a
	 * version value or no upgrade is available in the cache.
	 */
	public VersionUpgradeLookupService.@Nullable UpgradeSuggestion determineUpgrade(PsiElement element) {

		if (element.getNode().getElementType() != XmlTokenType.XML_DATA_CHARACTERS || !candidate || pom == null
				|| !buildContext.isAvailable()) {
			return null;
		}

		ProjectCache cache = this.cache.getProject(buildContext.getProjectId());

		XmlTag versionTag = PomUtil.findVersionTag(element);
		if (versionTag != null) {
			return resolveVersionTag(versionTag);
		}

		XmlTag propertyTag = PomUtil.findPropertyTag(element);
		if (propertyTag != null) {
			return resolvePropertyTag(cache, propertyTag);
		}

		return null;
	}

	private VersionUpgradeLookupService.@Nullable UpgradeSuggestion resolveVersionTag(XmlTag versionTag) {

		XmlTag parentTag = versionTag.getParentTag();
		ArtifactId artifactId = PomUtil.getArtifactId(parentTag);
		if (artifactId == null) {
			return null;
		}

		ArtifactVersion currentVersion = getCurrentVersion(versionTag, artifactId);
		if (currentVersion == null) {
			return null;
		}

		List<Release> options = cache.getReleases(artifactId, false);
		if (options.isEmpty()) {
			return null;
		}

		return determineUpgrade(currentVersion, options);
	}

	public @Nullable ArtifactVersion getCurrentVersion(XmlTag versionTag, ArtifactId artifactId) {

		String version = versionTag.getValue().getText().trim();

		if (version.contains("${")) {
			version = resolveProperty(version);
		}

		if (version != null && version.contains("${") && buildContext.isAvailable()) {
			return getCurrentVersion(artifactId);
		}

		if (!StringUtils.hasText(version)) {
			return null;
		}

		try {
			return ArtifactVersion.of(version);
		} catch (Exception e) {
			return null;
		}
	}

	@Nullable
	String resolveProperty(String expression) {

		while (expression.contains("${")) {

			Matcher matcher = PROPERTY_PATTERN.matcher(expression);
			if (!matcher.find()) {
				break;
			}

			String propertyName = matcher.group(1);
			String value = null;

			if (pom != null && pom.getDocument() != null && pom.getDocument().getRootTag() != null) {
				XmlTag properties = pom.getDocument().getRootTag().findFirstSubTag("properties");
				if (properties != null) {
					value = properties.getSubTagText(propertyName);
				}
			}

			if (value == null && buildContext.isAvailable()) {
				value = buildContext.getPropertyValue(propertyName);
			}

			if (value != null) {
				value = resolveProperty(value.trim());
			}

			if (value == null) {
				return null;
			}

			expression = matcher.replaceFirst(Matcher.quoteReplacement(value.trim()));
		}

		return expression;
	}

	VersionUpgradeLookupService.@Nullable UpgradeSuggestion resolvePropertyTag(ProjectCache cache, XmlTag propertyTag) {

		Property property = findProperty(cache, propertyTag);
		if (property == null) {
			return null;
		}

		ArtifactVersion currentVersion = getCurrentVersion(cache, propertyTag);
		if (currentVersion == null) {
			return null;
		}

		CachedArtifact firstArtifact = property.artifacts().getFirst();
		List<Release> options = this.cache.getReleases(firstArtifact.toArtifactId(), false);
		if (options.isEmpty()) {
			return null;
		}

		return determineUpgrade(currentVersion, options);
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

}
