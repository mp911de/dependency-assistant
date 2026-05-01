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

import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitVersion;
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
 * workflow file.
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
 * @param version the version ref portion of the declaration, or {@code null} if
 * the declaration has no usable version reference.
 * 
 * @author Mark Paluch
 * @see GitHubWorkflowParser
 * @see WorkflowRefStyle
 */
record UsesRepositoryAction(String owner, String repository, @Nullable String version) {

	public UsesRepositoryAction(GitHubAction action) {
		this(action.groupId(), action.artifactId(), action.version());
	}

	/**
	 * Return the dependency identity represented by this workflow action.
	 * <p>The identity is repository-scoped so that release lookup, caching, and
	 * update matching are independent of the workflow-local action path.
	 */
	public ArtifactId toArtifactId() {
		return GitHubAction.of(owner, repository);
	}

	/**
	 * Return the declared version source for dependency analysis.
	 * <p>An action without a usable ref has repository identity but no declared
	 * version. This distinction lets callers collect the action as a dependency
	 * without manufacturing version metadata that was not present in the workflow.
	 */
	public VersionSource toVersionSource() {
		return StringUtils.hasText(version()) ? VersionSource.declared(version()) : VersionSource.none();
	}

	/**
	 * Return the replacement text that preserves the workflow's ref style.
	 * <p>Version-style declarations are updated with the release version.
	 * SHA-pinned declarations are updated with the release commit SHA and carry the
	 * resolved version as explanatory text, allowing the workflow to remain pinned
	 * while the IDE still exposes semantic release information.
	 * @param version the resolved release version to render.
	 */
	public VersionText getVersion(GitVersion version) {

		WorkflowRefStyle style = WorkflowRefStyle.from(version());
		String text = version.getVersion().toString();

		if (style == WorkflowRefStyle.SHA && StringUtils.hasText(version.getSha())) {

			text = version.getSha();
			if (StringUtils.hasText(version())) {
				int shaLength = version().length();
				if (shaLength < text.length()) {
					text = text.substring(0, shaLength);
				}
			}

			return new VersionText(text, version.toString());
		}

		return new VersionText(text, "");
	}

	/**
	 * Return the rendering style implied by the declared ref.
	 * <p>Callers use the style to preserve the user's pinning model when offering
	 * completions or applying updates.
	 */
	public WorkflowRefStyle getStyle() {
		return WorkflowRefStyle.from(version());
	}

	/**
	 * Version text prepared for insertion into a workflow scalar.
	 * <p>The optional comment is managed metadata for SHA-pinned declarations and
	 * is intentionally kept separate from the scalar replacement text.
	 */
	record VersionText(String text, String comment) {

		boolean hasComment() {
			return StringUtils.hasText(comment);
		}

		@Override
		public String toString() {
			return StringUtils.hasText(comment) ? "%s # %s".formatted(text, comment) : text;
		}

	}

	/**
	 * Classification of a workflow {@code uses:} ref string.
	 *
	 * <p>The ref after {@code @} in a {@code uses:} declaration can take three
	 * forms: a SHA-1 hash or a bare semantic version.
	 *
	 * @author Mark Paluch
	 */
	enum WorkflowRefStyle {

		/**
		 * A full lowercase hex SHA-1 commit hash, e.g.
		 * {@code be666c2fcd27ec809703dec50e508c2fdc7f6654}.
		 */
		SHA,

		/**
		 * A semantic version without a leading {@code v} prefix, e.g. {@code 1.2.3}.
		 */
		VERSION;

		private static final Pattern FULL_SHA = Pattern.compile("^[0-9a-f]{4,40}$");

		/**
		 * Determine a {@link WorkflowRefStyle} from given version string. Defaults to
		 * {@link #SHA} if the version is empty.
		 * @param version the raw version as written in the YAML file.
		 * @return the resulting WorkflowRefStyle.
		 */
		static WorkflowRefStyle from(@Nullable String version) {
			return StringUtils.isEmpty(version) || FULL_SHA.matcher(version).matches() ? SHA : VERSION;
		}

	}

}
