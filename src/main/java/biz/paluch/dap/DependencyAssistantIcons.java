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

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.LayeredIcon;

/**
 * Icon utility class for Dependency Assistant icons.
 *
 * @author Mark Paluch
 */
public class DependencyAssistantIcons {

	/**
	 * Main Dependency Assistant icon.
	 */
	public static final Icon ICON = load("/META-INF/dependency-assistant.svg");

	/**
	 * Property navigation gutter icon.
	 */
	public static final Icon PROPERTY_NAVIGATE = load("/META-INF/icons/propertyNavigate.svg");

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

	/**
	 * Dependency rule violated.
	 */
	public static final Icon DEPENDENCY_RULE_WARN = load("/META-INF/icons/dependencyRuleWarn.svg");

	/**
	 * Dependency is locked to a version/generation.
	 */
	public static final Icon DEPENDENCY_LOCK = load("/META-INF/icons/dependencyLock.svg");

	/**
	 * Compose a dependency upgrade icon: the assistant gutter icon scaled into the
	 * lower-left corner with the version-age badge layered onto the lower-right
	 * quadrant.
	 * @param dependencyIcon the assistant gutter icon; must not be {@literal null}.
	 * @param ageIcon the version-age badge icon; must not be {@literal null}.
	 * @return the layered upgrade icon.
	 */
	public static Icon upgradeIcon(Icon dependencyIcon, Icon ageIcon) {

		LayeredIcon icon = new LayeredIcon(2);
		Icon scaled = ((ScalableIcon) dependencyIcon).scale(0.7f);
		icon.setIcon(scaled, 0, dependencyIcon.getIconHeight() - scaled.getIconHeight(), 0);
		icon.setIcon(((ScalableIcon) ageIcon).scale(0.7f), 1, dependencyIcon.getIconWidth() / 2,
				dependencyIcon.getIconHeight() / 2);
		return icon;
	}

	private static Icon load(String path) {
		return IconLoader.getIcon(path, DependencyAssistantIcons.class.getClassLoader());
	}

}
