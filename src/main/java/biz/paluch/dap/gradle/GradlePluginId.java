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

import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link ArtifactId} adapter for dependencies declared through Gradle's plugin
 * DSL.
 *
 * <p>Plugin declarations use a single identifier, not module coordinates. The
 * assistant still needs to route those declarations through the common
 * artifact, release, and update infrastructure, so a plugin id is normalized as
 * an {@link ArtifactId} whose {@link #groupId()} and {@link #artifactId()} are
 * both the plugin id.
 *
 * <p>This type models the declaration-facing identity of a plugin. It does not
 * expose Gradle Plugin Portal marker coordinates such as
 * {@code <pluginId>:<pluginId>.gradle.plugin}; that translation belongs to the
 * release-source layer. Keeping the marker artifact out of parser code lets
 * plugin declarations behave like regular dependency sites while preserving the
 * plugin id users see in the build file.
 *
 * @author Mark Paluch
 * @see GradleArtifactId
 * @see GradleDependency
 */
interface GradlePluginId extends ArtifactId {

	/**
	 * Conservative character set accepted for plugin ids before they are adapted to
	 * the common artifact model.
	 */
	Pattern PLUGIN_ID_PATTERN = Pattern.compile("[a-zA-Z0-9._-]+");

	/**
	 * Screen candidate plugin ids before creating an artifact adapter.
	 * <p>This check is intentionally local to parser dispatch and user input
	 * normalization. It is not a substitute for Gradle's own plugin resolution
	 * rules.
	 * @param id candidate plugin id text.
	 * @return {@literal true} if the text can be represented as a
	 * {@link GradlePluginId}.
	 */
	static boolean isValidPluginId(@Nullable String id) {
		return StringUtils.hasText(id) && PLUGIN_ID_PATTERN.matcher(id).matches();
	}

	/**
	 * Create a normalized plugin identity for use in dependency-site and release
	 * lookup pipelines.
	 * <p>The given value must be the plugin id as declared in the build file, not
	 * the Gradle Plugin Portal marker artifact.
	 *
	 * @param id the plugin identifier.
	 * @return the normalized plugin identity.
	 */
	static GradlePluginId of(String id) {
		Assert.isTrue(isValidPluginId(id), "Invalid plugin id: " + id);
		return new DefaultGradlePluginId(ArtifactId.of(id, id));
	}

	/**
	 * Identify plugin declarations after they have crossed into the common
	 * {@link ArtifactId} model.
	 * <p>The normalized {@code groupId == artifactId} shape is used as a stable
	 * signal by Gradle-specific release lookup and rendering code. Regular module
	 * coordinates should not be created with equal group and artifact ids unless
	 * they intentionally represent a plugin declaration.
	 *
	 * @param id the artifact to check.
	 * @return {@literal true} if the artifact follows the plugin identity shape.
	 */
	static boolean isPlugin(ArtifactId id) {
		return id.artifactId().equals(id.groupId());
	}

	/**
	 * Expose the plugin id at plugin-specific call sites.
	 * <p>This method makes the single-id nature of plugin declarations explicit
	 * while retaining {@link ArtifactId} compatibility for shared infrastructure.
	 *
	 * @see #groupId()
	 */
	default String id() {
		return groupId();
	}

}
