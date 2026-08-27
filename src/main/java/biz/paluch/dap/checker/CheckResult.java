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

	/**
	 * Return a builder collecting checked versions one at a time.
	 *
	 * @return a fresh builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Return a result containing no checked version.
	 *
	 * @return the empty result.
	 */
	public static CheckResult empty() {
		return EMPTY;
	}

	/**
	 * Return whether the result contains no known package-version result.
	 *
	 * @return {@literal true} if every entry is absent or no entry exists;
	 * {@literal false} otherwise.
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
	 * @param consumer the action to apply to each package and its version results.
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

	/**
	 * Builder that collects checked {@link Vulnerabilities} per package and version
	 * for a {@link CheckResult}.
	 *
	 * <p>Sources that discover results incrementally use the builder instead of
	 * assembling a nested map. Builders are not thread-safe.
	 */
	public static class Builder {

		private final Map<PackageIdentity, Map<ArtifactVersion, Vulnerabilities>> vulnerabilities = new LinkedHashMap<>();

		private Builder() {
		}

		/**
		 * Add the checked result for a package version.
		 *
		 * <p>A later call for the same package and version replaces the previous
		 * result. Add {@link Vulnerabilities#clean() clean} for a version that was
		 * checked without finding an advisory; omit versions the source could not
		 * answer for.
		 *
		 * @param ecosystemPackage the checked package.
		 * @param version the checked version.
		 * @param vulnerabilities the vulnerabilities found for the version.
		 * @return this builder.
		 */
		public Builder add(PackageIdentity ecosystemPackage, ArtifactVersion version, Vulnerabilities vulnerabilities) {
			this.vulnerabilities.computeIfAbsent(ecosystemPackage, key -> new LinkedHashMap<>()).put(version,
					vulnerabilities);
			return this;
		}

		/**
		 * Build a result from the currently collected versions.
		 *
		 * <p>Subsequent changes to the builder do not affect the returned result.
		 *
		 * @return a new result, or {@link #empty()} when no version was added.
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
