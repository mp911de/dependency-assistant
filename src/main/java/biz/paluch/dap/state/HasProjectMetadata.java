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

package biz.paluch.dap.state;

/**
 * Object that can provide {@link CachedMetadata dependency project metadata}.
 *
 * <p>A release source whose registry document also exposes project metadata
 * (such as the npm registry's {@code repository} and {@code bugs} fields)
 * implements this interface on its returned sequence so the cache-write path
 * can store the metadata without source-instance state.
 *
 * @author Mark Paluch
 * @see CachedMetadata
 * @see FetchedReleases#getProjectMetadata()
 */
public interface HasProjectMetadata {

	/**
	 * Return the project metadata captured during the fetch.
	 *
	 * @return the project metadata.
	 */
	CachedMetadata getProjectMetadata();

}
