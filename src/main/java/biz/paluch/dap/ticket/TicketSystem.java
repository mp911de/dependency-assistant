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

/**
 * Access to one bound ticket target. Values obtained from its repository must
 * not be passed to another system, even on the same external service.
 *
 * <p>Reference rendering uses portable keys so persisted plan items need no
 * ticket fetch. Rendering must not perform network access.
 *
 * @author Mark Paluch
 * @see Ticket
 * @see TicketSystemProvider
 * @see TicketRepository
 */
public interface TicketSystem {

	/**
	 * The display name of the ticket system.
	 */
	String getDisplayName();

	/**
	 * Return the repository that contains the tickets.
	 */
	TicketRepository getRepository();

	/**
	 * Render a ticket key for IDE display, for example {@code #1234}.
	 */
	String getDisplayReference(TicketKey key);

	/**
	 * Render a commit reference, using a closing phrase where supported. Return an
	 * empty string if no meaningful reference exists.
	 */
	String getCloseReference(TicketKey key);

}
