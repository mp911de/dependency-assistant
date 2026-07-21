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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Strategy interface for the hosting platform behind a project repository URL.
 *
 * <p>A platform decides whether it recognizes a normalized
 * {@link RepositoryUrl} and creates the {@link ProjectRepository} handle that
 * renders the platform's browsable URLs. Detection is purely syntactic (host
 * name and path shape); no network probing is involved. A declared hint, the
 * Maven {@code issueManagement/system} value, can vouch for self-hosted
 * instances whose host name alone does not reveal the platform, such as GitHub
 * Enterprise or a self-hosted GitLab.
 *
 * <p>Implementations are registered through the {@code biz.paluch.dap.platform}
 * extension point and must be stateless. Detection order follows the extension
 * registration order; the static {@link #findConnection} and
 * {@link #findIssueTracker} entry points return the first match.
 *
 * @author Mark Paluch
 * @see RepositoryConnection
 * @see ProjectRepository
 * @see RepositoryUrl
 * @see IssueTracker
 */
public interface Platform {

	/**
	 * Extension point for platforms.
	 */
	ExtensionPointName<Platform> EP_NAME = ExtensionPointName.create("biz.paluch.dap.platform");

	/**
	 * Detect whether this platform hosts the given repository URL.
	 * @param repositoryUrl the normalized repository URL.
	 * @param hint a declared platform hint such as the Maven
	 * {@code issueManagement/system} value; can be {@literal null}.
	 * @return the connection, or {@literal null} if this platform does not
	 * recognize the URL.
	 */
	@Nullable
	RepositoryConnection detect(RepositoryUrl repositoryUrl, @Nullable String hint);

	/**
	 * Derive the issue tracker for a connection detected by this platform.
	 *
	 * @param repositoryConnection the detected repository connection.
	 * @param hint a declared platform hint such as the Maven
	 * {@code issueManagement/system} value; can be {@literal null}.
	 * @return the derived issue tracker, or {@literal null} if this platform cannot
	 * derive one for the connection.
	 */
	@Nullable
	IssueTracker detectIssueTracker(RepositoryConnection repositoryConnection, @Nullable String hint);

	/**
	 * Create the repository handle for a connection detected by this platform.
	 *
	 * @param project the project requesting the handle.
	 * @param connection the detected repository connection.
	 * @return the repository handle, or {@literal null} if the connection does not
	 * belong to this platform.
	 */
	@Nullable
	ProjectRepository createRepository(Project project, RepositoryConnection connection);

	/**
	 * Detect the hosting platform of a declared repository URL by asking all
	 * registered platforms. The URL is parsed once; the first platform that
	 * recognizes the parsed URL wins.
	 * @param url the declared repository URL.
	 * @param hint a declared platform hint such as the Maven
	 * {@code issueManagement/system} value; can be {@literal null}.
	 * @return the connection of the first recognizing platform, or {@literal null}
	 * if the value does not parse as a repository URL or no platform recognizes it.
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
	 * Derive the issue tracker for a detected connection by asking all registered
	 * platforms.
	 * @param repositoryConnection the detected repository connection.
	 * @param hint a declared platform hint such as the Maven
	 * {@code issueManagement/system} value; can be {@literal null}.
	 * @return the tracker of the first platform that derives one, or
	 * {@literal null} if no platform derives a tracker.
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
