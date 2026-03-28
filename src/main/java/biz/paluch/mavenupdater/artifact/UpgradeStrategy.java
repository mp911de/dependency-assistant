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
package biz.paluch.mavenupdater.artifact;

import java.util.Collection;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * Upgrade strategies.
 *
 * @author Mark Paluch
 */
public enum UpgradeStrategy {

	/**
	 * Upgrade to the latest patch version.
	 */
	PATCH {
		@Override
		@Nullable
		VersionOption select(ArtifactVersion current, Collection<VersionOption> options) {

			return options.stream() //
					.filter(Predicate.not(VersionOption::isPreview)) //
					.filter(opt -> opt.version().hasSameMajorMinor(current) && opt.isNewer(current)) //
					.filter(opt -> opt.isReleaseVersion() || opt.isBugFixVersion()) //
					.findFirst().orElse(null);
		}
	},

	/**
	 * Upgrade to the latest minor version.
	 */
	MINOR {
		@Override
		@Nullable
		VersionOption select(ArtifactVersion current, Collection<VersionOption> options) {
			return options.stream() //
					.filter(Predicate.not(VersionOption::isPreview)) //
					.filter(opt -> opt.version().hasSameMajor(current) && !opt.hasSameMajorMinor(current) && opt.isNewer(current))
					.findFirst().orElse(null);
		}
	},

	/**
	 * Upgrade to the next major version.
	 */
	MAJOR {
		@Override
		@Nullable
		VersionOption select(ArtifactVersion current, Collection<VersionOption> options) {
			return options.stream() //
					.filter(Predicate.not(VersionOption::isPreview)) //
					.filter(opt -> !opt.version().hasSameMajor(current) && opt.isNewer(current)) //
					.findFirst().orElse(null);
		}
	},

	/**
	 * Upgrade to the latest version.
	 */
	LATEST {
		@Override
		@Nullable
		VersionOption select(ArtifactVersion current, Collection<VersionOption> options) {
			return options.stream() //
					.filter(Predicate.not(VersionOption::isPreview)) //
					.findFirst().orElse(null);
		}

	},

	PREVIEW {
		@Override
		@Nullable
		VersionOption select(ArtifactVersion current, Collection<VersionOption> options) {
			return options.stream() //
					.filter(VersionOption::isPreview) //
					.filter(opt -> opt.isNewer(current)) //
					.findFirst().orElse(null);
		}
	};

	abstract @Nullable VersionOption select(ArtifactVersion current, Collection<VersionOption> options);
}
