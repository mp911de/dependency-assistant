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

import org.springframework.util.ObjectUtils;

/**
 * Default artifact coordinates (groupId + artifactId).
 *
 * @author Mark Paluch
 */
record DefaultArtifactId(String groupId, String artifactId) implements ArtifactId {

	@Override
	public boolean equals(Object o) {
		if (o instanceof ArtifactId that) {
			return groupId.equals(that.groupId()) && artifactId.equals(that.artifactId());
		}
		if (!(o instanceof DefaultArtifactId that)) {
			return false;
		}
		if (!ObjectUtils.nullSafeEquals(groupId, that.groupId)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(artifactId, that.artifactId);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHash(groupId, artifactId);
	}

	@Override
	public String toString() {
		return groupId.equals(artifactId) ? groupId : groupId + ":" + artifactId;
	}

}
