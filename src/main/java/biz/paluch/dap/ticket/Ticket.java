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
 * Repository-owned ticket data. Persist {@link #getKey()} with its system
 * association to render references without fetching the ticket again.
 *
 * @author Mark Paluch
 * @see TicketRepository
 */
public interface Ticket {

	TicketKey getKey();

	String getTitle();

	TicketState getState();

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
