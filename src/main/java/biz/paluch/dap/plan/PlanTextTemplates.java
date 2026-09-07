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

package biz.paluch.dap.plan;

import java.util.Properties;

import biz.paluch.dap.assistant.AssistantTemplateGroup;
import biz.paluch.dap.ticket.TicketKey;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.util.StringUtils;
import biz.paluch.dap.util.TextTemplates;
import com.intellij.openapi.project.Project;

/**
 * Renders user-editable Upgrade Plan ticket and commit text from the project's
 * File and Code Templates scheme.
 *
 * @author Mark Paluch
 */
class PlanTextTemplates {

	private final TextTemplates templates;

	PlanTextTemplates(Project project) {
		this.templates = TextTemplates.getInstance(project);
	}

	String getTicketTitle(UpgradePlanItem item) {
		return templates.render(AssistantTemplateGroup.TICKET_TEMPLATE, getProperties(item)).stripTrailing().trim();
	}

	/**
	 * Render the commit message without ticket references. {@code TICKET} and
	 * {@code CLOSES} render empty.
	 */
	String getCommitMessage(UpgradePlanItem item) {
		return renderCommitMessage(getProperties(item));
	}

	/**
	 * Render the commit message with the linked ticket's references rendered by the
	 * ticket system. {@code TICKET} and {@code CLOSES} render empty without a
	 * linked ticket.
	 */
	String getCommitMessage(UpgradePlanItem item, TicketSystem ticketSystem) {

		TicketKey ticketKey = item.getTicketKey();
		if (ticketKey == null) {
			return getCommitMessage(item);
		}

		return renderCommitMessage(getProperties(item, ticketSystem, ticketKey));
	}

	private String renderCommitMessage(Properties properties) {
		return StringUtils.collapseBlankLines(
				templates.render(AssistantTemplateGroup.COMMIT_TEMPLATE, properties).stripTrailing());
	}

	private static Properties getProperties(UpgradePlanItem item) {
		Properties properties = new Properties();
		properties.setProperty("DEPENDENCY", item.getDisplayName());
		properties.setProperty("FROM_VERSION", item.getFromVersion().toString());
		properties.setProperty("TO_VERSION", item.getToVersion().toString());
		properties.setProperty("TICKET", "");
		properties.setProperty("CLOSES", "");
		return properties;
	}

	private static Properties getProperties(UpgradePlanItem item, TicketSystem ticketSystem, TicketKey ticketKey) {
		Properties properties = getProperties(item);
		properties.setProperty("TICKET", ticketSystem.getDisplayReference(ticketKey));
		properties.setProperty("CLOSES", ticketSystem.getCloseReference(ticketKey));
		return properties;
	}

}
