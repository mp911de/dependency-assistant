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
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;

/**
 * Bulk vulnerability-check request mapping each {@link PackageIdentity} to the
 * exact {@link ArtifactVersion versions} to evaluate.
 *
 * <p>A request can span multiple artifacts and package systems so a single
 * {@link VulnerabilitySource#check(com.intellij.openapi.project.Project, CheckRequest)
 * check} can process the whole batch. Create it through {@link #builder()}. The
 * builder snapshots the package mappings when {@link Builder#build()} is called
 * but retains the supplied version lists.
 *
 * @author Mark Paluch
 * @see VulnerabilitySource
 * @see CheckResult
 */
public class CheckRequest {

	private final Map<PackageIdentity, List<ArtifactVersion>> packages;

	private final long timestamp = System.currentTimeMillis();

	private CheckRequest(Map<PackageIdentity, List<ArtifactVersion>> packages) {
		this.packages = packages;
	}

	/**
	 * Return a builder for a new request.
	 *
	 * @return a fresh builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Return whether the request contains no package mapping.
	 *
	 * @return {@literal true} if no package is mapped; {@literal false} otherwise.
	 */
	public boolean isEmpty() {
		return packages.isEmpty();
	}

	/**
	 * Apply the given action to each package and its versions to check.
	 *
	 * @param consumer the action to apply to each package and version list.
	 */
	public void forEach(BiConsumer<PackageIdentity, List<ArtifactVersion>> consumer) {
		packages.forEach(consumer);
	}

	/**
	 * Return the subset of this request whose package system the given predicate
	 * accepts.
	 *
	 * <p>The returned request shares its version lists with this request.
	 *
	 * @param supported tests whether a package system is supported.
	 * @return a request holding only the accepted packages.
	 */
	public CheckRequest filter(Predicate<PackageSystem> supported) {

		Map<PackageIdentity, List<ArtifactVersion>> filtered = new LinkedHashMap<>(packages.size());
		packages.forEach((identity, versions) -> {
			if (supported.test(identity.getPackageSystem())) {
				filtered.put(identity, versions);
			}
		});
		return new CheckRequest(filtered);
	}

	/**
	 * Return the request creation time used for scan-duration diagnostics.
	 *
	 * @return the creation time in epoch milliseconds.
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * Return the total number of versions in this request.
	 *
	 * @return the number of mapped versions.
	 */
	public int size() {
		int size = 0;
		for (List<ArtifactVersion> value : packages.values()) {
			size += value.size();
		}
		return size;
	}

	@Override
	public String toString() {
		return "To scan: " + size() + ": " + packages.keySet();
	}

	/**
	 * Builder that collects package versions for a {@link CheckRequest}.
	 */
	public static class Builder {

		private final Map<PackageIdentity, List<ArtifactVersion>> packages = new LinkedHashMap<>();

		private Builder() {
		}

		/**
		 * Add the versions to check for a package.
		 *
		 * <p>A later call for the same package replaces its previous version list. The
		 * supplied list is retained by the built request.
		 *
		 * @param pkg the package to check.
		 * @param versions the exact versions to check.
		 * @return this builder.
		 */
		public Builder add(PackageIdentity pkg, List<ArtifactVersion> versions) {
			packages.put(pkg, versions);
			return this;
		}

		/**
		 * Return the total number of versions currently collected.
		 *
		 * @return the number of collected versions.
		 */
		public int size() {
			int size = 0;
			for (List<ArtifactVersion> value : packages.values()) {
				size += value.size();
			}
			return size;
		}

		/**
		 * Build a request from the current package mappings.
		 *
		 * <p>Subsequent changes to the builder's mappings do not affect the request.
		 * The version lists themselves remain shared.
		 *
		 * @return a new request.
		 */
		public CheckRequest build() {
			return new CheckRequest(new LinkedHashMap<>(packages));
		}

	}

}
