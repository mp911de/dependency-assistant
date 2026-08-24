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
 * Platform-bound handle for one upstream project repository, created by a
 * {@link Platform} from a supported repository URL.
 *
 * <p>The handle renders platform-specific browsable URLs for the repository,
 * its issue tracker, its releases list, and the release notes of a concrete
 * tag. URL rendering does not probe the network. Platforms can additionally
 * expose remote tag access through {@link #getTags(ProgressIndicator)} and
 * {@link #getTagSource()}.
 *
 * <p>Tag names used in release-note URLs are remote-supplied and are
 * percent-encoded via {@link #encodePathSegment} so they occupy exactly one URL
 * path segment.
 *
 * <p>Nullability expresses per-platform derivation confidence: a platform that
 * cannot derive a link reliably (for example the Bitbucket issue tracker, which
 * is frequently disabled per repository) returns {@literal null} rather than a
 * guessed URL.
 *
 * @author Mark Paluch
 * @see Platform
 */
public interface ProjectRepository {

	/**
	 * Return the browsable repository root URL.
	 *
	 * @return the https URL of the repository main page.
	 */
	URI getUrl();

	/**
	 * Return the issue tracker derived for this repository.
	 *
	 * @return the issue tracker, or {@literal null} if the platform cannot derive
	 * one reliably.
	 */
	@Nullable
	IssueTracker getIssueTracker();

	/**
	 * Return the URL of the repository's releases list.
	 *
	 * @return the releases-list URL (for platforms without a releases concept, the
	 * closest browsable listing).
	 */
	URI getReleasesUrl();

	/**
	 * Return the release-notes URL for a concrete release.
	 *
	 * @param release the release name, can be a tag name as reported by the
	 * platform.
	 * @return the release or tag page URL, or {@literal null} if this platform has
	 * no per-tag page.
	 */
	@Nullable
	URI getReleaseNotesUrl(String release);

	/**
	 * Percent-encode a remote-supplied value so it occupies exactly one URL path
	 * segment. Reserved characters including {@code /}, {@code ?}, and {@code #}
	 * are encoded, so the value cannot traverse the path or introduce a query or
	 * fragment component.
	 *
	 * @param value the raw value, typically a tag name reported by the platform.
	 * @return the encoded path segment.
	 */
	static String encodePathSegment(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/**
	 * Fetch the repository's tag names.
	 *
	 * <p>The default implementation performs no I/O and returns an empty sequence.
	 * Platform implementations with tag support can perform remote requests and use
	 * the indicator for cancellation.
	 *
	 * @param indicator the progress indicator used for cancellation.
	 * @return the repository tag names.
	 * @throws IOException if remote tag retrieval fails.
	 */
	default Sequence<String> getTags(ProgressIndicator indicator) throws IOException {
		return Sequence.empty();
	}

	/**
	 * Return the source used to fetch this repository's tags.
	 *
	 * @return the tag source, or {@literal null} if this platform does not provide
	 * tag fetching.
	 */
	default @Nullable TagSource getTagSource() {
		return null;
	}


}
