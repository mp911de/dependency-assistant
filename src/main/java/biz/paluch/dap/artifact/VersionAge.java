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

import javax.swing.Icon;

import com.intellij.icons.AllIcons;

/**
 * Coarse presentation category for a candidate artifact version.
 *
 * <p>
 * {@code VersionAge} is the shared visual language for dependency update
 * candidates in completion lists, dialogs, and generated documentation. It
 * intentionally sits after version parsing and upgrade selection: callers use
 * {@link ArtifactVersion} to compare concrete versions and
 * {@link UpgradeStrategy} to select an update target, then use this type to
 * present the candidate with consistent impact semantics.
 *
 * <p>
 * The categories are not an ordering contract. They describe the
 * relationship that is useful to a user reviewing an update: older, neutral,
 * patch, minor, major, preview, or a stable release target.
 *
 * @author Mark Paluch
 */
public enum VersionAge {

	/**
	 * Candidate version that compares older than the current version.
	 */
	OLDER,

	/**
	 * Neutral category for equal versions or callers that cannot provide a more
	 * specific relationship.
	 */
	SAME_OR_UNKNOWN,

	/**
	 * Newer candidate in the same major/minor version line.
	 */
	NEWER_PATCH,

	/**
	 * Newer candidate in the same major version line, but outside the current
	 * major/minor line.
	 */
	NEWER_MINOR,

	/**
	 * Newer candidate outside the current major line, or an update target that may
	 * cross stable version boundaries.
	 */
	NEWER_MAJOR,

	/**
	 * Newer milestone or release candidate. Preview status takes precedence over
	 * patch/minor/major presentation because it communicates release stability.
	 */
	PREVIEW,

	/**
	 * Stable release target selected by policy rather than inferred from a concrete
	 * version comparison.
	 */
	RELEASE,

	RULE;

	/**
	 * Return the presentation category for the given upgrade strategy.
	 * <p>
	 * The mapping bridges a user-selected strategy to the same categories used
	 * for concrete versions. It does not inspect available releases; it only
	 * expresses the expected impact of the selected strategy.
	 * @param target the selected upgrade strategy
	 * @return the corresponding presentation category
	 */
	public static VersionAge fromTarget(UpgradeStrategy target) {

		return switch (target) {
		case PATCH -> NEWER_PATCH;
		case MINOR -> NEWER_MINOR;
		case MAJOR, LATEST -> NEWER_MAJOR;
		case PREVIEW -> PREVIEW;
		case RELEASE -> RELEASE;
			case RULE -> RULE;
		};
	}

	/**
	 * Return how the candidate version relates to the current version.
	 * <p>
	 * The result is meant for presentation and should not be used to select an
	 * upgrade candidate. Selection belongs to {@link UpgradeStrategy}; the
	 * returned category classifies an already-known candidate according to the
	 * comparison and version boundary contract of {@link ArtifactVersion}.
	 * @param start the start version (or currently used version).
	 * @param end the end version (or upgrade candidate version)
	 * @return the end candidate version age category.
	 * @see VersionAware
	 */
	public static VersionAge between(VersionAware start, VersionAware end) {
		return between(start.getVersion(), end.getVersion());
	}

	/**
	 * Return how the candidate version relates to the current version.
	 * <p>
	 * The result is meant for presentation and should not be used to select an
	 * upgrade candidate. Selection belongs to {@link UpgradeStrategy}; the
	 * returned category classifies an already-known candidate according to the
	 * comparison and version boundary contract of {@link ArtifactVersion}.
	 * @param start the start version (or currently used version).
	 * @param end the end version (or upgrade candidate version)
	 * @return the end candidate version age category.
	 */
	public static VersionAge between(ArtifactVersion start, ArtifactVersion end) {

		int cmp = end.compareTo(start);
		if (cmp < 0) {
			return OLDER;
		}

		if (cmp == 0) {
			return SAME_OR_UNKNOWN;
		}

		if (end.isMilestoneVersion() || end.isReleaseCandidateVersion()) {
			return PREVIEW;
		}

		if (end.hasSameMajorMinor(start) && end.isNewer(start)) {
			return NEWER_PATCH;
		}

		if (end.hasSameMajor(start) && end.isNewer(start)) {
			return NEWER_MINOR;
		}

		return NEWER_MAJOR;
	}

	/**
	 * Return the IntelliJ icon associated with this category.
	 */
	public Icon getIcon() {
		return switch (this) {
		case OLDER -> AllIcons.Nodes.Library;
		case RELEASE, NEWER_PATCH -> AllIcons.Actions.StartDebugger;
		case NEWER_MINOR -> AllIcons.Debugger.ThreadRunning;
		case NEWER_MAJOR -> AllIcons.Actions.RunAll;
		case PREVIEW -> AllIcons.Debugger.DebuggerSync;
		case SAME_OR_UNKNOWN -> AllIcons.Nodes.PpLibFolder;
			case RULE -> AllIcons.Actions.QuickfixBulb;
		};
	}

	/**
	 * Return the symbolic icon name used when rendering this category in
	 * documentation.
	 */
	public String getIconName() {
		return switch (this) {
		case OLDER -> "AllIcons.Nodes.Library";
		case RELEASE, NEWER_PATCH -> "AllIcons.Actions.StartDebugger";
		case NEWER_MINOR -> "AllIcons.Debugger.ThreadRunning";
		case NEWER_MAJOR -> "AllIcons.Actions.RunAll";
		case PREVIEW -> "AllIcons.Debugger.DebuggerSync";
		case SAME_OR_UNKNOWN -> "AllIcons.Nodes.PpLibFolder";
			case RULE -> "AllIcons.Actions.QuickfixBulb";
		};
	}

}
