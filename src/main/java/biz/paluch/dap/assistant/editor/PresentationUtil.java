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

package biz.paluch.dap.assistant.editor;

import biz.paluch.dap.assistant.presentation.DependencyPresentation;
import biz.paluch.dap.metadata.ProjectName;

class PresentationUtil {

	public static String getDependencyName(DependencyPresentation presentation) {
		ProjectName projectName = presentation.getProjectName();
		if (presentation.hasDependencyName()) {
			return presentation.getDependencyName();
		}

		if (projectName.hasDisplayName()) {
			return projectName.getDisplayName();
		}
		return presentation.getDisplayName();
	}

}
