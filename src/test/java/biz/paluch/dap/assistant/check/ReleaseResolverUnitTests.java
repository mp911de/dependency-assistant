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

package biz.paluch.dap.assistant.check;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.ReleaseSources;
import biz.paluch.dap.state.Cache;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link ReleaseResolver}.
 *
 * @author Mark Paluch
 */
class ReleaseResolverUnitTests {

	private static final ArtifactId LETTUCE_CORE = ArtifactId.of("io.lettuce", "lettuce-core");

	static final PackageIdentity LETTUCE = PackageIdentity.of(LETTUCE_CORE, PackageSystem.MAVEN);


	@Test
	void keepsCompletedReleasesWhenSiblingSourceTimesOut() {

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			ReleaseResolver resolver = new ReleaseResolver(executor, new AbstractProgressIndicatorBase(),
					new Cache(), 50, TimeUnit.MILLISECONDS);
			ReleaseSources sources = new ReleaseSources(LETTUCE,
					List.of(new ReleasingSource("central", "1.0.0"), new BlockingSource("slow")));

			ReleaseLookupResult result = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
					Duration.ofSeconds(1), () -> resolver.getReleases(sources, ReleaseResolver.reset()));

			assertThat(result.error()).isNull();
			assertThat(result.releases()).containsRelease("1.0.0");
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void reportsTimeoutWhenNoSourceCompletes() {

		ExecutorService executor = Executors.newFixedThreadPool(1);
		try {
			ReleaseResolver resolver = new ReleaseResolver(executor, new AbstractProgressIndicatorBase(),
					new Cache(), 50, TimeUnit.MILLISECONDS);
			ReleaseSources sources = new ReleaseSources(LETTUCE,
					List.of(new BlockingSource("slow")));

			ReleaseLookupResult result = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
					Duration.ofSeconds(1), () -> resolver.getReleases(sources, ReleaseResolver.reset()));

			assertThat(result.error()).contains("Release source slow timed out");
			assertThat(result.releases()).isEmpty();
		} finally {
			executor.shutdownNow();
		}
	}

	private static class ReleasingSource implements ReleaseSource {

		private final String id;

		private final String version;

		ReleasingSource(String id, String version) {
			this.id = id;
			this.version = version;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public List<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) {
			return List.of(Release.of(version));
		}

	}

	private static class BlockingSource implements ReleaseSource {

		private final String id;

		BlockingSource(String id) {
			this.id = id;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public List<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) {

			while (!Thread.currentThread().isInterrupted()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			return List.of();
		}

	}

}
