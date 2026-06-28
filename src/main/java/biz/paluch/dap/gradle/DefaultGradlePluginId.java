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

package biz.paluch.dap.gradle;

import java.util.Objects;

import biz.paluch.dap.artifact.ArtifactId;

/**
 * Default {@link GradlePluginId} implementation.
 *
 * @author Mark Paluch
 */
class DefaultGradlePluginId implements GradlePluginId {

	private final ArtifactId id;

	public DefaultGradlePluginId(ArtifactId id) {
		this.id = id;
	}

	@Override
	public String groupId() {
		return id.groupId();
	}

	@Override
	public String artifactId() {
		return id.artifactId();
	}

	@Override
	public int compareTo(ArtifactId o) {
		return id.compareTo(o);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ArtifactId that) {
			return id.equals(that);
		}
		if (this == o) {
			return true;
		}
		if ((!(o instanceof ArtifactId other))) {
			return false;
		}
		return Objects.equals(id, other);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		return id.toString();
	}

}
