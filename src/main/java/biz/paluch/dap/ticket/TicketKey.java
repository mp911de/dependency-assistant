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

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/**
 * Portable identifier of a ticket.
 *
 * <p>A {@code TicketKey} holds the value users recognize inside the ticket
 * system, for example {@code 1234} on GitHub or {@code PROJ-123} on Jira. It
 * deliberately does not include display adornments such as {@code #} or commit
 * close keywords.
 *
 * <p>Unlike the entity interfaces of this package, a key is constructible and
 * persistable. A key stored with a plan item can be handed back to the
 * {@link TicketSystem}, for example to
 * {@link TicketSystem#getCloseReference(TicketKey)}, without fetching the
 * ticket first. Two keys are equal when their identifier values are equal.
 *
 * @author Mark Paluch
 * @see TicketSystem#getDisplayReference(TicketKey)
 * @see TicketSystem#getCloseReference(TicketKey)
 */
public class TicketKey {

	private final String value;

	private TicketKey(String value) {
		this.value = value;
	}

	/**
	 * Create a ticket key from its identifier value.
	 *
	 * @param value the identifier value as used by the ticket system.
	 * @return the ticket key.
	 * @throws IllegalArgumentException if {@code value} is empty or blank.
	 */
	public static TicketKey of(String value) {

		Assert.hasText(value, "Ticket key value must not be empty");
		return new TicketKey(value);
	}

	/**
	 * Return the identifier value.
	 *
	 * @return the identifier value.
	 */
	public String getValue() {
		return value;
	}

	@Override
	public boolean equals(@Nullable Object o) {

		if (!(o instanceof TicketKey ticketKey)) {
			return false;
		}

		return value.equals(ticketKey.value);
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public String toString() {
		return value;
	}

}
