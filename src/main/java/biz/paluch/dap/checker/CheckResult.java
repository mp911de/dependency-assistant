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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;

/**
 * The outcome of a bulk {@link CheckRequest}: for each {@link PackageIdentity
 * package} a source could check, the {@link Vulnerabilities vulnerabilities} it
 * produced per version.
 *
 * <p>The result holds only versions a source actually checked: a version no
 * source could resolve is absent from the map.
 * {@link #getVulnerabilities(PackageIdentity, ArtifactVersion)} therefore
 * returns {@link Vulnerabilities#absent() absent} exactly when no source
 * returned that version, and a {@link Vulnerabilities#isClean() clean} or
 * {@link Vulnerabilities#isVulnerable() vulnerable} result otherwise.
 *
 * @author Mark Paluch
 * @see VulnerabilitySource
 * @see CheckRequest
 */
public class CheckResult {

	private static final CheckResult EMPTY = new CheckResult(Map.of());

	private final Map<PackageIdentity, Map<ArtifactVersion, Vulnerabilities>> vulnerabilities;

	private CheckResult(Map<PackageIdentity, Map<ArtifactVersion, Vulnerabilities>> vulnerabilities) {
		this.vulnerabilities = vulnerabilities;
	}

	/**
	 * Return the result for a request that checked nothing.
	 *
	 * @return the empty result.
	 */
	public static CheckResult empty() {
		return EMPTY;
	}

	/**
	 * Return a result over the given per-package vulnerabilities.
	 *
	 * <p>The package map is copied, while per-version maps are retained as the
	 * result state.
	 *
	 * @param vulnerabilities the vulnerabilities per version, per package and must
	 * hold only real results that are not {@link Vulnerabilities#isUnknown()
	 * absent}.
	 * @return the result; {@link #empty()} when the map is empty.
	 */
	public static CheckResult of(Map<PackageIdentity, Map<ArtifactVersion, Vulnerabilities>> vulnerabilities) {

		if (vulnerabilities.isEmpty()) {
			return EMPTY;
		}

		Map<PackageIdentity, Map<ArtifactVersion, Vulnerabilities>> copy = new LinkedHashMap<>(vulnerabilities);
		return new CheckResult(copy);
	}

	/**
	 * Return whether no package was checked.
	 *
	 * @return {@literal true} if nothing was checked; {@literal false} otherwise.
	 */
	public boolean isEmpty() {

		if (this.vulnerabilities.isEmpty()) {
			return true;
		}

		for (Map<ArtifactVersion, Vulnerabilities> value : this.vulnerabilities.values()) {
			for (Vulnerabilities vulnerabilities : value.values()) {
				if (!vulnerabilities.isUnknown()) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Return the vulnerabilities for the given package version.
	 *
	 * @param ecosystemPackage the package to look up.
	 * @param version the version to look up.
	 * @return the vulnerabilities a source returned for the version, or
	 * {@link Vulnerabilities#absent() absent} when no source returned it.
	 */
	public Vulnerabilities getVulnerabilities(PackageIdentity ecosystemPackage, ArtifactVersion version) {
		return vulnerabilities.getOrDefault(ecosystemPackage, Map.of()).getOrDefault(version, Vulnerabilities.absent());
	}

	/**
	 * Apply the given action to each checked package and its per-version
	 * vulnerabilities.
	 *
	 * @param consumer the action to apply to each package.
	 */
	public void forEach(BiConsumer<PackageIdentity, Map<ArtifactVersion, Vulnerabilities>> consumer) {
		vulnerabilities.forEach(consumer);
	}

	@Override
	public String toString() {
		return "CheckResult{" +
				"vulnerabilities=" + vulnerabilities +
				'}';
	}

}
