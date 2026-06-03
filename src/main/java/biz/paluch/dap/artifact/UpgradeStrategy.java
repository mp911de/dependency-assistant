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

import java.util.Collection;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * Determines which release to select as the upgrade target from a list of
 * available releases.
 * <p>
 * Each constant represents a boundary within the version space.
 * {@link #select} filters the provided releases according to that boundary and
 * returns the best match, or {@literal null} if no suitable release exists.
 * <p>
 * Note: The {@code options} list passed to {@link #select} must be sorted
 * newest-first (as produced by {@link ReleaseResolver}).
 *
 * @author Mark Paluch
 * @see ReleaseResolver
 * @see Release
 */
public enum UpgradeStrategy {

	/**
	 * Select the newest non-preview release within the same major and minor version
	 * as {@code current}, limited to general-availability releases (including
	 * bug-fix releases).
	 * <p>Return {@literal null} when the current version is already the latest
	 * within its major.minor line, or no qualifying release exists.
	 */
	PATCH {

		@Override
		@Nullable
		public Release select(ArtifactVersion current, Collection<Release> options) {

			return options.stream() //
					.filter(Predicate.not(Release::isPreview)) //
					.filter(opt -> opt.version().hasSameMajorMinor(current) && opt.isNewer(current)) //
					.filter(Release::isReleaseVersion) //
					.findFirst().orElse(null);
		}

	},

	/**
	 * Select the newest non-preview release with the same major version but a
	 * higher minor version than {@code current}.
	 * <p>
	 * Return {@literal null} when no qualifying minor upgrade exists.
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
	 * Select the newest non-preview release with a higher major version than
	 * {@code current}.
	 * <p>
	 * Return {@literal null} when no qualifying major upgrade exists.
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
	 * Select the newest non-preview release regardless of version boundaries.
	 * <p>
	 * This strategy does not compare against {@code current}; it simply returns
	 * the first non-preview entry from the sorted list, which may be the same as or
	 * older than {@code current} if no newer stable release is available.
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
	 * Select the newest preview release (RC, milestone) that is newer than
	 * {@code current}. If the current version is a snapshot, select a non-snapshot
	 * preview release.
	 * <p>Return {@literal null} when no qualifying preview release exists.
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
	 * Select the newest non-preview, non-snapshot release within the same major and
	 * minor line as {@code current} if the current version is a snapshot or
	 * preview.
	 * <p>When {@code current} is a snapshot or preview, finalize it instead: select
	 * the general-availability release with the same numeric version (e.g.
	 * {@code 3.9.6-SNAPSHOT} or {@code 3.9.6-M1} resolves to {@code 3.9.6}). If
	 * that release was never published, fall back to the newest stable release in
	 * the same line that is newer than {@code current}.
	 * <p>Return {@literal null} when no qualifying release exists.
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

	/**
	 * Select the best upgrade candidate from {@code options} according to this
	 * strategy.
	 * <p>
	 * The caller is responsible for providing {@code options} sorted
	 * newest-first. Each strategy applies its own filter and returns the first
	 * matching element.
	 *
	 * @param current the version currently in use; must not be {@literal null}.
	 * @param options the available releases sorted newest-first; must not be
	 * {@literal null}.
	 * @return the selected release, or {@literal null} if no release satisfies this
	 * strategy's criteria.
	 */
	public abstract @Nullable Release select(ArtifactVersion current, Collection<Release> options);

}
