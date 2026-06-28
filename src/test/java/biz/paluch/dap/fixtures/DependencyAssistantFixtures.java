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

package biz.paluch.dap.fixtures;

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.artifact.TestCache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedRelease;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.project.Project;

/**
 * Utility methods to setup Dependency Assistant for tests.
 * @author Mark Paluch
 */
public class DependencyAssistantFixtures {

	/**
	 * Set up Dependency Assistant for the given project.
	 */
	public static void setup(Project project) {
		StateService service = StateService.getInstance(project);
		service.setCache(createCache());
	}

	public static TestCache createCache() {
		TestCache cache = new TestCache();
		cache.addArtifacts(getArtifacts());
		return cache;
	}

	/**
	 * Return deep copies of the registry artifacts so a test that records
	 * vulnerabilities (mutating a {@link CachedRelease}) never pollutes the shared
	 * static fixtures consumed by other tests.
	 */
	private static List<CachedArtifact> getArtifacts() {

		List<CachedArtifact> copies = new ArrayList<>();
		for (CachedArtifact artifact : Releases.all()) {

			CachedArtifact copy = artifact.snapshot();
			List<CachedRelease> releases = new ArrayList<>();
			for (CachedRelease release : artifact.getReleases()) {
				releases.add(new CachedRelease(release.version(), release.date(), release.sha()));
			}
			copy.setCachedReleases(releases, copy.getLastSeen());
			copies.add(copy);
		}
		return copies;
	}

}
