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

package biz.paluch.dap.support;

import java.util.Collection;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersioningScheme;
import biz.paluch.dap.util.MessageBundle;
import org.jetbrains.annotations.Nls;
import org.jspecify.annotations.Nullable;

/**
 * Selects a release within an upgrade boundary. Remediation targets are
 * supplied by security or rule analysis instead.
 *
 * @author Mark Paluch
 * @see Releases
 */
public enum UpgradeStrategy {

	/**
	 * Security remediation supplied by analysis. {@link #select} returns
	 * {@code null}.
	 */
	SAFE {

		@Override
		@Nullable
		public Release select(ArtifactVersion current, Collection<Release> options) {
			return null;
		}

	},

	/**
	 * Rule remediation supplied by governance analysis. {@link #select} returns
	 * {@code null}.
	 */
	RULE {

		@Override
		@Nullable
		public Release select(ArtifactVersion current, Collection<Release> options) {
			return null;
		}

	},

	/**
	 * Newest general-availability or bugfix release newer than the current version
	 * in the same major/minor line. For release trains, service releases within the
	 * same train qualify.
	 */
	PATCH {

		@Override
		@Nullable
		public Release select(ArtifactVersion current, Collection<Release> options) {

			return options.stream() //
					.filter(Predicate.not(Release::isPreview)) //
					.filter(opt -> opt.version().hasSameMajorMinor(current) && opt.isNewer(current)) //
					.filter(opt -> opt.isReleaseVersion() || opt.isBugFixVersion()) //
					.findFirst().orElse(null);
		}

	},

	/**
	 * Newest non-preview release in a higher minor of the same major line.
	 */
	MINOR {

		@Override
		@Nullable
		public Release select(ArtifactVersion current, Collection<Release> options) {
			return options.stream() //
					.filter(Predicate.not(Release::isPreview)) //
					.filter(opt -> opt.version().hasSameMajor(current) && !opt.hasSameMajorMinor(current)
							&& opt.isNewer(current))
					.findFirst().orElse(null);
		}

	},

	/**
	 * Newest non-preview release in a higher major line.
	 */
	MAJOR {

		@Override
		@Nullable
		public Release select(ArtifactVersion current, Collection<Release> options) {
			return options.stream() //
					.filter(Predicate.not(Release::isPreview)) //
					.filter(opt -> !opt.version().hasSameMajor(current) && opt.isNewer(current)) //
					.findFirst().orElse(null);
		}

	},

	/**
	 * Newest non-preview release, even if equal to or older than the current
	 * version.
	 */
	LATEST {

		@Override
		@Nullable
		public Release select(ArtifactVersion current, Collection<Release> options) {
			return options.stream() //
					.filter(Predicate.not(Release::isPreview)) //
					.findFirst().orElse(null);
		}

	},

	/**
	 * Newest preview newer than the current version. For snapshots, prefer a
	 * non-snapshot preview in the same major/minor line even if it compares older.
	 */
	PREVIEW {

		@Override
		@Nullable
		public Release select(ArtifactVersion current, Collection<Release> options) {

			if (current.isSnapshotVersion()) {
				Release release = options.stream() //
						.filter(Predicate.not(Release::isSnapshotVersion)) //
						.filter(Release::isPreview)
						.filter(opt -> opt.hasSameMajorMinor(current)) //
						.findFirst().orElse(null);
				if (release != null) {
					return release;
				}
			}

			return options.stream() //
					.filter(Release::isPreview) //
					.filter(opt -> opt.isNewer(current)) //
					.findFirst().orElse(null);
		}

	},

	/**
	 * Finalize a snapshot or preview, preferring the stable release with the same
	 * base version. Otherwise, select a newer stable release in the same
	 * major/minor line. A stable current version has no candidate.
	 */
	RELEASE {

		@Override
		@Nullable
		public Release select(ArtifactVersion current, Collection<Release> options) {

			if (!current.isPreview() && !current.isSnapshotVersion()) {
				return null;
			}

			if (current.isPreview()) {

				Release finalized = options.stream() //
						.filter(Predicate.not(Release::isPreview)) //
						.filter(Predicate.not(Release::isSnapshotVersion)) //
						.filter(opt -> opt.hasSameBaseVersion(current)) //
						.findFirst().orElse(null);
				if (finalized != null) {
					return finalized;
				}
			}

			if (current.isSnapshotVersion()) {

				Release finalized = options.stream() //
						.filter(Predicate.not(Release::isPreview)) //
						.filter(Predicate.not(Release::isSnapshotVersion)) //
						.filter(opt -> opt.hasSameBaseVersion(current)) //
						.findFirst().orElse(null);
				if (finalized != null) {
					return finalized;
				}
			}

			return options.stream() //
					.filter(Predicate.not(Release::isPreview)) //
					.filter(Predicate.not(Release::isSnapshotVersion)) //
					.filter(opt -> opt.isNewer(current) && opt.hasSameMajorMinor(current)) //
					.findFirst().orElse(null);
		}

	};

	public static @Nls String getDisplayName(UpgradeStrategy strategy) {
		return MessageBundle.message("upgrade-strategy." + strategy.name());
	}

	/**
	 * Select from releases sorted newest-first within one versioning scheme. Prefer
	 * {@link #select(ArtifactVersion, Releases)} for automatic scheme scoping.
	 *
	 * @return {@code null} if no candidate meets this strategy's criteria.
	 */
	public abstract @Nullable Release select(ArtifactVersion current, Collection<Release> options);

	/**
	 * Select within the current version's scheme. Reuse the analyzed history across
	 * strategies.
	 *
	 * @return {@code null} for opaque versions or when no candidate qualifies.
	 */
	public @Nullable Release select(ArtifactVersion current, Releases releases) {

		if (current.scheme() == VersioningScheme.OPAQUE) {
			return null;
		}

		return select(current, releases.inScheme(current.scheme()));
	}

	/**
	 * Return whether security or rule analysis supplies the target.
	 */
	public boolean isRemediation() {
		return this == SAFE || this == RULE;
	}

	public @Nls String getDisplayName() {
		return MessageBundle.message("upgrade-strategy." + name());
	}

}
