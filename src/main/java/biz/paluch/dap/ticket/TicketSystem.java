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

package biz.paluch.dap.ticket;

/**
 * Project-scoped access point for an external ticket system.
 *
 * <p>A {@code TicketSystem} is bound to one resolved target, for example a
 * GitHub repository or a Jira project, so repository operations only need
 * ticket-domain arguments. Implementations are contributed through
 * {@link TicketSystemProvider}.
 *
 * <p>This bound target is also the object boundary. A {@link Ticket},
 * {@link Milestone}, {@link Label}, or {@link TicketState} obtained from this
 * system's {@link TicketRepository} is valid only with that repository and
 * system. Such instances must not be passed to another {@code TicketSystem},
 * even if that system addresses the same external service.
 *
 * <p>The system also owns ticket reference rendering. Rendering accepts a plain
 * {@link TicketKey}, not a live {@link Ticket}, so persisted plan items can
 * render IDE text and commit-message references without fetching the ticket.
 * Rendering is string formatting and must not perform network IO.
 *
 * @author Mark Paluch
 * @see TicketSystemProvider
 * @see TicketRepository
 */
public interface TicketSystem {

	/**
	 * Return the repository bound to this ticket system target.
	 *
	 * @return the repository for searching, creating, and listing tickets.
	 */
	TicketRepository getRepository();

	/**
	 * Return the display reference for the given ticket key.
	 *
	 * <p>Examples include {@code #1234} on GitHub and {@code PROJ-123} on Jira.
	 *
	 * @param key the persisted or live ticket key to render.
	 * @return the user-facing ticket reference for IDE presentation.
	 */
	String getDisplayReference(TicketKey key);

	/**
	 * Return the commit-message reference for the given ticket key.
	 *
	 * <p>Systems with close keywords may return a closing phrase, for example
	 * {@code Closes #1234}. Systems without commit-based closing can return a plain
	 * display reference; an empty string means that no meaningful commit reference
	 * exists for the key.
	 *
	 * @param key the persisted or live ticket key to render.
	 * @return the commit-message fragment for referencing the ticket.
	 */
	String getCloseReference(TicketKey key);

}
