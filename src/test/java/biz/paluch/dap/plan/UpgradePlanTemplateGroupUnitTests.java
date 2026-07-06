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
import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradePlanTemplateGroup}.
 *
 * @author Mark Paluch
 */
class UpgradePlanTemplateGroupUnitTests {

	@Test
	void groupsTicketAndCommitTemplatesWithTheirPlanIcons() {

		FileTemplateGroupDescriptor descriptor = new UpgradePlanTemplateGroup().getFileTemplatesDescriptor();

		assertThat(descriptor.getTitle()).isEqualTo("Dependency Assistant");
		assertThat(descriptor.getIcon()).isSameAs(DependencyAssistantIcons.ICON);
		assertThat(descriptor.getTemplates()).extracting(FileTemplateDescriptor::getDisplayName)
				.containsExactly("Upgrade Ticket", "Commit Message");
		assertThat(descriptor.getTemplates())
				.extracting(FileTemplateDescriptor::getFileName, FileTemplateDescriptor::getIcon)
				.containsExactly(
						tuple("Dependency Assistant Upgrade Ticket.txt", AllIcons.Toolwindows.Task),
						tuple("Dependency Assistant Commit Message.txt", AllIcons.Actions.Commit));
	}

}
