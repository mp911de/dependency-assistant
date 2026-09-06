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

package biz.paluch.dap.github;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.HasArtifactId;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Repository action referenced by a workflow {@code uses:} declaration.
 * <p>Identity is {@code owner/repository}. Action subdirectories do not affect
 * identity because tags and commits belong to the repository.
 *
 * @author Mark Paluch
 * @see UsesRepositoryAction
 */
interface GitHubAction extends HasArtifactId {

	/**
	 * Recognizes repository actions. Local actions and Docker references do not use
	 * GitHub release metadata.
	 */
	Pattern USES = Pattern.compile(
			"^(?<owner>[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38})\\/" +
					"(?<repo>(?!\\.\\.?[/@])[A-Za-z0-9._-]{1,100})" +
					"(?<paths>[/A-Za-z0-9._-]*)@" +
					"(?<version>\\S*)\\s*(#(?<comment>[\\sA-Za-z0-9._-]+))?$");

	String owner();

	String repository();

	/**
	 * Return the raw ref after {@code @}, without normalization.
	 */
	String version();

	/**
	 * Return whether the value is a supported repository action.
	 * <p>Unsupported values may still be valid workflow entries.
	 */
	static boolean isValidUsage(@Nullable String uses) {
		return StringUtils.hasText(uses) && USES.matcher(uses).matches();
	}

	/**
	 * Create a repository action without a selected ref.
	 */
	static UsesRepositoryAction of(String owner, String repository) {
		return new UsesRepositoryAction(ArtifactId.of(owner, repository), "");
	}

	/**
	 * Parse a repository action, ignoring action subdirectories and trailing
	 * comments.
	 * @throws IllegalArgumentException if the value is not a supported repository
	 * action.
	 */
	public static UsesRepositoryAction from(String uses) {

		Matcher matcher = USES.matcher(uses);
		Assert.isTrue(matcher.matches(), "Invalid GitHub Action: %s".formatted(uses));

		return new UsesRepositoryAction(ArtifactId.of(matcher.group(1), matcher.group(2)), matcher.group(4));
	}

}
