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

package biz.paluch.dap.artifact;

import java.util.Objects;

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * {@link ArtifactVersion} wrapper carrying source-provided hash metadata.
 *
 * <p>For Git-backed artifacts the hash is a commit SHA. The same carrier is
 * also used for checksummed Gradle distributions, where the value is the
 * published archive checksum. A hash-less instance created through
 * {@link #of(ArtifactVersion)} retains only the comparable version.
 *
 * @author Mark Paluch
 */
public class GitVersion extends ArtifactVersionWrapper implements ArtifactVersion {

	private final @Nullable String sha;

	private final ArtifactVersion version;

	/**
	 * Create a new {@code GitVersion}.
	 * @param sha the source-provided hash, or {@literal null} when unavailable.
	 * @param version the version used for comparison and display.
	 */
	GitVersion(@Nullable String sha, ArtifactVersion version) {
		super(version);
		this.sha = sha;
		this.version = version;
	}

	/**
	 * Create a {@code GitVersion} with source-provided hash metadata.
	 * @param sha the source-provided hash, or {@literal null} when unavailable.
	 * @param version the normalized delegate version.
	 * @return the version.
	 */
	public static GitVersion of(@Nullable String sha, ArtifactVersion version) {
		return new GitVersion(sha, version);
	}

	/**
	 * Create a {@code GitVersion} without hash metadata.
	 * @param version the normalized delegate version.
	 * @return the version.
	 */
	public static GitVersion of(ArtifactVersion version) {
		return new GitVersion(null, version);
	}

	/**
	 * Return whether source-provided hash metadata is available.
	 * @return {@code true} if a non-blank hash is available.
	 */
	public boolean hasSha() {
		return StringUtils.hasText(sha);
	}

	/**
	 * Return the source-provided hash, or {@literal null} if unavailable.
	 * @return the source-provided hash, or {@literal null}.
	 */
	@Nullable
	public String getSha() {
		return sha;
	}

	/**
	 * Return the required source-provided hash.
	 * @return the required hash.
	 * @throws IllegalStateException if no hash is associated with this version.
	 */
	public String getRequiredSha() {
		if (StringUtils.isEmpty(sha)) {
			throw new IllegalStateException("No sha associated with this version");
		}
		return sha;
	}

	/**
	 * Return the source-provided hash truncated to its first 8 characters.
	 * @return the abbreviated hash, the full value when no longer than 8
	 * characters, or {@literal null} if unavailable.
	 */
	@Nullable
	public String getShortSha() {
		return StringUtils.hasText(sha) && sha.length() > 7 ? sha.substring(0, 8) : sha;
	}

	/**
	 * Return the required source-provided hash truncated to its first 8 characters.
	 * @return the abbreviated hash, or the full value when no longer than 8
	 * characters.
	 * @throws IllegalStateException if no hash is associated with this version.
	 */
	public String getRequiredShortSha() {
		String sha = getShortSha();
		if (StringUtils.isEmpty(sha)) {
			throw new IllegalStateException("No sha associated with this version");
		}
		return sha;
	}

	/**
	 * Render this version as a ref string suitable for the given style.
	 * <p>For {@link RefStyle#VERSION} or when no hash metadata is available, the
	 * version's tag form is returned. For {@link RefStyle#SHA} with SHA metadata,
	 * the stored hash is returned, truncated to the original committish length when
	 * the original committish is shorter than the SHA. A {@literal null} or empty
	 * {@code originalCommittish} preserves the full SHA.
	 *
	 * <p>{@link RefStyle#SHA} is meaningful only when the stored hash is a Git
	 * commit hash.
	 * @param style the rendering style, classified from the original committish.
	 * @param originalCommittish the original committish text the user wrote; can be
	 * {@literal null}.
	 * @return the rendered ref string.
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
	 * {@link #getShortSha() abbreviated hash} if present.
	 * @return the documentation display string.
	 */
	@Override
	public String toDocumentationString() {

		if (StringUtils.hasText(sha)) {
			return "%s (%s)".formatted(this, getShortSha());
		}

		return toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof GitVersion that) {
			return Objects.equals(sha, that.sha) && Objects.equals(version, that.version);
		}
		if (!(obj instanceof ArtifactVersion av)) {
			return false;
		}
		if (av.isWrapped()) {
			return equals(av.getVersion());
		}
		return false;
	}

}
