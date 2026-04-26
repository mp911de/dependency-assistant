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

import javax.swing.*;

import com.intellij.openapi.util.IconLoader;

/**
 * Icon utility class for Dependency Assistant icons.
 */
public class DependencyAssistantIcons {

	public static final Icon ICON = load("/META-INF/dependency-assistant.svg");

	public static final Icon UPGRADE_MAVEN_ICON = load("/META-INF/upgrade-icons/mavenProject.svg");

	public static final Icon UPGRADE_GRADLE_ICON = load("/META-INF/upgrade-icons/gradle.svg");

	public static final Icon UPGRADE_LIBRARY_ICON = load("/META-INF/upgrade-icons/library.svg");

	public static final Icon UPGRADE_NPM_ICON = load("/META-INF/upgrade-icons/npm.svg");

	public static final Icon UPGRADE_GITHUB_ICON = load("/META-INF/upgrade-icons/github.svg");

	public static final Icon UPGRADE_TOML_ICON = load("/META-INF/upgrade-icons/toml.svg");

	// TODO: UPGRADE_PROPERTY

	public static final Icon PROPERTY_NAVIGATE = load("/META-INF/propertyNavigate.svg");

	public static final Icon TOML_NAVIGATE = load("/META-INF/tomlNavigate.svg");

	private static Icon load(String path) {
		return IconLoader.getIcon(path, DependencyAssistantIcons.class.getClassLoader());
	}

}
