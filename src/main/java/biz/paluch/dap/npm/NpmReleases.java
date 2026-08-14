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

package biz.paluch.dap.npm;

import java.util.Iterator;
import java.util.List;

import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.state.CachedMetadata;
import biz.paluch.dap.state.HasProjectMetadata;
import biz.paluch.dap.util.Sequence;

/**
 * Releases parsed from an npm packument, carrying the {@link CachedMetadata}
 * captured from the document's {@code repository} and {@code bugs} fields.
 *
 * <p>The metadata rides along with the fetch result so the cache-write path can
 * store it without any source-instance state; {@literal null} metadata means
 * the document carried no inspectable version listing.
 *
 * @author Mark Paluch
 * @see NpmRegistry
 * @see HasProjectMetadata
 */
class NpmReleases implements Sequence<Release>, HasProjectMetadata {

	private final List<Release> releases;

	private final CachedMetadata projectMetadata;

	NpmReleases(List<Release> releases, CachedMetadata projectMetadata) {
		this.releases = releases;
		this.projectMetadata = projectMetadata;
	}

	@Override
	public Iterator<Release> iterator() {
		return releases.iterator();
	}

	@Override
	public boolean isEmpty() {
		return releases.isEmpty();
	}

	@Override
	public List<Release> toList() {
		return releases;
	}

	@Override
	public CachedMetadata getProjectMetadata() {
		return projectMetadata;
	}

	@Override
	public String toString() {
		return "NpmReleases[releases=%d, projectMetadata=%s]".formatted(releases.size(), projectMetadata);
	}

}
