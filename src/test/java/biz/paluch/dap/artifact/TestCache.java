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

package biz.paluch.dap.artifact;

import java.time.Clock;
import java.util.Collection;
import java.util.List;

import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.project.Project;

/**
 * Test {@link Cache} subclass exposing convenience methods to seed
 * vulnerability state.
 *
 * @author Mark Paluch
 */
public class TestCache extends Cache {

	public TestCache() {
	}

	public TestCache(Clock clock) {
		super(clock);
	}

	public void addVulnerabilities(ArtifactId artifactId, String version, Collection<Vulnerability> vulnerabilities) {
		recordVulnerabilities(artifactId, ArtifactVersion.of(version), vulnerabilities);
	}

	public void addVulnerabilities(ArtifactId artifactId, ArtifactVersion version,
			Collection<Vulnerability> vulnerabilities) {
		recordVulnerabilities(artifactId, version, vulnerabilities);
	}

	public void addVulnerabilities(ArtifactId artifactId, String version, Vulnerability... vulnerabilities) {
		addVulnerabilities(artifactId, ArtifactVersion.of(version), vulnerabilities);
	}

	public void addVulnerabilities(ArtifactId artifactId, ArtifactVersion version, Vulnerability... vulnerabilities) {
		recordVulnerabilities(artifactId, version, List.of(vulnerabilities));
	}

	public static TestCache getInstance(Project project) {
		return (TestCache) StateService.getInstance(project).getCache();
	}

}
