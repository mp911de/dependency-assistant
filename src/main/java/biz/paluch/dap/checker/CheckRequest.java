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
 * Exact package versions to evaluate in one vulnerability check.
 * <p>A request can span package systems. Built and filtered requests share the
 * supplied version lists.
 *
 * @author Mark Paluch
 * @see CheckResult
 */
public class CheckRequest {

	private final Map<PackageIdentity, List<ArtifactVersion>> packages;

	private final long timestamp = System.currentTimeMillis();

	private CheckRequest(Map<PackageIdentity, List<ArtifactVersion>> packages) {
		this.packages = packages;
	}

	public static Builder builder() {
		return new Builder();
	}

	public boolean isEmpty() {
		return packages.isEmpty();
	}

	public void forEach(BiConsumer<PackageIdentity, List<ArtifactVersion>> consumer) {
		packages.forEach(consumer);
	}

	/**
	 * Select packages by ecosystem, sharing their version lists.
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
	 * Return the request creation time in epoch milliseconds for scan-duration
	 * diagnostics.
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * Return the number of versions to check, across all packages.
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
		 * Replace the versions to check for a package. The supplied array is copied.
		 */
		public Builder add(PackageIdentity pkg, ArtifactVersion... versions) {
			return add(pkg, List.of(versions));
		}

		/**
		 * Replace the versions to check for a package.
		 * <p>The list is retained by the built request.
		 */
		public Builder add(PackageIdentity pkg, List<ArtifactVersion> versions) {
			packages.put(pkg, versions);
			return this;
		}

		/**
		 * Return the number of versions collected across all packages.
		 */
		public int size() {
			int size = 0;
			for (List<ArtifactVersion> value : packages.values()) {
				size += value.size();
			}
			return size;
		}

		/**
		 * Snapshot the package mappings. Version lists remain shared.
		 */
		public CheckRequest build() {
			return new CheckRequest(new LinkedHashMap<>(packages));
		}

	}

}
