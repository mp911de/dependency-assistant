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

package biz.paluch.dap.artifact;

/**
 * The system by which an artifact's versions are formed and ordered.
 *
 * <p>Scheme equality is the comparability rule: two {@link ArtifactVersion}s
 * can be ordered relative to each other only when they share a scheme and the
 * scheme is not {@link #OPAQUE}. The scheme classifies version <em>shape</em>;
 * {@link #NUMERIC} deliberately treats semantic and calendar versions as one
 * comparable family.
 *
 * @author Mark Paluch
 * @see ArtifactVersion#scheme()
 * @see ArtifactVersion#canCompare(ArtifactVersion)
 */
public enum VersioningScheme {

	/**
	 * Semantic or calver numbers, e.g. {@code 1.4.7} or {@code 2025.0.6}.
	 */
	NUMERIC,

	/**
	 * Named release train plus suffix, e.g. {@code Bismuth-SR1}.
	 */
	RELEASE_TRAIN,

	/**
	 * Unresolved or opaque refs (branches, SHAs) that cannot participate in version
	 * ordering. Comparable to nothing, not even another opaque ref.
	 */
	OPAQUE;

}
