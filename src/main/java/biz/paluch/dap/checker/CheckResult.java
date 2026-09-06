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

package biz.paluch.dap.checker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;

/**
 * Outcome of a bulk {@link CheckRequest}, containing the checked
 * {@link Vulnerabilities} per package and version.
 *
 * <p>Only versions for which a source returned a known result are considered
 * checked. A missing package or version reads as
 * {@link Vulnerabilities#absent() absent}, which is distinct from an explicitly
 * clean result.
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

	public static Builder builder() {
		return new Builder();
	}

	public static CheckResult empty() {
		return EMPTY;
	}

	/**
	 * Return whether no known package-version result exists.
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
	 * Return the checked result, or {@link Vulnerabilities#absent()} if no source
	 * answered.
	 */
	public Vulnerabilities getVulnerabilities(PackageIdentity ecosystemPackage, ArtifactVersion version) {
		return vulnerabilities.getOrDefault(ecosystemPackage, Map.of()).getOrDefault(version, Vulnerabilities.absent());
	}

	public void forEach(BiConsumer<PackageIdentity, Map<ArtifactVersion, Vulnerabilities>> consumer) {
		vulnerabilities.forEach(consumer);
	}

	@Override
	public String toString() {
		return "CheckResult{" +
				"vulnerabilities=" + vulnerabilities +
				'}';
	}

	/**
	 * Collects per-version results. Not thread-safe.
	 */
	public static class Builder {

		private final Map<PackageIdentity, Map<ArtifactVersion, Vulnerabilities>> vulnerabilities = new LinkedHashMap<>();

		private Builder() {
		}

		/**
		 * Replace the result for a package version.
		 * <p>Record {@link Vulnerabilities#clean()} when checked without advisories.
		 * Omit versions the source could not answer.
		 */
		public Builder add(PackageIdentity ecosystemPackage, ArtifactVersion version, Vulnerabilities vulnerabilities) {
			this.vulnerabilities.computeIfAbsent(ecosystemPackage, key -> new LinkedHashMap<>()).put(version,
					vulnerabilities);
			return this;
		}

		/**
		 * Snapshot the collected results independently of later builder changes.
		 */
		public CheckResult build() {

			if (vulnerabilities.isEmpty()) {
				return EMPTY;
			}

			Map<PackageIdentity, Map<ArtifactVersion, Vulnerabilities>> copy = new LinkedHashMap<>(
					vulnerabilities.size());
			vulnerabilities.forEach((ecosystemPackage, byVersion) -> copy.put(ecosystemPackage,
					new LinkedHashMap<>(byVersion)));
			return new CheckResult(copy);
		}

	}

}
