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
 * Bind IDE projects to ticket systems. Providers must be stateless because the
 * platform may share them across projects and threads.
 *
 * @author Mark Paluch
 * @see TicketSystem
 */
public interface TicketSystemProvider {

	ExtensionPointName<TicketSystemProvider> EP_NAME = ExtensionPointName.create("biz.paluch.dap.ticketSystem");

	/**
	 * Whether this provider can bind the project. Returning {@code true} selects
	 * this provider without considering later providers. May access credentials and
	 * block.
	 */
	boolean supports(Project project);

	/**
	 * Bind the project after {@link #supports(Project)} returned {@code true}. May
	 * access credentials and block.
	 *
	 * @throws IllegalStateException if the project is unsupported.
	 */
	TicketSystem create(Project project);

	/**
	 * Bind through the first supporting provider, or return {@code null} if none
	 * supports the project. Creation failures propagate without trying later
	 * providers. Resolution may access credentials and block.
	 *
	 * @throws IllegalStateException if the selected provider cannot create its
	 * binding.
	 */
	static @Nullable TicketSystem find(Project project) {

		for (TicketSystemProvider provider : EP_NAME.getExtensionList()) {
			if (provider.supports(project)) {
				return provider.create(project);
			}
		}

		return null;
	}

}
