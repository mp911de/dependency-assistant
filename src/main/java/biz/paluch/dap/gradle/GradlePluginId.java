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

import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.HasPackageIdentity;
import biz.paluch.dap.artifact.HasPackageSystem;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Gradle plugin identity represented by equal group and artifact names.
 * <p>Use the declared plugin ID. Plugin Portal marker coordinates are produced
 * by {@link GradlePluginPortalReleaseSource}.
 *
 * @author Mark Paluch
 */
interface GradlePluginId extends ArtifactId, HasPackageSystem, HasPackageIdentity {

	Pattern PLUGIN_ID_PATTERN = Pattern.compile("[a-zA-Z0-9._-]+");

	/**
	 * Return whether the text has a supported plugin-ID shape.
	 * <p>This does not validate Gradle plugin resolution rules.
	 */
	static boolean isValidPluginId(@Nullable String id) {
		return StringUtils.hasText(id) && PLUGIN_ID_PATTERN.matcher(id).matches();
	}

	/**
	 * Return whether the call declares a plugin through {@code id}, {@code kotlin},
	 * or {@code embeddedKotlin}.
	 */
	static boolean isPluginCall(@Nullable String callName) {
		return GradleUtils.isPlugin(callName) || GradleUtils.KOTLIN.equals(callName)
				|| GradleUtils.EMBEDDED_KOTLIN.equals(callName);
	}

	/**
	 * Resolve a plugin call. For example, {@code kotlin("jvm")} identifies
	 * {@code org.jetbrains.kotlin.jvm}.
	 * @return {@literal null} if the call does not declare a plugin.
	 */
	static @Nullable GradlePluginId fromCall(@Nullable String callName, String argument) {

		if (GradleUtils.isPlugin(callName)) {
			return GradlePluginId.of(argument);
		}
		if (GradleUtils.KOTLIN.equals(callName) || GradleUtils.EMBEDDED_KOTLIN.equals(callName)) {
			return GradlePluginId.of("org.jetbrains.kotlin." + argument);
		}
		return null;
	}

	/**
	 * Create an identity from the declared plugin ID.
	 * @throws IllegalArgumentException if the ID is absent or contains unsupported
	 * characters.
	 */
	static GradlePluginId of(String id) {
		Assert.isTrue(isValidPluginId(id), "Invalid plugin id: " + id);
		return new DefaultGradlePluginId(ArtifactId.of(id, id));
	}

	/**
	 * Return whether the identity has equal group and artifact names.
	 * <p>Gradle release lookup treats this shape as a plugin declaration.
	 */
	static boolean isPlugin(ArtifactId id) {
		return id.artifactId().equals(id.groupId());
	}

	default String id() {
		return groupId();
	}

	@Override
	default PackageIdentity getPackageIdentity() {
		return PackageIdentity.of(this, getPackageSystem());
	}

	@Override
	default PackageSystem getPackageSystem() {
		return PackageSystem.MAVEN;
	}
}
