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
package biz.paluch.dap.support;

import java.util.List;

import biz.paluch.dap.ProjectBuildContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.Property;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Shared version-upgrade lookup used by both
 * {@code NewerVersionLineMarkerProvider} and {@code NewerVersionAnnotator}.
 */
public abstract class VersionUpgradeLookupSupport {

	private final @Nullable ProjectState projectState;

	protected VersionUpgradeLookupSupport(Project project, ProjectBuildContext buildContext) {

		DependencyAssistantService service = DependencyAssistantService.getInstance(project);
		this.projectState = buildContext.isAvailable() ? service.getProjectState(buildContext.getProjectId()) : null;
	}

	protected boolean hasCachedState() {
		return projectState != null;
	}

	/**
	 * Resolves the version upgrade result for a PSI element, or returns
	 * {@code null} if the element does not represent a version value or no upgrade
	 * is available in the cache.
	 */
	public abstract UpgradeSuggestion suggestUpgrades(PsiElement element);

	/**
	 * Resolves upgrade suggestion from a resolved {@link ArtifactReference} using
	 * releases from the given cache.
	 *
	 * @param cache must not be {@literal null}.
	 * @param artifactReference the reference to evaluate
	 * @return a present suggestion or
	 * {@link biz.paluch.dap.support.UpgradeSuggestion#none()}
	 */
	protected UpgradeSuggestion suggestUpgrades(Cache cache,
			ArtifactReference artifactReference) {

		if (!artifactReference.isResolved()) {
			return UpgradeSuggestion.none();
		}

		ArtifactDeclaration declaration = artifactReference.getDeclaration();
		if (!declaration.hasVersionSource() || !declaration.isVersionDefined()) {
			return UpgradeSuggestion.none();
		}

		List<Release> options = cache.getReleases(declaration.getArtifactId(), false);
		if (options.isEmpty()) {
			return UpgradeSuggestion.none();
		}

		return determineUpgrade(artifactReference, declaration.getVersion(), options);
	}

	/**
	 * Resolves the property with the given name. Returns {@code null} if no such
	 * property exists in the project state.
	 *
	 * @param propertyName the property name
	 * @return
	 */
	public @Nullable Property getProperty(String propertyName) {
		return projectState != null ? projectState.findProperty(propertyName) : null;
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

	/**
	 * Determines the best available upgrade tier from the given options relative to
	 * the current version.
	 */
	public static UpgradeSuggestion determineUpgrade(ArtifactReference artifactReference, ArtifactVersion current,
			List<Release> options) {

		Release major = UpgradeStrategy.MAJOR.select(current, options);
		Release minor = UpgradeStrategy.MINOR.select(current, options);
		Release patch = UpgradeStrategy.PATCH.select(current, options);

		if (major == null && minor == null && patch == null) {
			return UpgradeSuggestion.none();
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

		return UpgradeSuggestion.of(strategy, bestOption, artifactReference);
	}


}
