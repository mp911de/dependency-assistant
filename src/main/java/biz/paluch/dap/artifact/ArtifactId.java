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

import java.util.Comparator;

/**
 * Artifact coordinates identified by a {@code groupId} and an
 * {@code artifactId}.
 *
 * <p>The two segments form the declared dependency identity. Git-backed
 * artifacts may carry separate release-routing metadata in
 * {@link GitArtifactId} while exposing these declared coordinates through this
 * interface.
 *
 * @author Mark Paluch
 */
public interface ArtifactId extends Comparable<ArtifactId> {

	/**
	 * Natural ordering by group id, then artifact id, both case-sensitive. Routing
	 * metadata does not participate.
	 */
	Comparator<? super ArtifactId> COMPARATOR = Comparator.comparing(ArtifactId::groupId)
			.thenComparing(ArtifactId::artifactId);

	/**
	 * Display ordering by case-insensitive artifact id, then group id. Use
	 * {@link #COMPARATOR} for identity keys.
	 */
	Comparator<? super ArtifactId> BY_ARTIFACT_ID = Comparator
			.comparing(ArtifactId::artifactId, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(ArtifactId::groupId);

	static ArtifactId of(String groupId, String artifactId) {
		return new DefaultArtifactId(groupId, artifactId);
	}

	String groupId();

	String artifactId();

	@Override
	default int compareTo(ArtifactId o) {
		return ArtifactId.COMPARATOR.compare(this, o);
	}

	/**
	 * Return detached coordinates, discarding implementation-specific metadata.
	 */
	default ArtifactId detach() {
		return new DefaultArtifactId(groupId(), artifactId());
	}

}
