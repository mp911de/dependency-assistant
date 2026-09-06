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

/**
 * How artifact versions are formed and compared.
 * <p>Semantic comparison requires a shared non-opaque scheme. Numeric versions
 * include both semantic and calendar forms. {@link Releases} owns ordering
 * across schemes in an artifact history.
 *
 * @author Mark Paluch
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

	/**
	 * Provide a fallback ordering with release trains before numeric versions.
	 * <p>This is not evidence of a scheme migration.
	 * @return zero for equal schemes or when either scheme is opaque.
	 */
	public static int compareEra(VersioningScheme left, VersioningScheme right) {

		if (left == right || left == OPAQUE || right == OPAQUE) {
			return 0;
		}
		return left == RELEASE_TRAIN ? -1 : 1;
	}

}
