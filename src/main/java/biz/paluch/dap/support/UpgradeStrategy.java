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
 * Determines which release to select as the upgrade target from a list of
 * available releases.
 * <p>Each constant represents a boundary within the version space.
 * {@link #select} filters the provided releases according to that boundary and
 * returns the best match, or {@literal null} if no suitable release exists.
 * <p>Note: The {@code options} list passed to {@link #select} must be sorted
 * newest-first.
 *
 * @author Mark Paluch
 * @see Release
 */
public enum UpgradeStrategy {

	/**
	 * Remediation strategy whose target is the Safe Version, the lowest newer
	 * release known free of vulnerabilities. The target is supplied by security
	 * analysis rather than computed by {@link #select}, which returns
	 * {@literal null}.
	 */
	SAFE {

		@Override
		@Nullable
		public Release select(ArtifactVersion current, Collection<Release> options) {
			return null;
		}

	},

	/**
	 * Remediation strategy whose target aligns the version according to
	 * {@link biz.paluch.dap.rule.DependencyRule}. The target is supplied by
	 * governance analysis rather than computed by {@link #select}, which returns
	 * {@literal null}.
	 */
	RULE {

		@Override
		@Nullable
		public Release select(ArtifactVersion current, Collection<Release> options) {
			return null;
		}

	},

	/**
	 * Select the newest non-preview release within the same major and minor version
	 * as {@code current}, limited to general-availability releases (including
	 * bug-fix releases).
	 * <p>For release-train versions the same major.minor line is the same train, so
	 * service releases of that train (e.g. {@code Dysprosium-SR25}) qualify as
	 * patch-level upgrades while releases of a different train do not.
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
					.filter(opt -> opt.isReleaseVersion() || opt.isBugFixVersion()) //
					.findFirst().orElse(null);
		}

	},

	/**
	 * Select the newest non-preview release with the same major version but a
	 * higher minor version than {@code current}.
	 * <p>Return {@literal null} when no qualifying minor upgrade exists.
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
	 * <p>Return {@literal null} when no qualifying major upgrade exists.
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
	 * Return the localized display name of the given upgrade strategy.
	 */
	public static @Nls String getDisplayName(UpgradeStrategy strategy) {
		return MessageBundle.message("upgrade-strategy." + strategy.name());
	}

	/**
	 * Select the best upgrade candidate from {@code options} according to this
	 * strategy.
	 * <p>The caller is responsible for providing {@code options} sorted
	 * newest-first and belonging to a single {@link VersioningScheme}. Each
	 * strategy applies its own filter and returns the first matching element.
	 * Prefer {@link #select(ArtifactVersion, Releases)} which scopes the candidates
	 * to the current version's scheme.
	 *
	 * @param current the version currently in use.
	 * @param options the available releases sorted newest-first; must not be
	 * {@literal null}.
	 * @return the selected release, or {@literal null} if no release satisfies this
	 * strategy's criteria.
	 */
	public abstract @Nullable Release select(ArtifactVersion current, Collection<Release> options);

	/**
	 * Select the best upgrade candidate from the given release history according to
	 * this strategy, considering only releases in the same {@link VersioningScheme}
	 * as {@code current}.
	 * <p>A {@link VersioningScheme#OPAQUE} current version yields no upgrade. Build
	 * one {@link Releases} per operation and pass it to all strategies to avoid
	 * repeating the scheme analysis.
	 *
	 * @param current the version currently in use.
	 * @param releases the analyzed release history.
	 * @return the selected release, or {@literal null} if no release satisfies this
	 * strategy's criteria within the current version's scheme.
	 */
	public @Nullable Release select(ArtifactVersion current, Releases releases) {

		if (current.scheme() == VersioningScheme.OPAQUE) {
			return null;
		}

		return select(current, releases.inScheme(current.scheme()));
	}

	/**
	 * Return whether this is a remediation strategy, whose target is supplied by
	 * security or governance analysis rather than selected from the version space.
	 *
	 * <p>A remediation target ({@link #SAFE}, {@link #RULE}) is offered only when
	 * the corresponding analysis produces a target. When present, it can be pinned
	 * into the displayed release set even when it sits outside the normal
	 * newer-release window. The version tiers are never remediation. Use this in
	 * place of comparing against {@link #SAFE} and {@link #RULE} directly.
	 *
	 * @return {@literal true} for {@link #SAFE} and {@link #RULE}; {@literal false}
	 * for the version tiers.
	 */
	public boolean isRemediation() {
		return this == SAFE || this == RULE;
	}

	/**
	 * Return the localized display name of this upgrade strategy.
	 */
	public @Nls String getDisplayName() {
		return MessageBundle.message("upgrade-strategy." + name());
	}

}
