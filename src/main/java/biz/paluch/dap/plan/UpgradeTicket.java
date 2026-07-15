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

package biz.paluch.dap.plan;

import java.util.Objects;

import biz.paluch.dap.ticket.Ticket;
import biz.paluch.dap.ticket.TicketRepository;

/**
 * Dependency upgrade ticket.
 *
 * @author Mark Paluch
 */
class UpgradeTicket {

	private final String key;

	private final String displayReference;

	private final String url;

	private final String repository;

	UpgradeTicket(TicketRepository repository, Ticket ticket) {
		this(ticket.getKey().getValue(), repository.getDisplayReference(ticket.getKey()),
				ticket.getWebLink().toString(),
				repository.getClass().getName());
	}

	public UpgradeTicket(String key, String displayReference, String url, String repository) {
		this.key = key;
		this.displayReference = displayReference;
		this.url = url;
		this.repository = repository;
	}

	public String getKey() {
		return key;
	}

	public String getDisplayReference() {
		return displayReference;
	}

	public String getUrl() {
		return url;
	}

	public String getRepository() {
		return repository;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof UpgradeTicket that)) {
			return false;
		}
		return Objects.equals(key, that.key) && Objects.equals(url, that.url)
				&& Objects.equals(repository, that.repository);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, url, repository);
	}

	@Override
	public String toString() {
		return displayReference;
	}

}
