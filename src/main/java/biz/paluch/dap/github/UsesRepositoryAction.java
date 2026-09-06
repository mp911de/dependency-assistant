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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.RefStyle;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Repository action with its raw ref retained for style-preserving updates.
 *
 * @author Mark Paluch
 * @param version the ref after {@code @}, or {@literal null} if absent.
 * @see GitHubAction
 */
record UsesRepositoryAction(ArtifactId artifactId, @Nullable String version) implements GitHubAction, HasArtifactId {

	public UsesRepositoryAction(GitHubAction action) {
		this(action.getArtifactId(), action.version());
	}

	@Override
	public String owner() {
		return artifactId.groupId();
	}

	@Override
	public String repository() {
		return artifactId.artifactId();
	}

	@Override
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	/**
	 * Return the declared version source, or an absent source if the ref is
	 * missing.
	 */
	public VersionSource toVersionSource() {
		return VersionSource.from(version());
	}

	/**
	 * Render an update in the declared ref style.
	 * <p>SHA-pinned refs include a version comment. If the release has no SHA, the
	 * update falls back to its version text.
	 */
	public VersionText getVersion(GitVersion gitVersion) {

		RefStyle style = RefStyle.from(version());
		String text = gitVersion.renderRef(style, version());

		if (style == RefStyle.SHA && StringUtils.hasText(gitVersion.getSha())) {
			return new VersionText(text, gitVersion.toString());
		}

		return new VersionText(text, "");
	}

	public RefStyle getStyle() {
		return RefStyle.from(version());
	}

	/**
	 * Ref text and an optional managed version comment.
	 * @param comment an empty string when no comment should be written.
	 */
	record VersionText(String text, String comment) {

		/**
		 * Pin to the full commit SHA with the version as a managed comment.
		 * @throws IllegalStateException if the release has no SHA.
		 */
		static VersionText create(GitVersion gitVersion) {
			return new VersionText(gitVersion.getRequiredSha(), gitVersion.toString());
		}

		boolean hasComment() {
			return StringUtils.hasText(comment);
		}

		@Override
		public String toString() {
			return StringUtils.hasText(comment) ? "%s # %s".formatted(text, comment) : text;
		}

	}

}
