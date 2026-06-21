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
import biz.paluch.dap.artifact.RefStyle;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Repository-backed GitHub Actions dependency declared by a workflow
 * {@code uses:} value.
 *
 * <p>This record is the boundary between YAML syntax and dependency metadata.
 * It retains the repository coordinates that identify the action release source
 * and the raw ref that determines how updates should be rendered back into the
 * source file.
 *
 * <p>Any path segment after the repository name is intentionally not part of
 * this contract. GitHub action releases are resolved at repository level, so
 * {@code owner/repository/path/to/action@ref} and {@code owner/repository@ref}
 * share the same dependency identity. The ref is kept as written, without
 * semantic normalization, because a workflow pinned to an SHA should continue
 * to be updated as an SHA while still allowing the user-facing version to be
 * shown as explanatory metadata.
 *
 * @param owner the GitHub repository owner, used as the dependency group.
 * @param repository the GitHub repository name, used as the dependency
 * artifact.
 * @param version the version ref portion of the declaration, or {@literal null}
 * if the declaration has no usable version reference.
 *
 * @author Mark Paluch
 * @see GitHubWorkflowParser
 * @see RefStyle
 */
record UsesRepositoryAction(String owner, String repository, @Nullable String version) {

	public UsesRepositoryAction(GitHubAction action) {
		this(action.groupId(), action.artifactId(), action.version());
	}

	/**
	 * Return the dependency identity represented by this workflow action.
	 * <p>
	 * The identity is repository-scoped so that release lookup, caching, and
	 * update matching are independent of the workflow-local action path.
	 */
	public ArtifactId toArtifactId() {
		return GitHubAction.of(owner, repository);
	}

	/**
	 * Return the declared version source for dependency analysis.
	 * <p>
	 * An action without a usable ref has repository identity but no declared
	 * version. This distinction lets callers collect the action as a dependency
	 * without manufacturing version metadata that was not present in the workflow.
	 */
	public VersionSource toVersionSource() {
		return VersionSource.from(version());
	}

	/**
	 * Return the replacement text that preserves the workflow's ref style.
	 * <p>
	 * Version-style declarations are updated with the release version.
	 * SHA-pinned declarations are updated with the release commit SHA when that
	 * metadata is available and carry the resolved version as explanatory text. If
	 * no SHA is available, the release version is returned as the safest available
	 * replacement text.
	 * @param gitVersion the resolved release version to render.
	 */
	public VersionText getVersion(GitVersion gitVersion) {

		RefStyle style = RefStyle.from(version());
		String text = gitVersion.renderRef(style, version());

		if (style == RefStyle.SHA && StringUtils.hasText(gitVersion.getSha())) {
			return new VersionText(text, gitVersion.toString());
		}

		return new VersionText(text, "");
	}

	/**
	 * Return the rendering style implied by the declared ref.
	 * <p>
	 * Callers use the style to preserve the user's pinning model when offering
	 * completions or applying updates.
	 */
	public RefStyle getStyle() {
		return RefStyle.from(version());
	}

	/**
	 * Version text prepared for insertion into a workflow scalar.
	 * <p>
	 * The optional comment is managed metadata for SHA-pinned declarations and
	 * is intentionally kept separate from the scalar replacement text.
	 */
	record VersionText(String text, String comment) {

		/**
		 * Create a replacement that pins to the immutable commit SHA, regardless of the
		 * declared ref style.
		 * @return the SHA replacement text with the version as managed comment.
		 * @throws IllegalStateException if {@code gitVersion} carries no SHA.
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
