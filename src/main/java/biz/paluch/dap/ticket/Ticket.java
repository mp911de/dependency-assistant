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

import java.net.URI;
import java.util.List;

/**
 * User-facing ticket exposed by the connected ticket system.
 *
 * <p>A ticket is an implementation-owned handle returned by a
 * {@link TicketRepository}. It exposes display and planning data while keeping
 * assignment identifiers and other remote-system keys private. Use
 * {@link #getKey()} for the portable identifier and let {@link TicketSystem}
 * render that key for IDE text or commit messages.
 *
 * <p>Mutating tickets, including closing them, is outside this API.
 *
 * @author Mark Paluch
 * @see TicketRepository
 * @see TicketKey
 */
public interface Ticket {

	/**
	 * Return the portable key identifying this ticket in user-facing contexts.
	 *
	 * @return the portable ticket key.
	 */
	TicketKey getKey();

	/**
	 * Return the ticket title.
	 *
	 * @return the ticket title.
	 */
	String getTitle();

	/**
	 * Return the lifecycle state reported for this ticket.
	 *
	 * @return the ticket state.
	 */
	TicketState getState();

	/**
	 * Return the browser link for this ticket.
	 *
	 * @return the browser link for this ticket.
	 */
	URI getWebLink();

	/**
	 * Return the milestones this ticket is attached to.
	 *
	 * <p>Milestones attached to a ticket can be closed, even though
	 * {@link TicketRepository#getMilestones(com.intellij.openapi.progress.ProgressIndicator)}
	 * lists open milestones only.
	 *
	 * @return the milestones this ticket is attached to; empty if none.
	 */
	List<Milestone> getMilestones();

	/**
	 * Return the labels attached to this ticket.
	 *
	 * @return the labels attached to this ticket; empty if none.
	 */
	List<Label> getLabels();

}
