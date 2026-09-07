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

package biz.paluch.dap.util;

import java.io.IOException;
import java.util.Properties;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

/**
 * Renders templates from the project's File and Code Templates scheme. Callers
 * apply any whitespace cleanup to the rendered text.
 *
 * @author Mark Paluch
 */
@Service(Service.Level.PROJECT)
public final class TextTemplates {

	private final FileTemplateManager manager;

	private TextTemplates(Project project) {
		this.manager = FileTemplateManager.getInstance(project);
	}

	public static TextTemplates getInstance(Project project) {
		return project.getService(TextTemplates.class);
	}

	/**
	 * Render the template using the given properties.
	 */
	public String render(String templateName, Properties properties) {
		FileTemplate template = manager.getJ2eeTemplate(templateName);
		try {
			return template.getText(properties);
		} catch (IOException e) {
			throw new IllegalStateException(MessageBundle.message("template.render.error", templateName), e);
		}
	}

}
