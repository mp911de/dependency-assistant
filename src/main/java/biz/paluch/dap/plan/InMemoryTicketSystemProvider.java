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

import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.ticket.TicketSystemProvider;
import com.intellij.ide.util.ProjectPropertyService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;

/**
 * Development/offline {@link TicketSystemProvider} backing the Upgrade Plan with
 * the {@link InMemoryTicketRepository}. Always supports so the ticket workflow can
 * be exercised without a live ticket system. Not for production use.
 *
 * @author Mark Paluch
 */
public class InMemoryTicketSystemProvider implements TicketSystemProvider {

	@Override
	public boolean supports(Project project) {
		PropertiesComponent properties = ProjectPropertyService.getInstance(project);
		boolean enabled = properties.getBoolean("InMemoryTicketSystemProvider.enabled", false);
		return enabled;
	}

	@Override
	public TicketSystem create(Project project) {
		return new InMemoryTicketSystem();
	}

}
