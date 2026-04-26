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
import java.util.Set;

import org.jetbrains.idea.maven.indices.MavenGAVIndex;

/**
 * Strategy interface for fetching the available releases of a Maven artifact.
 * <p>Each implementation covers a distinct release registry: a remote Maven
 * repository, the Gradle Plugin Portal, or the IDE-local Maven index.
 * Implementations are expected to be stateless, or at most hold immutable
 * configuration such as a repository URL or credentials, so that they can be
 * held as long-lived singletons and called concurrently from multiple threads.
 * <p>{@link ReleaseResolver} submits calls to all registered sources in
 * parallel via an {@link java.util.concurrent.ExecutorService}. To signal a
 * definitive absence — meaning the artifact is not published at this source at
 * all — implementations must throw {@link ArtifactNotFoundException}. That
 * exception causes {@link ReleaseResolver} to skip the source rather than
 * propagate the failure. Any other {@link RuntimeException} is treated as a
 * transient error and propagated to the caller once results from other sources
 * have been collected.
 * <p>An empty list means no version data is available from this source for the
 * given artifact (for example, a non-fatal HTTP error was absorbed internally),
 * which is distinct from a definitive 404.
 * <p>Built-in sources are accessible via the static factory methods:
 * <ul>
 * <li>{@link #mavenCentral()} — the canonical Maven Central HTTP source.</li>
 * <li>{@link #gradlePluginPortal()} — the Gradle Plugin Portal HTTP
 * source.</li>
 * </ul>
 * The IDE-local Maven index is covered by {@link IndexReleaseSource}.
 *
 * @author Mark Paluch
 * @see ReleaseResolver
 * @see RemoteRepositoryReleaseSource
 * @see GradlePluginPortalReleaseSource
 */
public interface ReleaseSource {

	/**
	 * Return all known releases for the given artifact at this source.
	 * <p>The returned list may include release, preview (RC, milestone), and
	 * SNAPSHOT versions depending on what the underlying registry exposes; callers
	 * are responsible for any further filtering. The list may be in any order —
	 * callers must not assume a particular sort. An empty list signals that no
	 * version data is available from this source, which is a recoverable condition;
	 * other sources may still contribute results.
	 * <p>Implementations are called from a background thread and must be safe for
	 * concurrent use.
	 *
	 * @param artifactId the artifact whose releases to retrieve; must not be
	 * {@literal null}.
	 * @return the releases known to this source; guaranteed to be not
	 * {@literal null} but may be empty.
	 * @throws ArtifactNotFoundException if the artifact is definitively absent from
	 * this source (e.g. HTTP 404). {@link ReleaseResolver} catches this exception
	 * and moves on to remaining sources.
	 * @throws RuntimeException for transient or unexpected errors.
	 */
	List<Release> getReleases(ArtifactId artifactId);

	/**
	 * Return the built-in {@link ReleaseSource} backed by Maven Central.
	 *
	 * @return the Maven Central source; guaranteed to be not {@literal null}.
	 */
	static ReleaseSource mavenCentral() {
		return RemoteRepositoryReleaseSource.MAVEN_CENTRAL;
	}

	/**
	 * Return the built-in {@link ReleaseSource} backed by the Gradle Plugin Portal.
	 * <p>This source handles Gradle plugin coordinates (where {@code groupId}
	 * equals {@code artifactId}) and translates them to the Portal's
	 * marker-artifact convention before fetching. For regular library coordinates
	 * it returns an empty list immediately.
	 *
	 * @return the Gradle Plugin Portal source; guaranteed to be not
	 * {@literal null}.
	 */
	static ReleaseSource gradlePluginPortal() {
		return GradlePluginPortalReleaseSource.INSTANCE;
	}

	/**
	 * {@link ReleaseSource} backed by the IDE-local {@link MavenGAVIndex}.
	 * <p>Queries the IntelliJ Maven integration's local index, which reflects
	 * artifacts that have been downloaded or indexed in the current IDE
	 * installation. The lookup is fast and requires no network access. Release
	 * dates are not available from the index and are omitted from the returned
	 * {@link Release} instances.
	 *
	 * @author Mark Paluch
	 */
	class IndexReleaseSource implements ReleaseSource {

		private final MavenGAVIndex index;

		public IndexReleaseSource(MavenGAVIndex index) {
			this.index = index;
		}

		@Override
		public List<Release> getReleases(ArtifactId artifactId) {

			Set<String> versions = index.getVersions(artifactId.groupId(), artifactId.artifactId());

			return versions.stream().map(Release::of).toList();
		}
	}
}
