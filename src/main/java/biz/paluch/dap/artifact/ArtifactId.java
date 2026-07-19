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

import java.util.Comparator;

/**
 * Artifact coordinates identified by {@code groupId} and {@code artifactId}.
 *
 * @author Mark Paluch
 */
public interface ArtifactId extends Comparable<ArtifactId> {

	/**
	 * Natural ordering by {@code groupId} then {@code artifactId}, consistent with
	 * {@link Object#equals(Object)}. This is the ordering used by
	 * {@link #compareTo(ArtifactId)} and therefore by
	 * {@code TreeMap}/{@code TreeSet} keys, so it compares both segments
	 * case-sensitively to match the case-sensitive identity contract.
	 */
	Comparator<? super ArtifactId> COMPARATOR = Comparator.comparing(ArtifactId::groupId)
			.thenComparing(ArtifactId::artifactId);

	/**
	 * Display ordering by {@code artifactId} then {@code groupId}. Unlike
	 * {@link #COMPARATOR} this is a presentation-only comparator, never the natural
	 * ordering or an identity key, so it orders {@code artifactId}
	 * case-insensitively for readable, alphabetized lists.
	 */
	Comparator<? super ArtifactId> BY_ARTIFACT_ID = Comparator
			.comparing(ArtifactId::artifactId, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(ArtifactId::groupId);

	/**
	 * Creates an {@link ArtifactId} from the given group id and artifact id.
	 */
	static ArtifactId of(String groupId, String artifactId) {
		return new DefaultArtifactId(groupId, artifactId);
	}

	/**
	 * Return the group id that namespaces the artifact (e.g. the Maven
	 * {@code groupId} or the first segment of Gradle coordinates).
	 * @return the group id; never {@literal null}.
	 */
	String groupId();

	/**
	 * Return the artifact id that names the artifact within its {@link #groupId()
	 * group}.
	 * @return the artifact id; never {@literal null}.
	 */
	String artifactId();

	@Override
	default int compareTo(ArtifactId o) {
		return ArtifactId.COMPARATOR.compare(this, o);
	}

	/**
	 * Create a detached {@link ArtifactId} that is not coupled to its underlying
	 * implementation.
	 */
	default ArtifactId detach() {
		return new DefaultArtifactId(groupId(), artifactId());
	}

}
