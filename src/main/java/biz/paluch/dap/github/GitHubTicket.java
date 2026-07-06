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

package biz.paluch.dap.github;

import java.net.URI;
import java.util.List;

import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.Ticket;
import biz.paluch.dap.ticket.TicketKey;
import biz.paluch.dap.ticket.TicketState;

/**
 * GitHub issue exposed as a {@link Ticket}, keyed by the issue number.
 *
 * @author Mark Paluch
 */
class GitHubTicket implements Ticket {

	private final TicketKey key;

	private final String title;

	private final GitHubTicketState state;

	private final URI webLink;

	private final List<Milestone> milestones;

	private final List<Label> labels;

	GitHubTicket(TicketKey key, String title, GitHubTicketState state, URI webLink, List<Milestone> milestones,
			List<Label> labels) {
		this.key = key;
		this.title = title;
		this.state = state;
		this.webLink = webLink;
		this.milestones = milestones;
		this.labels = labels;
	}

	@Override
	public TicketKey getKey() {
		return key;
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public TicketState getState() {
		return state;
	}

	@Override
	public URI getWebLink() {
		return webLink;
	}

	@Override
	public List<Milestone> getMilestones() {
		return milestones;
	}

	@Override
	public List<Label> getLabels() {
		return labels;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof GitHubTicket that)) {
			return false;
		}
		return key.equals(that.key);
	}

	@Override
	public int hashCode() {
		return key.hashCode();
	}

	@Override
	public String toString() {
		return "%s %s".formatted(key, title);
	}

}
