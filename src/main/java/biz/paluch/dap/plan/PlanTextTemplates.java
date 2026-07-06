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

package biz.paluch.dap.plan;

import java.io.IOException;
import java.util.Properties;

import biz.paluch.dap.util.MessageBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.project.Project;

/**
 * Renders user-editable Upgrade Plan ticket and commit text from the project's
 * File and Code Templates scheme.
 *
 * @author Mark Paluch
 */
class PlanTextTemplates {

	private final FileTemplateManager manager;

	PlanTextTemplates(Project project) {
		this.manager = FileTemplateManager.getInstance(project);
	}

	String ticketTitle(UpgradePlanItem item) {
		return render(UpgradePlanTemplateGroup.TICKET_TEMPLATE, "plan.template.ticket", item);
	}

	String commitMessage(UpgradePlanItem item) {
		return render(UpgradePlanTemplateGroup.COMMIT_TEMPLATE, "plan.template.commit", item);
	}

	private String render(String templateName, String messageKey, UpgradePlanItem item) {

		Properties properties = new Properties();
		properties.setProperty("DEPENDENCY", item.getDisplayName());
		properties.setProperty("FROM_VERSION", item.getFromVersion().toString());
		properties.setProperty("TO_VERSION", item.getToVersion().toString());

		FileTemplate template = manager.getJ2eeTemplate(templateName);
		try {
			return template.getText(properties).stripTrailing();
		} catch (IOException e) {
			throw new IllegalStateException(MessageBundle.message("plan.template.render.error",
					MessageBundle.message(messageKey)), e);
		}
	}

}
