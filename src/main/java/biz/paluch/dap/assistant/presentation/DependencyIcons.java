/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.assistant.presentation;

import javax.swing.Icon;

/**
 * Base icon selected by a build-tool integration for a dependency in table
 * views.
 *
 * <p>The icon is integration-selected and can distinguish declaration kind.
 * Version, rule, and security status are rendered separately by the consuming
 * surface.
 *
 * @author Mark Paluch
 * @see IconDependencyPresentation
 */
public interface DependencyIcons {

	/**
	 * Return the integration-selected base icon rendered next to the dependency.
	 *
	 * @return the table icon.
	 */
	Icon getTableIcon();

}
