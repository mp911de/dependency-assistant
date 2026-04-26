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
 * <p>Each constant represents a boundary within the version space.
 * {@link #select} filters the provided releases according to that boundary and
 * returns the best match, or {@literal null} if no suitable release exists.
 * <p>Note: The {@code options} list passed to {@link #select} must be sorted
 * newest-first (as produced by {@link ReleaseResolver}).
 *
 * @author Mark Paluch
 * @see ReleaseResolver
 * @see Release
 */
public enum UpgradeStrategy {

	/**
	 * Select the newest non-preview release within the same major and minor version
	 * as {@code current}, limited to release and bug-fix versions.
	 * <p>Returns {@literal null} when the current version is already the latest
	 * within its major.minor line, or no qualifying release exists.
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
	 * Select the newest non-preview release with the same major version but a
	 * higher minor version than {@code current}.
	 * <p>Returns {@literal null} when no qualifying minor upgrade exists.
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
	 * <p>Returns {@literal null} when no qualifying major upgrade exists.
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
	 * <p>This strategy does not compare against {@code current}; it simply returns
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
	 * {@code current}.
	 * <p>Returns {@literal null} when no qualifying preview release exists.
	 */
	PREVIEW {

		@Override
		@Nullable
		public Release select(ArtifactVersion current, Collection<Release> options) {
			return options.stream() //
					.filter(Release::isPreview) //
					.filter(opt -> opt.isNewer(current)) //
					.findFirst().orElse(null);
		}

	};

	/**
	 * Select the best upgrade candidate from {@code options} according to this
	 * strategy.
	 * <p>The caller is responsible for providing {@code options} sorted
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
