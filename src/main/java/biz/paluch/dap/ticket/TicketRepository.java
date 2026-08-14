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

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import com.intellij.openapi.progress.ProgressIndicator;

/**
 * Repository-like container of tickets inside a ticket system.
 *
 * <p>A repository represents one system target, for example a GitHub
 * repository, a GitLab project, or a Jira project. It is obtained from a
 * {@link TicketSystem} and supplies the ticket objects, milestones, labels, and
 * states accepted by query and creation operations.
 *
 * <p>{@link Ticket}, {@link Milestone}, {@link Label}, and {@link TicketState}
 * instances returned here are implementation-owned. Callers may display their
 * user-facing data and pass them back to the same repository, but must not
 * synthesize foreign instances for filtering or assignment.
 *
 * <p>Search and create are separate operations by design. Reuse-by-title,
 * default milestone or label selection, and open-state filtering are caller
 * policy. The repository never closes or mutates existing tickets; closing is
 * expressed later through commit messages via
 * {@link TicketSystem#getCloseReference(TicketKey)}.
 *
 * <p>Operations may perform blocking network IO and should be invoked from
 * cancelable background work.
 *
 * @author Mark Paluch
 * @see TicketSystem#getRepository()
 */
public interface TicketRepository {

	/**
	 * Find tickets matching the configured query.
	 *
	 * <p>Criteria combine as AND; values within one criterion combine as OR. An
	 * empty criterion is unconstrained.
	 *
	 * @param indicator progress indicator used to observe cancellation.
	 * @param query callback that configures titles, states, milestones, and labels
	 * before the search.
	 * @return all matching tickets, or an empty list if none match.
	 * @throws IOException if the ticket system cannot be reached or rejects the
	 * search.
	 */
	List<? extends Ticket> findTickets(ProgressIndicator indicator, Consumer<TicketQuery> query) throws IOException;

	/**
	 * Create a ticket with the given title and specification.
	 *
	 * <p>The title is positional so ticket creation cannot proceed without it.
	 * Description, milestone, and labels are optional parts of the
	 * {@link TicketSpec}.
	 *
	 * @param indicator progress indicator used to observe cancellation.
	 * @param title the ticket title.
	 * @param spec callback that configures the description, milestone, and labels
	 * before creation.
	 * @return the newly created ticket.
	 * @throws IOException if the ticket system cannot be reached or rejects the
	 * creation.
	 */
	Ticket createTicket(ProgressIndicator indicator, String title, Consumer<TicketSpec> spec) throws IOException;

	/**
	 * Return the states that can be used to filter this repository's tickets.
	 *
	 * @param indicator progress indicator used to observe cancellation.
	 * @return the materialized ticket states known to this repository.
	 * @throws IOException if the states are server-defined and the ticket system
	 * cannot be reached.
	 */
	List<? extends TicketState> getTicketStates(ProgressIndicator indicator) throws IOException;

	/**
	 * Return the open milestones of this repository.
	 *
	 * @param indicator progress indicator used to observe cancellation.
	 * @return the materialized open milestones, or an empty list if the repository
	 * has none.
	 * @throws IOException if the ticket system cannot be reached and no fallback
	 * data is available.
	 */
	List<? extends Milestone> getMilestones(ProgressIndicator indicator) throws IOException;

	/**
	 * Return the labels of this repository.
	 *
	 * @param indicator progress indicator used to observe cancellation.
	 * @return the materialized labels, or an empty list if the repository has none.
	 * @throws IOException if the ticket system cannot be reached and no fallback
	 * data is available.
	 */
	List<? extends Label> getLabels(ProgressIndicator indicator) throws IOException;

	/**
	 * Return a cached version of this repository. Cached repositories are
	 * guaranteed to be thread-safe but not all methods are able to serve requests
	 * from cache.
	 */
	TicketRepository cached();


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
