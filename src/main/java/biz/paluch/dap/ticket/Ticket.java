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
 * Ticket data obtained from a bound {@link TicketRepository}.
 *
 * <p>The {@link TicketKey} carries no system or repository identity. Retain its
 * association with the bound {@link TicketSystem} when persisting it. The
 * system can render display and commit references from the key without fetching
 * the ticket again.
 *
 * @author Mark Paluch
 */
public interface Ticket {

	/**
	 * Return the ticket identifier without display adornments or commit keywords.
	 */
	TicketKey getKey();

	/**
	 * Return the ticket title.
	 */
	String getTitle();

	/**
	 * Return the ticket's lifecycle state in its repository.
	 */
	TicketState getState();

	/**
	 * Return the URI for opening the ticket in a web browser.
	 */
	URI getWebLink();

	/**
	 * Return attached milestones, including closed ones. An empty list means none.
	 */
	List<Milestone> getMilestones();

	/**
	 * Return attached labels, or an empty list if none.
	 */
	List<Label> getLabels();

}
