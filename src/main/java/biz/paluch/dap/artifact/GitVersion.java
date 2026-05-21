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

package biz.paluch.dap.artifact;

import java.util.Objects;

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * {@link ArtifactVersion} implementation for versions associated with a SHA
 * commit hash.
 *
 * @author Mark Paluch
 */
public class GitVersion extends ArtifactVersionWrapper implements ArtifactVersion {

	private final @Nullable String sha;

	private final ArtifactVersion version;

	/**
	 * Create a new {@code GitVersion}.
	 * @param sha the full 40-character SHA-1, or {@literal null} when unavailable.
	 * @param version the version used for comparison and display.
	 */
	private GitVersion(@Nullable String sha, ArtifactVersion version) {
		super(version);
		this.sha = sha;
		this.version = version;
	}

	/**
	 * Create a {@code GitVersion} with an SHA.
	 * @param sha the character SHA.
	 * @param version the normalized delegate version.
	 * @return the version.
	 */
	public static GitVersion of(@Nullable String sha, ArtifactVersion version) {
		return new GitVersion(sha, version);
	}

	/**
	 * Create a {@code GitVersion} without a SHA (tag-only resolution).
	 * @param version the normalized delegate version.
	 * @return the version.
	 */
	public static GitVersion of(ArtifactVersion version) {
		return new GitVersion(null, version);
	}

	/**
	 * Return the resolved SHA-1 commit hash, or {@literal null} if unavailable.
	 */
	@Nullable
	public String getSha() {
		return sha;
	}

	/**
	 * Return the required SHA or throw {@link IllegalStateException} if no SHA is
	 * associated with this version.
	 * @return the required SHA.
	 * @throws IllegalStateException if no SHA is associated with this version.
	 */
	public String getRequiredSha() {
		if (StringUtils.isEmpty(sha)) {
			throw new IllegalStateException("No sha associated with this version");
		}
		return sha;
	}

	/**
	 * Return the resolved SHA-1 commit hash, or {@literal null} if unavailable.
	 */
	@Nullable
	public String getShortSha() {
		if (StringUtils.hasText(sha) && sha.length() > 7) {
			return sha.substring(0, 8);
		}
		return sha;
	}

	public boolean hasSha() {
		return StringUtils.hasText(sha);
	}

	/**
	 * Render this version as a ref string suitable for the given style.
	 * <p>
	 * For {@link RefStyle#VERSION} or when no SHA metadata is available, the
	 * version's tag form is returned. For {@link RefStyle#SHA} with SHA metadata,
	 * the stored SHA is returned, truncated to the original committish length when
	 * the original committish is shorter than the SHA. A {@literal null} or empty
	 * {@code originalCommittish} preserves the full SHA.
	 * @param style the rendering style, classified from the original committish.
	 * @param originalCommittish the original committish text the user wrote; can be
	 * {@literal null}.
	 * @return the rendered ref string; guaranteed to be not {@literal null}.
	 */
	public String renderRef(RefStyle style, @Nullable String originalCommittish) {

		if (style == RefStyle.VERSION || !StringUtils.hasText(sha)) {
			return getVersion().toString();
		}

		String text = sha;
		if (StringUtils.hasText(originalCommittish)) {
			int length = originalCommittish.length();
			if (length < text.length()) {
				return text.substring(0, length);
			}
		}

		return text;
	}

	/**
	 * Return a string suitable for documentation containing the version and
	 * {@link #getShortSha() short SHA} if present.
	 */
	public String toDocumentationString() {

		if (StringUtils.hasText(sha)) {
			return "%s (%s)".formatted(this, getShortSha());
		}

		return toString();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof GitVersion that)) {
			return false;
		}
		return Objects.equals(sha, that.sha) && Objects.equals(version, that.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(sha, version);
	}

}
