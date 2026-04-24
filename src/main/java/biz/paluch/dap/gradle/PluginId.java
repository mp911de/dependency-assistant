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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * DSL-agnostic representation of a Gradle plugin declaration.
 * <p>Captures the data needed to render a {@link DependencySite} regardless of
 * whether the source is Groovy or Kotlin DSL: the declaration call PSI, the
 * version literal PSI, the resolved id, the raw version text, and an optional
 * version property key when the version was expressed as a single
 * {@code ${property}} placeholder.
 *
 * @param declaration the call PSI representing the plugin declaration.
 * @param versionLiteral the PSI element used as the version anchor (the inline
 * literal, or a property-backed PSI when the declared version delegates to a
 * resolved script property).
 * @param resolvedId the fully resolved plugin id (placeholders substituted).
 * @param versionText the raw version text as authored.
 * @param versionPropertyKey the property name when the version is expressed as
 * a single {@code ${property}} placeholder; {@literal null} otherwise.
 * @author Mark Paluch
 */
record PluginId(PsiElement declaration, PsiElement versionLiteral, String resolvedId, String versionText,
		@Nullable String versionPropertyKey) {

	private static final Logger LOG = Logger.getInstance(PluginId.class);

	/**
	 * Returns a plugin {@link ArtifactId} when {@link #resolvedId()} is a valid
	 * plugin id, or {@literal null} otherwise.
	 */
	@Nullable
	ArtifactId toValidatedArtifactId() {

		if (!GradlePlugin.isValidPluginId(resolvedId)) {
			LOG.debug("Skipping plugin entry: cannot use resolved id '%s'".formatted(resolvedId));
			return null;
		}
		return GradlePlugin.of(resolvedId);
	}

	/**
	 * Render this plugin declaration as a {@link DependencySite}. Returns
	 * {@literal null} when the resolved id is not a valid plugin id.
	 */
	@Nullable
	DependencySite toDependencySite() {

		ArtifactId artifactId = toValidatedArtifactId();
		if (artifactId == null) {
			return null;
		}

		PropertyExpression versionExpression = StringUtils.hasText(versionPropertyKey)
				? PropertyExpression.property(versionPropertyKey)
				: PropertyExpression.from(versionText);
		return GradleDependency.of(artifactId, versionExpression).toDependencySite(declaration, versionLiteral);
	}

}
