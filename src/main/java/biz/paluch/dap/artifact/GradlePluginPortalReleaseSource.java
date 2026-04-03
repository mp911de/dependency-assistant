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
package biz.paluch.dap.artifact;

import java.util.List;

/**
 * {@link ReleaseSource} that queries the <a href="https://plugins.gradle.org/">Gradle Plugin Portal</a> for available
 * plugin versions.
 * <p>
 * The Portal exposes a Maven 2-compatible repository at {@value #PORTAL_URL}. Gradle plugin IDs are published there as
 * <em>marker artifacts</em> with the convention:
 *
 * <pre>
 *   groupId    = &lt;pluginId&gt;
 *   artifactId = &lt;pluginId&gt;.gradle.plugin
 * </pre>
 *
 * For example, the plugin {@code org.springframework.boot} is resolvable at:
 *
 * <pre>
 *   https://plugins.gradle.org/m2/org/springframework/boot/
 *       org.springframework.boot.gradle.plugin/maven-metadata.xml
 * </pre>
 *
 * {@link biz.paluch.dap.gradle.GradleDependencyParser} stores plugin declarations as
 * {@code ArtifactId.of(pluginId, pluginId)}, so this source recognises a plugin lookup whenever
 * {@code groupId.equals(artifactId)}, applies the marker-artifact transformation, and delegates the actual HTTP fetch
 * and XML parsing to a {@link RemoteRepositoryReleaseSource} backed by the Portal's Maven 2 endpoint.
 * <p>
 * For regular library coordinates ({@code groupId != artifactId}) the source returns an empty list immediately so that
 * no unnecessary HTTP requests are made to the Portal.
 *
 * @author Mark Paluch
 */
public class GradlePluginPortalReleaseSource implements ReleaseSource {

	public static GradlePluginPortalReleaseSource INSTANCE = new GradlePluginPortalReleaseSource();

	private static final String PORTAL_URL = "https://plugins.gradle.org/m2/";

	private static final RemoteRepositoryReleaseSource GRADLE_PLUGIN_PORTAL = new RemoteRepositoryReleaseSource(
			new RemoteRepository("gradle-plugin-portal", PORTAL_URL, null));

	@Override
	public List<Release> getReleases(ArtifactId artifactId) {

		// GradleDependencyParser encodes plugin IDs as ArtifactId(pluginId, pluginId).
		// Regular library coordinates have a distinct groupId and artifactId, so we skip
		// them to avoid unnecessary 404 round-trips to the Portal.
		if (!artifactId.groupId().equals(artifactId.artifactId())) {
			return List.of();
		}

		// Convert the plugin ID to its Maven marker artifact coordinate.
		String pluginId = artifactId.groupId();
		ArtifactId markerArtifact = ArtifactId.of(pluginId, pluginId + ".gradle.plugin");

		return GRADLE_PLUGIN_PORTAL.getReleases(markerArtifact);
	}

}
