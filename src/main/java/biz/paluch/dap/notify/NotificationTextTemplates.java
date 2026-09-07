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

package biz.paluch.dap.notify;

import java.util.Properties;

import biz.paluch.dap.assistant.AppliedUpdate;
import biz.paluch.dap.assistant.AppliedUpdates;
import biz.paluch.dap.assistant.AssistantTemplateGroup;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import biz.paluch.dap.util.TextTemplates;
import com.intellij.openapi.project.Project;

/**
 * Renders user-editable commit text from the project's File and Code Templates
 * scheme. Applied updates carry no ticket, so {@code TICKET} and {@code CLOSES}
 * render empty.
 *
 * @author Mark Paluch
 */
class NotificationTextTemplates {

	private final TextTemplates templates;

	NotificationTextTemplates(Project project) {
		this.templates = TextTemplates.getInstance(project);
	}

	public String getCommitMessage(AppliedUpdates updates) {

		if (updates.size() == 1) {
			return getCommitMessage(updates.first());
		}

		return "%s%n%n%s".formatted(MessageBundle.message("notification.commit.title.many"), updates);
	}

	private String getCommitMessage(AppliedUpdate item) {

		Properties properties = new Properties();
		properties.setProperty("DEPENDENCY", item.displayName());
		properties.setProperty("FROM_VERSION", item.getFromVersion().toString());
		properties.setProperty("TO_VERSION", item.getTargetVersion().toString());
		properties.setProperty("TICKET", "");
		properties.setProperty("CLOSES", "");

		return StringUtils.collapseBlankLines(templates.render(AssistantTemplateGroup.COMMIT_TEMPLATE, properties));
	}

}
