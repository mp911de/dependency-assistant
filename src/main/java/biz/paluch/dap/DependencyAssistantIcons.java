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

package biz.paluch.dap;

import javax.swing.Icon;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;

/**
 * Central registry of the {@link Icon} constants used across the plugin for
 * gutter markers, dependency tables, and severity highlighting, plus helpers
 * such as {@link #upgradeIcon(Icon, Icon)} that compose status overlays. All
 * icons are loaded through {@link IconLoader} so HiDPI scaling and theming are
 * handled by the platform.
 *
 * @author Mark Paluch
 */
public class DependencyAssistantIcons {

	private static final float UPGRADE_ICON_SCALE = 0.7f;

	/**
	 * Main Dependency Assistant icon.
	 */
	public static final Icon ICON = load("/META-INF/dependency-assistant.svg");

	/**
	 * Property navigation gutter icon.
	 */
	public static final Icon PROPERTY_NAVIGATE = load("/META-INF/icons/propertyNavigate.svg");

	/**
	 * Gutter icon marking a version property referenced by more than one dependency
	 * declaration.
	 */
	public static final Icon SHARED_PROPERTY = load("/META-INF/icons/sharedProperty.svg");

	/**
	 * Maven dependency upgrade icon.
	 */
	public static final Icon UPGRADE_MAVEN_ICON = load("/META-INF/icons/maven/upgrade-mavenProject.svg");

	/**
	 * TOML catalog navigation gutter icon.
	 */
	public static final Icon TOML_NAVIGATE = load("/META-INF/icons/gradle/tomlNavigate.svg");

	/**
	 * Gradle dependency upgrade icon.
	 */
	public static final Icon UPGRADE_GRADLE_ICON = load("/META-INF/icons/gradle/upgrade-gradle.svg");

	/**
	 * TOML version catalog upgrade icon.
	 */
	public static final Icon UPGRADE_TOML_ICON = load("/META-INF/icons/gradle/upgrade-toml.svg");

	/**
	 * Library dependency upgrade icon.
	 */
	public static final Icon UPGRADE_LIBRARY_ICON = load("/META-INF/icons/upgrade-library.svg");

	/**
	 * NPM dependency upgrade icon.
	 */
	public static final Icon UPGRADE_NPM_ICON = load("/META-INF/icons/npm/upgrade-npm.svg");

	/**
	 * NPM icon.
	 */
	public static final Icon NPM = load("/META-INF/icons/npm/npm.svg");

	/**
	 * GitHub dependency upgrade icon.
	 */
	public static final Icon UPGRADE_GITHUB_ICON = load("/META-INF/icons/github/upgrade-github.svg");

	/**
	 * Dependency has active rule.
	 */
	public static final Icon DEPENDENCY_RULE = load("/META-INF/icons/dependencyRule.svg");

	public static final Icon RULE_COMPLIANT = ((ScalableIcon) AllIcons.Actions.InlaySecuredShield).scale(1.3f);

	/**
	 * Dependency rule violated.
	 */
	public static final Icon DEPENDENCY_RULE_WARN = load("/META-INF/icons/dependencyRuleWarn.svg");

	/**
	 * Dependency is locked to a version/generation.
	 */
	public static final Icon DEPENDENCY_LOCK = load("/META-INF/icons/dependencyLock.svg");

	/**
	 * Upgrade Plan: apply all planned upgrades (monotone variant of the platform's
	 * run-all icon).
	 */
	public static final Icon PLAN_APPLY_ALL = load("/META-INF/icons/plan/applyAll.svg");

	/**
	 * Upgrade Plan: create tickets for planned upgrades (stacked all-mode variant).
	 */
	public static final Icon PLAN_CREATE_TICKETS = load("/META-INF/icons/plan/createTickets.svg");

	/**
	 * Upgrade Plan: milestone selector (GitHub Octicon "milestone").
	 */
	public static final Icon PLAN_MILESTONE = load("/META-INF/icons/plan/milestone.svg");

	/**
	 * Upgrade Plan: label selector (GitHub Octicon "tag").
	 */
	public static final Icon PLAN_LABEL = load("/META-INF/icons/plan/label.svg");

	/**
	 * Upgrade Plan tool window.
	 */
	public static final Icon TOOL_WINDOW_UPGRADE_PLAN = load("/META-INF/icons/plan/toolWindow.svg");

	public static final Icon PLAN_MILESTONE_OPEN = load("/META-INF/icons/plan/milestone-open.svg");

	public static final Icon PLAN_MILESTONE_CLOSED = load("/META-INF/icons/plan/milestone-closed.svg");

	/**
	 * Upgrade Plan: open milestone without a due date (lighter gray variant).
	 */
	public static final Icon PLAN_MILESTONE_UNSCHEDULED = load("/META-INF/icons/plan/milestone-unscheduled.svg");

	/**
	 * Compose a dependency upgrade icon: the assistant gutter icon scaled into the
	 * lower-left corner with the target status overlay layered onto the lower-right
	 * quadrant.
	 * @param dependencyIcon the assistant gutter icon.
	 * @param overlayIcon the target status overlay icon.
	 * @return the layered upgrade icon.
	 */
	public static Icon upgradeIcon(Icon dependencyIcon, Icon overlayIcon) {

		LayeredIcon icon = new LayeredIcon(2);
		Icon scaled = IconUtil.scale(dependencyIcon, null, UPGRADE_ICON_SCALE);
		icon.setIcon(scaled, 0, dependencyIcon.getIconHeight() - scaled.getIconHeight(), 0);
		icon.setIcon(IconUtil.scale(overlayIcon, null, UPGRADE_ICON_SCALE), 1, dependencyIcon.getIconWidth() / 2,
				dependencyIcon.getIconHeight() / 2);
		return icon;
	}

	private static Icon load(String path) {
		return IconLoader.getIcon(path, DependencyAssistantIcons.class.getClassLoader());
	}

}
