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
 * Tickets within one bound target, such as a repository or project.
 *
 * <p>Returned tickets, milestones, labels, and states belong to this
 * repository. Use these instances for filtering and assignment. Do not supply
 * foreign values.
 *
 * <p>Callers decide whether to reuse existing tickets and which defaults to
 * apply. This API does not modify existing tickets. Operations may block and
 * belong in cancelable background work, except reference rendering, which must
 * remain local.
 *
 * @author Mark Paluch
 * @see TicketSystem#getRepository()
 */
public interface TicketRepository {

	/**
	 * Find tickets using {@link TicketQuery} criteria. An empty list means no
	 * matches.
	 *
	 * @throws IOException if the search fails.
	 */
	List<? extends Ticket> findTickets(ProgressIndicator indicator, Consumer<TicketQuery> query) throws IOException;

	/**
	 * Create a ticket with the supplied optional specification.
	 *
	 * @throws IOException if creation fails.
	 */
	Ticket createTicket(ProgressIndicator indicator, String title, Consumer<TicketSpec> spec) throws IOException;

	/**
	 * Return states available for filtering.
	 *
	 * @throws IOException if server-defined states cannot be retrieved.
	 */
	List<? extends TicketState> getTicketStates(ProgressIndicator indicator) throws IOException;

	/**
	 * Return open milestones, or an empty list if none.
	 *
	 * @throws IOException if retrieval fails and no fallback is available.
	 */
	List<? extends Milestone> getMilestones(ProgressIndicator indicator) throws IOException;

	/**
	 * Return labels, or an empty list if none.
	 *
	 * @throws IOException if retrieval fails and no fallback is available.
	 */
	List<? extends Label> getLabels(ProgressIndicator indicator) throws IOException;

	/**
	 * Return a thread-safe view whose milestone and label listings never refresh
	 * remote data. Empty lists mean no cached data, not necessarily no remote
	 * entries. Search and creation may be unsupported.
	 */
	TicketRepository cached();


	/**
	 * Render a ticket key for IDE display without network access.
	 */
	String getDisplayReference(TicketKey key);

	/**
	 * Render a commit reference without network access. A closing phrase may be
	 * used where supported, otherwise a plain reference. Return an empty string
	 * when no meaningful reference exists.
	 */
	String getCloseReference(TicketKey key);

}
