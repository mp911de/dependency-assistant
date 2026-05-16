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

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.idea.maven.indices.MavenGAVIndex;

/**
 * Strategy interface for obtaining known releases of a Maven artifact.
 *
 * <p>Implementations typically adapt one registry, such as a remote Maven
 * repository, the Gradle Plugin Portal, or the IDE-local Maven index. They may
 * be called concurrently by {@link ReleaseResolver}.
 *
 * <p>Throw {@link ArtifactNotFoundException} only for a definitive absence at
 * this source. Return an empty list when release data is simply unavailable.
 *
 * @author Mark Paluch
 * @see ReleaseResolver
 * @see RemoteRepositoryReleaseSource
 * @see GradlePluginPortalReleaseSource
 */
public interface ReleaseSource {

	/**
	 * Wrap each {@link RemoteRepository} as a {@link ReleaseSource} backed by
	 * {@link RemoteRepositoryReleaseSource}.
	 *
	 * @param remoteRepositories the repositories to wrap; must not be
	 * {@literal null}.
	 * @return a list of release sources in the same order; guaranteed to be not
	 * {@literal null}.
	 */
	static List<ReleaseSource> getReleaseSources(Collection<RemoteRepository> remoteRepositories) {
		return remoteRepositories.stream().map(RemoteRepositoryReleaseSource::new).map(it -> (ReleaseSource) it)
				.toList();
	}

	/**
	 * Return all known releases for the given artifact at this source.
	 * <p>The returned list may be unsorted and may contain release, preview, and
	 * snapshot versions. Implementations should periodically call
	 * {@link ProgressIndicator#checkCanceled()} during long-running fetches to
	 * honor user cancellation.
	 * @param artifactId the artifact whose releases to retrieve.
	 * @param indicator the progress indicator used to honor cancellation; must not
	 * be {@literal null}.
	 * @return the releases known to this source.
	 * @throws ArtifactNotFoundException if the artifact is definitively absent.
	 */
	List<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator);

	/**
	 * Return the built-in {@link ReleaseSource} backed by Maven Central.
	 */
	static ReleaseSource mavenCentral() {
		return RemoteRepositoryReleaseSource.MAVEN_CENTRAL;
	}

	/**
	 * Return the built-in {@link ReleaseSource} backed by the Gradle Plugin Portal.
	 * <p>This source is intended for Gradle plugin marker coordinates.
	 */
	static ReleaseSource gradlePluginPortal() {
		return GradlePluginPortalReleaseSource.INSTANCE;
	}

	/**
	 * {@link ReleaseSource} backed by the IDE-local {@link MavenGAVIndex}.
	 *
	 * @author Mark Paluch
	 */
	class IndexReleaseSource implements ReleaseSource {

		private final MavenGAVIndex index;

		/**
		 * Create a new {@code IndexReleaseSource}.
		 * @param index the IDE-local Maven index to query.
		 */
		public IndexReleaseSource(MavenGAVIndex index) {
			this.index = index;
		}

		@Override
		public List<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) {

			Set<String> versions = index.getVersions(artifactId.groupId(), artifactId.artifactId());
			return versions.stream().map(Release::of).toList();
		}

	}

}
