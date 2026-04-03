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

/**
 * Icon utility class for Dependency Assistant icons.
 */
public class DependencyAssistantIcons {

	public static final Icon ICON = load("/META-INF/pluginIcon.svg");
	public static final Icon DISABLED_ICON = IconLoader.getDisabledIcon(ICON);

	public static final Icon MAVEN_ICON = load("/META-INF/upgrade-maven.svg");
	public static final Icon MAVEN_TRANSPARENT_ICON = IconLoader.getTransparentIcon(MAVEN_ICON, 1);

	public static final Icon GRADLE_ICON = load("/META-INF/upgrade-gradle.svg");
	public static final Icon GRADLE_TRANSPARENT_ICON = IconLoader.getTransparentIcon(GRADLE_ICON, 1);

	private static Icon load(String path) {
		return IconLoader.getIcon(path, DependencyAssistantIcons.class.getClassLoader());
	}

}
