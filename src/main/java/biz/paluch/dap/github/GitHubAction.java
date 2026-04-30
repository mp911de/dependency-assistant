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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * GitHub-specific extension to {@link ArtifactId}.
 * 
 * @author Mark Paluch
 */
interface GitHubAction extends ArtifactId {

	Pattern USES = Pattern.compile(
			"^(?<owner>[A-Za-z0-9-]{1,40})\\/" +
					"(?<repo>[A-Za-z0-9-]{0,40})" +
					"(?<paths>[/A-Za-z0-9._-]*)@" +
					"(?<version>\\S+)?\\s*(#(?<comment>[\\sA-Za-z0-9._-]+))?$");

	/**
	 * Return the version of the action.
	 */
	String version();

	/**
	 * Return whether {@code id} is a safe, well-formed {@code uses} reference.
	 */
	static boolean isValidUsage(@Nullable String uses) {
		return StringUtils.hasText(uses) && USES.matcher(uses).matches();
	}

	static GitHubAction of(String owner, String repository) {
		return new DefaultGitHubAction(owner, repository, "");
	}

	/**
	 * Parse a GitHub ArtifactId from a {@code uses:} value. Supported forms are:
	 * <pre class="code">
	 * {owner}/{repo}@{ref}
	 * {owner}/{repo}/{path}@{ref}
	 * </pre>
	 *
	 * @param uses the {@code uses} value.
	 * @return the parsed artifact id.
	 * @throws IllegalArgumentException if the value is not a valid {@code uses:}
	 * reference.
	 */
	public static GitHubAction from(String uses) {

		Matcher matcher = USES.matcher(uses);
		Assert.isTrue(matcher.matches(), "Invalid GitHub Action: %s".formatted(uses));

		return new DefaultGitHubAction(matcher.group(1), matcher.group(2), matcher.group(4));
	}

	record DefaultGitHubAction(String owner, String repository, String version) implements GitHubAction {

		@Override
		public String groupId() {
			return owner;
		}

		@Override
		public String artifactId() {
			return repository;
		}

		@Override
		public boolean equals(Object o) {

			if (o instanceof GitHubAction that) {
				return Objects.equals(owner, that.groupId()) && Objects.equals(version, that.version())
						&& Objects.equals(repository, that.artifactId());
			}

			if (o instanceof ArtifactId that) {
				return Objects.equals(owner, that.groupId())
						&& Objects.equals(repository, that.artifactId());
			}

			return false;
		}

		@Override
		public int hashCode() {
			return Objects.hash(owner, repository, version);
		}

		@Override
		public String toString() {
			return StringUtils.hasText(version) ? "%s/%s@%s".formatted(owner, repository, version)
					: "%s/%s".formatted(owner, repository);
		}

	}

}
