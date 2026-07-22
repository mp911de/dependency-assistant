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

package biz.paluch.dap.fixtures;

import java.util.Collection;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.state.CachedRelease;
import biz.paluch.dap.state.FetchPlan;
import biz.paluch.dap.state.FetchedReleases;
import biz.paluch.dap.util.Sequence;

/**
 * Test factory for {@link FetchedReleases} without a preferred source and
 * without captured project metadata.
 *
 * @author Mark Paluch
 */
public class TestFetchedReleases {

	private TestFetchedReleases() {
	}

	/**
	 * Create {@link FetchedReleases} carrying no preferred source and no captured
	 * project metadata.
	 */
	public static FetchedReleases of(ArtifactId artifactId, Collection<CachedRelease> releases, FetchPlan plan,
			Collection<String> emptySources) {
		return new FetchedReleases(artifactId, releases, plan, null, emptySources, null);
	}

	/**
	 * Create {@link FetchedReleases} for the given version strings, carrying no
	 * preferred source and no captured project metadata.
	 */
	public static FetchedReleases of(ArtifactId artifactId, FetchPlan plan, Collection<String> emptySources,
			String... versions) {
		return of(artifactId, Sequence.of(versions).map(Release::of).map(CachedRelease::from).toList(), plan,
				emptySources);
	}

}
