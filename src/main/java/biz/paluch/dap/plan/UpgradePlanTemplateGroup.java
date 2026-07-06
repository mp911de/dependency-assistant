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

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory;

/**
 * Registers the editable Upgrade Plan text templates in the File and Code
 * Templates {@code Other} group.
 *
 * @author Mark Paluch
 */
public class UpgradePlanTemplateGroup implements FileTemplateGroupDescriptorFactory {

	static final String TICKET_TEMPLATE = "Dependency Assistant Upgrade Ticket.txt";

	static final String COMMIT_TEMPLATE = "Dependency Assistant Commit Message.txt";

	@Override
	public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {

		FileTemplateGroupDescriptor group = new FileTemplateGroupDescriptor(MessageBundle.message("plugin.name"),
				DependencyAssistantIcons.ICON);

		group.addTemplate(new FileTemplateDescriptor(TICKET_TEMPLATE, AllIcons.Toolwindows.Task) {

			@Override
			public String getDisplayName() {
				return MessageBundle.message("plan.template.ticket");
			}

		});

		group.addTemplate(new FileTemplateDescriptor(COMMIT_TEMPLATE, AllIcons.Actions.Commit) {

			@Override
			public String getDisplayName() {
				return MessageBundle.message("plan.template.commit");
			}

		});
		return group;
	}

}
