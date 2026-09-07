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

package biz.paluch.dap.github;

import java.util.Properties;

import biz.paluch.dap.assistant.AssistantTemplateGroup;
import biz.paluch.dap.ticket.TicketKey;
import biz.paluch.dap.util.TextTemplates;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.api.GHRepositoryPath;

/**
 * Renders GitHub issue references for one repository. Display references use
 * the fixed {@code #number} form. Close references render the editable
 * {@link AssistantTemplateGroup#GITHUB_CLOSE_REFERENCE_TEMPLATE}.
 *
 * @author Mark Paluch
 */
class GitHubConventions {

	private final TextTemplates templates;

	private final GHRepositoryPath path;

	GitHubConventions(Project project, GHRepositoryPath path) {
		this.templates = TextTemplates.getInstance(project);
		this.path = path;
	}

	public String getDisplayReference(TicketKey key) {
		return "#" + key;
	}

	public String getCloseReference(TicketKey key) {

		Properties properties = new Properties();
		properties.setProperty("NUMBER", key.getValue());
		properties.setProperty("OWNER", path.getOwner());
		properties.setProperty("REPOSITORY", path.getRepository());
		properties.setProperty("REFERENCE", getDisplayReference(key));
		properties.setProperty("FULL_REFERENCE",
				"%s/%s%s".formatted(path.getOwner(), path.getRepository(), getDisplayReference(key)));

		return templates.render(AssistantTemplateGroup.GITHUB_CLOSE_REFERENCE_TEMPLATE, properties).stripTrailing()
				.trim();
	}

}
