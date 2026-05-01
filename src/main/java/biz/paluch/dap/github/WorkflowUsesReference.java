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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Parsed representation of a GitHub Actions {@code uses:} scalar value.
 *
 * <p>A {@code uses:} declaration has the form {@code owner/repository[@ref]} or
 * {@code owner/repository/optional/path@ref}. This record captures each segment
 * individually.
 *
 * @param owner the GitHub repository owner (user or organization).
 * @param repository the GitHub repository name.
 * @param rawVersion the literal ref string as written in the file (e.g.
 * {@code v1.2.3} or a full SHA). Can be {@literal null} if the the version is
 * not set.
 * @author Mark Paluch
 */
record WorkflowUsesReference(String owner, String repository, @Nullable String rawVersion) {

	/**
	 * Return the artifact id for this reference using the {@code <owner>} /
	 * {@code <repository>} convention.
	 */
	ArtifactId toArtifactId() {
		return GitHubAction.of(owner, repository);
	}

	/**
	 * Format a {@link GitVersion} matching the {@link #rawVersion()} style.
	 * @param version the version to format.
	 * @return the formatted version.
	 */
	public VersionText getVersion(GitVersion version) {

		WorkflowRefStyle style = WorkflowRefStyle.from(rawVersion());
		String text = version.getVersion().toString();

		if (style == WorkflowRefStyle.SHA && StringUtils.hasText(version.getSha())) {

			text = version.getSha();
			if (StringUtils.hasText(rawVersion())) {
				int shaLength = rawVersion().length();
				if (shaLength < text.length()) {
					text = text.substring(0, shaLength);
				}
			}

			return new VersionText(text, version.toString());
		}

		if (hasVPrefix(rawVersion()) && !hasVPrefix(text)) {
			text = "v" + text;
		}

		return new VersionText(text, "");
	}

	private static boolean hasVPrefix(@Nullable String ref) {
		return ref != null && ref.length() >= 2
				&& (ref.charAt(0) == 'v' || ref.charAt(0) == 'V')
				&& Character.isDigit(ref.charAt(1));
	}


	public VersionSource toVersionSource() {
		return StringUtils.hasText(rawVersion()) ? VersionSource.declared(rawVersion()) : VersionSource.none();
	}

	record VersionText(String text, String comment) {

		@Override
		public String toString() {
			return StringUtils.hasText(comment) ? "%s # %s".formatted(text, comment) : text;
		}

	}

}
