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
package biz.paluch.mavenupdater.dependencies;

import javax.swing.Icon;

import com.intellij.icons.AllIcons;

/**
 * How a suggested version relates to the current version (for icon display).
 */
public enum VersionAge {

	OLDER,

	NEWER_PATCH,

	PREVIEW,

	NEWER_MINOR,

	NEWER_MAJOR,

	SAME_OR_UNKNOWN;

	public static VersionAge fromVersions(ArtifactVersion current, ArtifactVersion option) {

		int cmp = option.compareTo(current);
		if (cmp < 0) {
			return OLDER;
		}

		if (cmp == 0) {
			return SAME_OR_UNKNOWN;
		}

		if (option.isMilestoneVersion() || option.isReleaseCandidateVersion()) {
			return PREVIEW;
		}

		if (option.hasSameMajorMinor(current) && option.isNewer(current)) {
			return NEWER_PATCH;
		}

		if (option.getVersion().getMajor() == current.getVersion().getMajor()
				&& option.getVersion().getMinor() > current.getVersion().getMinor() && option.isNewer(current)) {
			return NEWER_MINOR;
		}

		return NEWER_MAJOR;
	}

	public Icon getIcon() {
		return switch (this) {
			case OLDER -> AllIcons.Nodes.Library;
			case NEWER_PATCH -> AllIcons.Actions.StartDebugger;
			case NEWER_MINOR -> AllIcons.Debugger.ThreadRunning;
			case NEWER_MAJOR -> AllIcons.Actions.RunAll;
			case PREVIEW -> AllIcons.Debugger.DebuggerSync;
			case SAME_OR_UNKNOWN -> AllIcons.Nodes.PpLibFolder;
		};

	}

}
