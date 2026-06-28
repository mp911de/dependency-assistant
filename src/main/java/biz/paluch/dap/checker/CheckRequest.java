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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;

/**
 * A bulk request to check several {@link PackageIdentity packages} for
 * vulnerabilities in one pass, each mapped to the exact versions to evaluate.
 *
 * <p>A request can span multiple artifacts and ecosystems so a single
 * {@link VulnerabilitySource#check(com.intellij.openapi.project.Project, CheckRequest)
 * check} resolves the whole batch. Build it through {@link #builder()}.
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
	 * Return whether the request carries no package to check.
	 *
	 * @return {@literal true} if there is nothing to check; {@literal false}
	 * otherwise.
	 */
	public boolean isEmpty() {
		return packages.isEmpty();
	}

	/**
	 * Apply the given action to each package and its versions to check.
	 *
	 * @param consumer the action to apply to each package; must not be
	 * {@literal null}.
	 */
	public void forEach(BiConsumer<PackageIdentity, List<ArtifactVersion>> consumer) {
		packages.forEach(consumer);
	}

	/**
	 * Return the subset of this request whose ecosystem the given predicate
	 * accepts, used to scope a request to the ecosystems a single source supports.
	 *
	 * @param supported tests whether a package's ecosystem is supported; must not
	 * be {@literal null}.
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

	public long getTimestamp() {
		return timestamp;
	}

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
	 * Builder collecting packages and their versions into a {@link CheckRequest}.
	 */
	public static class Builder {

		private final Map<PackageIdentity, List<ArtifactVersion>> packages = new LinkedHashMap<>();

		private Builder() {
		}

		/**
		 * Add an artifact and the versions to check for it.
		 *
		 * @param artifactId the artifact coordinate.
		 * @param packageSystem the ecosystem the artifact belongs to; must not be
		 * {@literal null}.
		 * @param versions the exact versions to check.
		 * @return this builder.
		 */
		public Builder add(ArtifactId artifactId, PackageSystem packageSystem, List<ArtifactVersion> versions) {
			packages.put(PackageIdentity.of(artifactId, packageSystem), versions);
			return this;
		}

		public int size() {
			int size = 0;
			for (List<ArtifactVersion> value : packages.values()) {
				size += value.size();
			}
			return size;
		}

		/**
		 * Build the request.
		 *
		 * @return the request.
		 */
		public CheckRequest build() {
			return new CheckRequest(new LinkedHashMap<>(packages));
		}

	}

}
