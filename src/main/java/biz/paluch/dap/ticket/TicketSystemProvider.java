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

package biz.paluch.dap.ticket;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * SPI for binding IntelliJ projects to external ticket systems.
 *
 * <p>Providers are stateless: the platform may share a single instance across
 * projects and threads.
 *
 * <p>Each created {@code TicketSystem} forms a separate object boundary. Ticket
 * objects and repository-owned values obtained through one system are not valid
 * inputs to another system or repository.
 *
 * @author Mark Paluch
 * @see TicketSystem
 */
public interface TicketSystemProvider {

	/**
	 * Extension point through which ticket system providers are contributed.
	 */
	ExtensionPointName<TicketSystemProvider> EP_NAME = ExtensionPointName.create("biz.paluch.dap.ticketSystem");

	/**
	 * Find the ticket system bound to the given project by consulting the
	 * registered providers in order and creating the first that supports the
	 * project.
	 *
	 * <p>Provider resolution may access credentials and block. A selected
	 * provider's creation failure is propagated; later providers are not consulted.
	 * The returned system's repository operations may also block and belong on
	 * background work.
	 *
	 * @param project the project to resolve against.
	 * @return the bound ticket system, or {@literal null} when no provider supports
	 * the project.
	 * @throws IllegalStateException if the selected provider cannot create its
	 * ticket-system binding.
	 */
	static @Nullable TicketSystem find(Project project) {

		for (TicketSystemProvider provider : EP_NAME.getExtensionList()) {
			if (provider.supports(project)) {
				return provider.create(project);
			}
		}

		return null;
	}

	/**
	 * Determine whether this provider can create a usable ticket system for the
	 * given project.
	 *
	 * <p>Returning {@literal true} selects this provider without consulting later
	 * providers. This method may access credentials and block.
	 *
	 * @param project the project to probe.
	 * @return {@literal true} if {@link #create(Project)} may be called;
	 * {@literal false} otherwise.
	 */
	boolean supports(Project project);

	/**
	 * Create the ticket system bound to the given project's resolved target.
	 *
	 * <p>Callers must invoke this method only after {@link #supports(Project)}
	 * returned {@literal true}. This method may access credentials and block.
	 *
	 * @param project the project to bind against.
	 * @return the project-scoped ticket system.
	 * @throws IllegalStateException if {@link #supports(Project)} would return
	 * {@literal false} for the project.
	 * @see #supports(Project)
	 */
	TicketSystem create(Project project);

}
