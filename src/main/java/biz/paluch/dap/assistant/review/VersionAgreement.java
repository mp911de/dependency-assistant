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

package biz.paluch.dap.assistant.review;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactVersion;
import org.jspecify.annotations.Nullable;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * The largest subset of a candidate bucket agreeing on one effective current
 * version, paired with that version.
 *
 * <p>A drifting candidate agrees with every version one of its occurrences is
 * declared at.
 *
 * @author Mark Paluch
 */
record VersionAgreement(ArtifactVersion version, List<TableRow> members) {

	/**
	 * Select the largest version-agreeing subset within the bucket, tie-breaking
	 * equal sizes to the higher version.
	 *
	 * @param bucket the candidates to select from.
	 * @return the agreement, or {@literal null} when the bucket declares no
	 * version.
	 */
	static @Nullable VersionAgreement select(List<TableRow> bucket) {

		MultiValueMap<ArtifactVersion, TableRow> byVersion = new LinkedMultiValueMap<>();
		for (TableRow candidate : bucket) {
			for (ArtifactVersion version : candidate.getDeclaredVersions()) {
				byVersion.add(version, candidate);
			}
		}

		List<VersionAgreement> agreements = byVersion.entrySet().stream()
				.map(entry -> new VersionAgreement(entry.getKey(), entry.getValue()))
				.toList();

		VersionAgreement selected = null;
		for (VersionAgreement agreement : agreements) {
			if (selected == null || agreement.isBetterFit(selected)) {
				selected = agreement;
			}
		}

		return selected;
	}

	/**
	 * Return whether this agreement is a better fit than the given one: it carries
	 * more agreeing members, or equally many at a higher version.
	 *
	 * @param other the agreement to compare against.
	 * @return {@code true} if this agreement supersedes {@code other};
	 * {@code false} otherwise.
	 */
	boolean isBetterFit(VersionAgreement other) {

		if (size() != other.size()) {
			return size() > other.size();
		}
		return version.isNewer(other.version());
	}

	int size() {
		return members.size();
	}

}
