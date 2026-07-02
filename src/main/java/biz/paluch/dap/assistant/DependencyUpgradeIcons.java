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

package biz.paluch.dap.assistant;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.checker.CheckerIcons;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.util.ResolvableIcon;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.ScalableIcon;

/**
 * Icon registry for dependency-upgrade presentation.
 *
 * <p>Maps a {@link VersionAge} or {@link UpgradeStrategy} to a
 * {@link ResolvableIcon} so each entry declares its Swing icon and its
 * reflective documentation path in one place. This keeps {@link VersionAge} a
 * pure version classifier and removes the parallel icon/icon-name lookups that
 * could drift.
 *
 * @author Mark Paluch
 */
public class DependencyUpgradeIcons {

	private static final ResolvableIcon OLDER = new ResolvableIcon(AllIcons.Nodes.Library, "AllIcons.Nodes.Library");

	private static final ResolvableIcon NEWER_PATCH = new ResolvableIcon(AllIcons.Actions.StartDebugger,
			"AllIcons.Actions.StartDebugger");

	private static final ResolvableIcon NEWER_MINOR = new ResolvableIcon(AllIcons.Debugger.ThreadRunning,
			"AllIcons.Debugger.ThreadRunning");

	private static final ResolvableIcon NEWER_MAJOR = new ResolvableIcon(AllIcons.Actions.RunAll,
			"AllIcons.Actions.RunAll");

	private static final ResolvableIcon PREVIEW = new ResolvableIcon(AllIcons.Debugger.DebuggerSync,
			"AllIcons.Debugger.DebuggerSync");

	private static final ResolvableIcon SAME_OR_UNKNOWN = new ResolvableIcon(AllIcons.Nodes.PpLibFolder,
			"AllIcons.Nodes.PpLibFolder");

	private static final ResolvableIcon SAFE = new ResolvableIcon(CheckerIcons.SAFE,
			"biz.paluch.dap.checker.CheckerIcons.SAFE");

	private static final ResolvableIcon RULE_COMPLIANT = new ResolvableIcon(
			((ScalableIcon) AllIcons.Actions.InlaySecuredShield).scale(1.3f), "AllIcons.Actions.InlaySecuredShield");

	private static final ResolvableIcon RULE_WARNING = new ResolvableIcon(DependencyAssistantIcons.DEPENDENCY_RULE_WARN,
			"biz.paluch.dap.DependencyAssistantIcons.DEPENDENCY_RULE_WARN");

	private DependencyUpgradeIcons() {
	}

	public static Icon resolveIcon(UpgradeStrategy strategy) {
		return resolve(strategy).getIcon();
	}

	public static ResolvableIcon resolve(UpgradeStrategy strategy) {
		return switch (strategy) {
		case SAFE -> SAFE;
		case RULE -> RULE_COMPLIANT;
		case RELEASE, PATCH -> NEWER_PATCH;
		case MINOR -> NEWER_MINOR;
		case MAJOR, LATEST -> NEWER_MAJOR;
		case PREVIEW -> PREVIEW;
		};
	}

	public static Icon resolveIcon(VersionAge age) {
		return resolve(age).getIcon();
	}

	public static ResolvableIcon resolve(VersionAge age) {
		return switch (age) {
		case OLDER -> OLDER;
		case NEWER_PATCH -> NEWER_PATCH;
		case NEWER_MINOR -> NEWER_MINOR;
		case NEWER_MAJOR -> NEWER_MAJOR;
		case PREVIEW -> PREVIEW;
		case SAME_OR_UNKNOWN -> SAME_OR_UNKNOWN;
		};
	}

	static ResolvableIcon ruleWarning() {
		return RULE_WARNING;
	}

	static ResolvableIcon ruleCompliant() {
		return RULE_COMPLIANT;
	}

}
