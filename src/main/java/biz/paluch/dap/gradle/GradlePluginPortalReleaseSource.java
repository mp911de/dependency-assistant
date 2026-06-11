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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.MavenRepository;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.RemoteRepository;
import com.intellij.openapi.progress.ProgressIndicator;

/**
 * {@link ReleaseSource} for Gradle Plugin Portal marker artifacts.
 *
 * <p>
 * Plugin declarations are represented as {@code groupId == artifactId}. Such
 * lookups are translated to the Portal marker artifact
 * {@code <pluginId>:<pluginId>.gradle.plugin}. Regular library coordinates
 * return an empty list.
 *
 * @author Mark Paluch
 */
public class GradlePluginPortalReleaseSource implements ReleaseSource {

	/**
	 * Shared Gradle Plugin Portal release source.
	 */
	public static GradlePluginPortalReleaseSource INSTANCE = new GradlePluginPortalReleaseSource();

	private static final String PORTAL_URL = "https://plugins.gradle.org/m2/";

	private static final MavenRepository GRADLE_PLUGIN_PORTAL = new MavenRepository(
			new RemoteRepository("gradle-plugin-portal", PORTAL_URL, null));

	private GradlePluginPortalReleaseSource() {
	}

	@Override
	public List<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) {

		if (GradlePluginId.isPlugin(artifactId)) {
			String pluginId = artifactId.groupId();
			ArtifactId markerArtifact = ArtifactId.of(pluginId, pluginId + ".gradle.plugin");
			return GRADLE_PLUGIN_PORTAL.getReleases(markerArtifact, indicator);
		}

		// TODO: HEAD for individual versions? might be an overkill.
		// but only for these, that we don't yet know 🙃
		// ??
		return GRADLE_PLUGIN_PORTAL.getReleases(artifactId, indicator);
	}

}
