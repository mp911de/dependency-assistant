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
package biz.paluch.dap;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectCache;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.Property;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

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
class VersionUpgradeLookupService {

	private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]*)\\}");
	private final MavenContext mavenContext;
	private final @Nullable XmlFile pom;
	private final boolean candidate;
	private final @Nullable ProjectState projectState;
	private final Cache cache;

	VersionUpgradeLookupService(Project project, PsiFile pom) {

		this.mavenContext = MavenContext.of(project, pom);
		this.pom = pom instanceof XmlFile xmlFile ? xmlFile : null;
		this.candidate = MavenUtils.isMavenPomFile(pom);

		DependencyAssistantService service = DependencyAssistantService.getInstance(project);
		this.cache = service.getCache();
		this.projectState = mavenContext.isAvailable() ? service.getProjectState(mavenContext.getMavenId()) : null;
	}

	/**
	 * Resolves the version upgrade result for a PSI element, or returns {@code null} if the element does not represent a
	 * version value or no upgrade is available in the cache.
	 */
	public VersionUpgradeLookupService.@Nullable UpgradeSuggestion determineUpgrade(PsiElement element) {

		if (element.getNode().getElementType() != XmlTokenType.XML_DATA_CHARACTERS || !candidate || pom == null
				|| !mavenContext.isAvailable()) {
			return null;
		}

		ProjectCache cache = this.cache.getProject(mavenContext.getMavenId());

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

	public @Nullable Property getProperty(String propertyName) {
		if (projectState != null) {
			return projectState.getProperty(propertyName);
		}
		return null;
	}

	/**
	 * The outcome of a version lookup: the best available upgrade tier, the best candidate version, and the resolved
	 * current version.
	 */
	public record UpgradeSuggestion(UpgradeStrategy strategy, Release bestOption, ArtifactVersion current) {

		public String getMessage() {
			String upgradeTarget = MessageBundle.message("dialog.upgradeTarget." + strategy.name());
			return MessageBundle.message("gutter.newer.tooltip", upgradeTarget, bestOption().version().toString());
		}

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

		if (version != null && version.contains("${") && mavenContext.isAvailable()) {
			return getCurrentVersion(artifactId);
		}

		try {
			return ArtifactVersion.of(version);
		} catch (Exception e) {
			return null;
		}
	}

	public @Nullable ArtifactVersion getCurrentVersion(Property property) {
		if (property.artifacts().isEmpty()) {
			return null;
		}
		return getCurrentVersion(property.artifacts().iterator().next().toArtifactId());
	}

	public @Nullable ArtifactVersion getCurrentVersion(ArtifactId artifactId) {

		if (projectState == null) {
			return null;
		}

		Dependency dependency = projectState.findDependency(artifactId);
		return dependency != null ? dependency.getCurrentVersion() : null;
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

			if (pom.getDocument() != null && pom.getDocument().getRootTag() != null) {
				XmlTag properties = pom.getDocument().getRootTag().findFirstSubTag("properties");
				if (properties != null) {
					value = properties.getSubTagText(propertyName);
				}
			}

			if (value == null && mavenContext.isAvailable()) {
				value = mavenContext.doWithMaven(it -> it.getProperties().getProperty(propertyName));
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

		CachedArtifact firstArtifact = property.artifacts().iterator().next();
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

		if (currentVersionText.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED) && projectState != null) {
			return getCurrentVersion(property);
		}

		try {
			return ArtifactVersion.of(currentVersionText);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Determines the best available upgrade tier from the given options relative to the current version. Returns
	 * {@code null} if no upgrade is available.
	 */
	public static VersionUpgradeLookupService.@Nullable UpgradeSuggestion determineUpgrade(ArtifactVersion current,
			List<Release> options) {

		Release major = UpgradeStrategy.MAJOR.select(current, options);
		Release minor = UpgradeStrategy.MINOR.select(current, options);
		Release patch = UpgradeStrategy.PATCH.select(current, options);

		if (major == null && minor == null && patch == null) {
			return null;
		}

		UpgradeStrategy strategy;
		Release bestOption;
		if (major != null) {
			strategy = UpgradeStrategy.MAJOR;
			bestOption = major;
		} else if (minor != null) {
			strategy = UpgradeStrategy.MINOR;
			bestOption = minor;
		} else {
			strategy = UpgradeStrategy.PATCH;
			bestOption = patch;
		}

		return new UpgradeSuggestion(strategy, bestOption, current);
	}

}
