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
 * Extension to {@link ArtifactId} representing a Gradle plugin.
 * <p>Plugins use a single identifier that is used for {@link #artifactId()} and
 * {@link #groupId()}.
 *
 * @author Mark Paluch
 */
interface GradlePlugin extends ArtifactId {

	Pattern PLUGIN_ID_PATTERN = Pattern.compile("[a-zA-Z0-9._-]+");

	/**
	 * Return whether {@code id} is a safe, well-formed Gradle plugin ID.
	 */
	static boolean isValidPluginId(@Nullable String id) {
		return StringUtils.hasText(id) && PLUGIN_ID_PATTERN.matcher(id).matches();
	}

	/**
	 * Create a new plugin {@link ArtifactId}.
	 *
	 * @param id the plugin identifier.
	 * @return the created {@link GradlePlugin} for {@code id}.
	 */
	static GradlePlugin of(String id) {
		return new DefaultGradlePlugin(ArtifactId.of(id, id));
	}

	/**
	 * Return whether the given {@link ArtifactId} is a Gradle plugin (that is,
	 * whether {@link #groupId()} and {@link #artifactId()} are equal).
	 *
	 * @param id the artifact to check.
	 * @return {@code true} if the {@link ArtifactId} represents a plugin.
	 */
	static boolean isPlugin(ArtifactId id) {
		return id.artifactId().equals(id.groupId());
	}

	/**
	 * Adapt the given {@link ArtifactId} to a {@code GradlePlugin}.
	 * <p>Return the existing instance when possible; otherwise create a new
	 * {@link GradlePlugin}. The supplied identifier must represent a Gradle plugin.
	 *
	 * @param id the identifier.
	 * @return the Gradle plugin.
	 * @throws IllegalArgumentException if the given {@code id} does not represent a
	 * Gradle plugin.
	 */
	static GradlePlugin from(ArtifactId id) {
		if (id instanceof GradlePlugin plugin) {
			return plugin;
		}
		Assert.isTrue(isPlugin(id), "ArtifactId is not a plugin");
		return new DefaultGradlePlugin(id);
	}

	/**
	 * Return the plugin identifier.
	 *
	 * @see #groupId()
	 */
	default String id() {
		return groupId();
	}

}
