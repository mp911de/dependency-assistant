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
 * GitHub repository action referenced from a workflow {@code uses:}
 * declaration.
 *
 * <p>The dependency identity of a GitHub action is the repository that
 * publishes it: {@code owner/repository}. Any workflow-local action path is
 * deliberately ignored for identity and release lookup, since tags and SHAs
 * belong to the Git repository rather than to a subdirectory inside it.
 *
 * <p>The {@linkplain #version() version} is the raw ref after {@code @}. It is
 * retained as workflow metadata so callers can resolve, compare, and rewrite
 * the declaration without conflating repository identity with the selected ref.
 *
 * @author Mark Paluch
 * @see UsesRepositoryAction
 */
interface GitHubAction extends ArtifactId {

	/**
	 * Pattern for repository-backed GitHub Action {@code uses:} values.
	 * <p>Local actions and Docker image references are intentionally excluded from
	 * this contract because they are not resolved through GitHub repository release
	 * metadata.
	 */
	Pattern USES = Pattern.compile(
			"^(?<owner>[A-Za-z0-9-]{1,40})\\/" +
					"(?<repo>[A-Za-z0-9-]{1,40})" +
					"(?<paths>[/A-Za-z0-9._-]*)@" +
					"(?<version>\\S+)?\\s*(#(?<comment>[\\sA-Za-z0-9._-]+))?$");

	/**
	 * Return the raw workflow ref for this action.
	 * <p>The ref may represent a tag, branch, semantic version, or commit SHA and
	 * is not normalized by this abstraction.
	 */
	String version();

	/**
	 * Determine whether the given value is a repository-backed GitHub Action
	 * {@code uses:} declaration.
	 * <p>A {@code false} result does not mean the workflow entry is invalid YAML;
	 * it means the entry is outside the dependency model handled here.
	 * @param uses the workflow value to inspect
	 * @return {@code true} if the value can be represented as a
	 * {@code GitHubAction}
	 */
	static boolean isValidUsage(@Nullable String uses) {
		return StringUtils.hasText(uses) && USES.matcher(uses).matches();
	}

	/**
	 * Return a repository action identity without a selected ref.
	 * @param owner the GitHub repository owner
	 * @param repository the GitHub repository name
	 * @return the repository action identity
	 */
	static GitHubAction of(String owner, String repository) {
		return new DefaultGitHubAction(owner, repository, "");
	}

	/**
	 * Parse a repository-backed GitHub Action from a workflow {@code uses:} value.
	 * <p>The returned action keeps the repository owner, repository name, and raw
	 * ref. Path segments after the repository name and trailing workflow comments
	 * are accepted by the parser but are not part of the dependency identity.
	 *
	 * @param uses the workflow {@code uses:} value
	 * @return the parsed action
	 * @throws IllegalArgumentException if the value is not a valid {@code uses:}
	 * reference for this dependency model
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
			return Objects.hash(owner, repository);
		}

		@Override
		public String toString() {
			return StringUtils.hasText(version) ? "%s/%s@%s".formatted(owner, repository, version)
					: "%s/%s".formatted(owner, repository);
		}

	}

}
