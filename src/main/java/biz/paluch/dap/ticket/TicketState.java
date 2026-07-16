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
 * Lifecycle state used for tickets and query filters.
 *
 * <p>Instances are implementation-owned and obtained through
 * {@link TicketRepository#getTicketStates(com.intellij.openapi.progress.ProgressIndicator)}
 * or {@link Ticket#getState()}. Implementations may map these values to static
 * states, such as open and closed, or to server-defined workflow states. The
 * portable contract only asks whether the state counts as open or closed.
 *
 * @author Mark Paluch
 * @see TicketRepository#getTicketStates(com.intellij.openapi.progress.ProgressIndicator)
 */
public interface TicketState {

	/**
	 * Return whether a ticket in this state counts as open.
	 *
	 * @return {@literal true} if the state counts as open; {@literal false}
	 * otherwise.
	 */
	boolean isOpen();

	/**
	 * Return whether a ticket in this state counts as closed.
	 *
	 * <p>The default implementation treats every non-open state as closed.
	 *
	 * @return {@literal true} if the state counts as closed; {@literal false}
	 * otherwise.
	 */
	default boolean isClosed() {
		return !isOpen();
	}

}
