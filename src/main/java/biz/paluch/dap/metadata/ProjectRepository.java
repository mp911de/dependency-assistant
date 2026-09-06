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

package biz.paluch.dap.metadata;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import biz.paluch.dap.artifact.TagSource;
import biz.paluch.dap.util.Sequence;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jspecify.annotations.Nullable;

/**
 * Access to an upstream repository on a supported {@link Platform}.
 * <p>Browsable URLs are derived without network access. Optional links are
 * absent when the platform cannot derive them reliably. Tag fetching may
 * require remote access.
 *
 * @author Mark Paluch
 */
public interface ProjectRepository {

	/**
	 * Return the browsable repository root URL.
	 */
	URI getUrl();

	/**
	 * Return the issue tracker, or {@literal null} if none can be derived reliably.
	 */
	@Nullable
	IssueTracker getIssueTracker();

	/**
	 * Return the releases URL, or the closest browsable listing on platforms
	 * without releases.
	 */
	URI getReleasesUrl();

	/**
	 * Return the release or tag page URL, or {@literal null} if unavailable.
	 * <p>The name is encoded as a single URL path segment.
	 * @param release the unencoded release or tag name.
	 */
	@Nullable
	URI getReleaseNotesUrl(String release);

	/**
	 * Encode a value for use as a single URL path segment.
	 * <p>Reserved characters such as {@code /}, {@code ?}, and {@code #} are
	 * encoded.
	 */
	static String encodePathSegment(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/**
	 * Fetch the repository's tag names.
	 * <p>The default returns an empty sequence without I/O.
	 * @param indicator the progress indicator used for cancellation.
	 * @throws IOException if remote tag retrieval fails.
	 */
	default Sequence<String> getTags(ProgressIndicator indicator) throws IOException {
		return Sequence.empty();
	}

	/**
	 * Return the tag source, or {@literal null} if tag fetching is unavailable.
	 */
	default @Nullable TagSource getTagSource() {
		return null;
	}


}
