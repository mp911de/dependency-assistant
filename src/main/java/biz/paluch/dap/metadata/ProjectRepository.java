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
 * <p>The handle is pure URL knowledge: it renders the platform-specific
 * browsable URLs for the repository itself, its issue tracker, its releases
 * list, and the release notes of a concrete tag. Rendering never probes the
 * network; fetching, caching, and version-to-tag matching live outside this
 * handle. Tag names are remote-supplied and are percent-encoded via
 * {@link #encodePathSegment} so they occupy exactly one URL path segment.
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
	 * @return the https URL of the repository main page; guaranteed to be not
	 * {@literal null}.
	 */
	URI getUrl();

	@Nullable
	IssueTracker getIssueTracker();

	/**
	 * Return the URL of the releases list, the fallback target when no concrete tag
	 * is known for a version.
	 * @return the releases-list URL (for platforms without a releases concept, the
	 * closest browsable listing); guaranteed to be not {@literal null}.
	 */
	URI getReleasesUrl();

	/**
	 * Return the release-notes URL for a concrete release.
	 * @param release the release name, can be a tag name as reported by the
	 * platform
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
	 * @param value the raw value, typically a tag name reported by the platform.
	 * @return the encoded path segment; guaranteed to be not {@literal null}.
	 */
	static String encodePathSegment(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	default Sequence<String> getTags(ProgressIndicator indicator) throws IOException {
		return Sequence.empty();
	}

	/**
	 * Return the release source used to fetch this repository's tags and releases.
	 * @return the release source, or {@literal null} if this platform does not
	 * provide tag fetching.
	 */
	default @Nullable TagSource getTagSource() {
		return null;
	}


}
