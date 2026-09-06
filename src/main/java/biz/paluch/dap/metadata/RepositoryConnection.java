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

import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * A repository supported by a {@link Platform}.
 * <p>Connections are immutable detection results with no network resources. Use
 * {@link #createRepository(Project)} to obtain a repository handle.
 *
 * @author Mark Paluch
 * @see Platform#findConnection
 */
public interface RepositoryConnection {

	/**
	 * Return the canonical cache key, typically {@code host/owner/repository}.
	 */
	String getKey();

	/**
	 * Return the canonical browsable HTTPS URL, independent of how the repository
	 * URL was declared.
	 */
	String getUrl();

	/**
	 * Create a repository handle, or return {@literal null} if unavailable.
	 */
	default @Nullable ProjectRepository createRepository(Project project) {
		return null;
	}

}
