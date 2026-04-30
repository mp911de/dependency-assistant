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

package biz.paluch.dap.github;

import biz.paluch.dap.artifact.Release;
import org.jspecify.annotations.Nullable;

/**
 * A GitHub tag release that pairs a domain {@link Release} with the full
 * 40-character SHA-1 commit hash of the tag target.
 *
 * <p>The SHA is {@code null} when the original cache entry predates SHA
 * storage, allowing deserialization without migration.
 *
 * @param release the domain release (version and optional date).
 * @param sha the full 40-character SHA-1 commit hash, or {@code null} if
 * unavailable.
 * @author Mark Paluch
 */
record GitRelease(Release release, @Nullable String sha) {

	/**
	 * Convert this entry to a {@link GitVersion}, attaching the SHA when available.
	 * @return the git version; never {@literal null}.
	 */
	GitVersion toGitVersion() {
		return sha != null ? GitVersion.of(sha, release.version()) : GitVersion.of(release.version());
	}

}
