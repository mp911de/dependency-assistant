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

package biz.paluch.dap.checker;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;

/**
 * Read-only lookup of the known {@link Vulnerabilities} per artifact and
 * version.
 *
 * @author Mark Paluch
 * @see VulnerabilityRepository
 */
public interface VulnerabilitiesRepository {

	/**
	 * Return the known vulnerabilities for the given artifact version.
	 * @param artifactId the artifact to look up.
	 * @param version the version to look up.
	 * @return the known vulnerabilities, or {@link Vulnerabilities#absent()} when
	 * nothing is recorded for the version.
	 */
	Vulnerabilities getVulnerabilities(ArtifactId artifactId, ArtifactVersion version);

	/**
	 * Return the known vulnerabilities for the given package version.
	 * @param pkg the package to look up.
	 * @param version the version to look up.
	 * @return the known vulnerabilities, or {@link Vulnerabilities#absent()} when
	 * nothing is recorded for the version.
	 */
	Vulnerabilities getVulnerabilities(PackageIdentity pkg, ArtifactVersion version);

}
