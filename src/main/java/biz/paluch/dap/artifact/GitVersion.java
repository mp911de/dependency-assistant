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

	GitVersion(@Nullable String sha, ArtifactVersion version) {
		super(version);
		this.sha = sha;
		this.version = version;
	}

	/**
	 * Attach source-provided hash metadata to a version.
	 * @param sha the hash, or {@literal null} if unavailable.
	 */
	public static GitVersion of(@Nullable String sha, ArtifactVersion version) {
		return new GitVersion(sha, version);
	}

	/**
	 * Wrap a version without hash metadata.
	 */
	public static GitVersion of(ArtifactVersion version) {
		return new GitVersion(null, version);
	}

	/**
	 * Return whether a non-blank source hash is available.
	 */
	public boolean hasSha() {
		return StringUtils.hasText(sha);
	}

	/**
	 * Return the source hash, or {@literal null} if unavailable.
	 */
	@Nullable
	public String getSha() {
		return sha;
	}

	/**
	 * Return the source hash.
	 * @throws IllegalStateException if no non-blank hash is available.
	 */
	public String getRequiredSha() {
		if (StringUtils.isEmpty(sha)) {
			throw new IllegalStateException("No sha associated with this version");
		}
		return sha;
	}

	/**
	 * Return up to the first eight hash characters, or {@literal null} if
	 * unavailable.
	 */
	@Nullable
	public String getShortSha() {
		return StringUtils.hasText(sha) && sha.length() > 7 ? sha.substring(0, 8) : sha;
	}

	/**
	 * Return up to the first eight hash characters.
	 * @throws IllegalStateException if no non-blank hash is available.
	 */
	public String getRequiredShortSha() {
		String sha = getShortSha();
		if (StringUtils.isEmpty(sha)) {
			throw new IllegalStateException("No sha associated with this version");
		}
		return sha;
	}

	/**
	 * Render a tag or commit hash in the requested style.
	 * <p>Hash rendering preserves the original abbreviation length when shorter.
	 * Without a hash, the version tag is used. SHA style requires a Git commit
	 * hash.
	 * @param originalCommittish the original ref, or {@literal null} to keep the
	 * full hash. An empty value also keeps the full hash.
	 * @see RefStyle
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
	 * Include the abbreviated hash in the documentation string when available.
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
