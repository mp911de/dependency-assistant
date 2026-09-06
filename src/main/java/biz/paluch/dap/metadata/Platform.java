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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Strategy interface for repository hosts and creating platform-specific
 * handles.
 * <p>Detection uses the URL and an optional platform hint without network
 * access. A hint, such as Maven's {@code issueManagement/system}, can identify
 * a self-hosted instance. A {@literal null} hint means none was declared.
 * <p>Implementations must be stateless and register through the
 * {@code biz.paluch.dap.platform} extension point. The static lookup methods
 * use the first match in extension registration order.
 *
 * @author Mark Paluch
 */
public interface Platform {

	ExtensionPointName<Platform> EP_NAME = ExtensionPointName.create("biz.paluch.dap.platform");

	/**
	 * Detect whether this platform hosts the given repository URL.
	 * @return the connection, or {@literal null} if the URL is not supported.
	 */
	@Nullable
	RepositoryConnection detect(RepositoryUrl repositoryUrl, @Nullable String hint);

	/**
	 * Derive an issue tracker from a detected connection and optional platform
	 * hint.
	 * @return the tracker, or {@literal null} if none can be derived.
	 */
	@Nullable
	IssueTracker detectIssueTracker(RepositoryConnection repositoryConnection, @Nullable String hint);

	/**
	 * Create a repository handle for a connection detected by this platform.
	 * @return the handle, or {@literal null} if the platform cannot create one.
	 */
	@Nullable
	ProjectRepository createRepository(Project project, RepositoryConnection connection);

	/**
	 * Find a connection for a declared repository URL.
	 * @return the connection, or {@literal null} if the URL cannot be parsed or no
	 * platform supports it.
	 */
	static @Nullable RepositoryConnection findConnection(String url, @Nullable String hint) {

		RepositoryUrl repositoryUrl = RepositoryUrl.parse(url);
		if (repositoryUrl == null) {
			return null;
		}

		for (Platform platform : EP_NAME.getExtensionList()) {
			RepositoryConnection connection = platform.detect(repositoryUrl, hint);
			if (connection != null) {
				return connection;
			}
		}
		return null;
	}

	/**
	 * Find an issue tracker for a detected connection.
	 * @return the tracker, or {@literal null} if no platform can derive one.
	 */
	static @Nullable IssueTracker findIssueTracker(RepositoryConnection repositoryConnection, @Nullable String hint) {
		for (Platform platform : EP_NAME.getExtensionList()) {
			IssueTracker tracker = platform.detectIssueTracker(repositoryConnection, hint);
			if (tracker != null) {
				return tracker;
			}
		}
		return null;
	}

}
