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
 * Detected link between a declared repository URL and its hosting
 * {@link Platform}.
 *
 * <p>A connection is the result of platform detection: it identifies the
 * repository through its canonical {@link #getKey() key} and browsable
 * {@link #getUrl() URL} and creates the {@link ProjectRepository} handle on
 * demand. Connections are cheap, immutable detection results; they hold no
 * network resources and involve no network access.
 *
 * @author Mark Paluch
 * @see Platform#findConnection
 * @see ProjectRepository
 */
public interface RepositoryConnection {

	/**
	 * Return the canonical repository key used to address cached repository
	 * entries, typically in {@code host/owner/repository} form.
	 */
	String getKey();

	/**
	 * Return the browsable https URL of the repository main page in its
	 * platform-canonical form, independent of how the repository URL was declared.
	 */
	String getUrl();

	/**
	 * Create the repository handle for this connection through the detecting
	 * platform.
	 * @param project the project requesting the handle.
	 * @return the repository handle, or {@literal null} if the platform cannot
	 * create one.
	 */
	default @Nullable ProjectRepository createRepository(Project project) {
		return null;
	}

}
