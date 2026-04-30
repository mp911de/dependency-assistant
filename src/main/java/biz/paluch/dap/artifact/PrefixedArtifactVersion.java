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

import java.util.Objects;

/**
 * {@link ArtifactVersion} that wraps an inner version with a string prefix such
 * as {@code v}.
 *
 * <p>The prefix is preserved in {@link #toString()} so that the original
 * version string round-trips correctly (e.g. {@code v1.2.3}).
 *
 * @author Mark Paluch
 * @see ArtifactVersion#isWrapped()
 */
class PrefixedArtifactVersion extends ArtifactVersionWrapper implements ArtifactVersion {

	private final String prefix;

	PrefixedArtifactVersion(String prefix, ArtifactVersion delegate) {
		super(delegate);
		this.prefix = prefix;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof PrefixedArtifactVersion other)) {
			return false;
		}
		return prefix.equals(other.prefix) && getVersion().equals(other.getVersion());
	}

	@Override
	public int hashCode() {
		return Objects.hash(prefix, getVersion());
	}

	@Override
	public String toString() {
		return prefix + getVersion();
	}

}
