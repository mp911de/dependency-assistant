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

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.Ticket;
import biz.paluch.dap.ticket.TicketQuery;
import biz.paluch.dap.ticket.TicketRepository;
import biz.paluch.dap.ticket.TicketSpec;
import biz.paluch.dap.ticket.TicketState;
import com.intellij.openapi.progress.ProgressIndicator;

/**
 * @author Mark Paluch
 */
class TicketRepositoryWrapper implements TicketRepository {

	private final TicketRepository delegate;

	TicketRepositoryWrapper(TicketRepository delegate) {
		this.delegate = delegate;
	}

	@Override
	public List<? extends Ticket> findTickets(ProgressIndicator indicator, Consumer<TicketQuery> query)
			throws IOException {
		return delegate.findTickets(indicator, query);
	}

	@Override
	public Ticket createTicket(ProgressIndicator indicator, String title, Consumer<TicketSpec> spec)
			throws IOException {
		return delegate.createTicket(indicator, title, spec);
	}

	@Override
	public List<? extends TicketState> getTicketStates(ProgressIndicator indicator) throws IOException {
		return delegate.getTicketStates(indicator);
	}

	@Override
	public List<? extends Milestone> getMilestones(ProgressIndicator indicator) throws IOException {
		return delegate.getMilestones(indicator);
	}

	@Override
	public List<? extends Label> getLabels(ProgressIndicator indicator) throws IOException {
		return delegate.getLabels(indicator);
	}

	@Override
	public TicketRepository cached() {
		return delegate.cached();
	}

}
