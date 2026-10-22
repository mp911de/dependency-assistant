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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.Ticket;
import biz.paluch.dap.ticket.TicketRepository;
import biz.paluch.dap.ticket.TicketState;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.util.StepsProgressIndicator;
import biz.paluch.dap.util.TaskScope;
import biz.paluch.dap.util.TaskScope.Subtask;
import biz.paluch.dap.util.WeightedStepsProgressIndicator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.NlsContexts;
import org.jspecify.annotations.Nullable;

/**
 * Support class to interact with upgrade tickets.
 *
 * @author Mark Paluch
 */
abstract class TicketTaskSupport extends Task.Backgroundable {

	private static final int MAX_CONCURRENT_TASKS = 4;

	// visible to subclasses
	protected final Logger log = Logger.getInstance(getClass());

	private final UpgradePlanService service;

	private final TicketSystem ticketSystem;

	private final TicketRepository repository;

	private final List<Milestone> milestones;

	private final List<Label> labels;

	private final List<PlannedUpgrade> items;

	TicketTaskSupport(UpgradePlanService service, List<PlannedUpgrade> items,
			@NlsContexts.ProgressTitle String title) {

		super(service.getProject(), title, true);

		TicketSystem ticketSystem = service.getTicketSystem();

		Label label = service.getLabel();
		List<Label> labels = label != null ? List.of(label) : List.of();
		Milestone milestone = service.getSelectedMilestone();
		List<Milestone> milestones = milestone != null ? List.of(milestone) : List.of();

		this.service = service;
		this.ticketSystem = ticketSystem;
		this.repository = ticketSystem.getRepository();
		this.milestones = milestones;
		this.labels = labels;
		this.items = items;
	}

	public TicketSystem getTicketSystem() {
		return ticketSystem;
	}

	void start() {

		if (service.isBusy() || items.stream()
				.allMatch(PlannedUpgrade::hasTicket)) {
			return;
		}

		service.setBusy(true);
		try {
			queue();
		} catch (RuntimeException e) {
			service.setBusy(false);
			throw e;
		}
	}

	@Override
	public void run(ProgressIndicator indicator) {

		indicator.setIndeterminate(false);

		List<PlannedUpgrade> pending = items.stream()
				.filter(item -> !item.hasTicket()).toList();
		if (pending.isEmpty()) {
			return;
		}

		WeightedStepsProgressIndicator steps = WeightedStepsProgressIndicator.forTasks(indicator, pending.size());

		List<TicketState> openStates = getOpenStates(repository, indicator);
		AtomicReference<TicketFailure> taskFailure = new AtomicReference<>();

		try (TaskScope scope = TaskScope.open("UpgradeTickets", indicator, MAX_CONCURRENT_TASKS)) {

			for (PlannedUpgrade item : pending) {
				scope.fork(() -> {

					if (taskFailure.get() != null) {
						steps.nextStep();
						return null;
					}
					try {
						doRun(repository, openStates, steps, item);
					} catch (TicketFailure e) {
						taskFailure.compareAndSet(null, e);
					} catch (ProcessCanceledException e) {
						throw e;
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					return null;
				});
			}

			scope.joinAll();

			for (Subtask<?> subtask : scope.getSubtasks()) {
				subtask.getOrThrow();
			}
		}

		TicketFailure failure = taskFailure.get();
		if (failure != null) {
			throw failure;
		}
	}

	@Override
	public void onThrowable(Throwable error) {
		log.warn("Ticket creation failed", error);
	}

	@Override
	public void onFinished() {
		service.setBusy(false);
	}

	/**
	 * Perform the task using the given {@link TicketRepository} for the
	 * {@link PlannedUpgrade item}.
	 */
	abstract void doRun(TicketRepository repository, List<TicketState> openStates, StepsProgressIndicator indicator,
			PlannedUpgrade item);

	/**
	 * Return (or load) open ticket states.
	 */
	private static List<TicketState> getOpenStates(TicketRepository repository, ProgressIndicator indicator) {

		List<TicketState> openStates = new ArrayList<>();
		try {
			for (TicketState state : repository.getTicketStates(indicator)) {
				if (state.isOpen()) {
					openStates.add(state);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Cannot load ticket states", e);
		}
		return openStates;
	}

	protected @Nullable Ticket findExisting(String title, List<TicketState> openStates,
			ProgressIndicator indicator) throws IOException {

		List<? extends Ticket> existing = repository.findTickets(indicator,
				query -> query.title(title).state(openStates));

		for (Ticket ticket : existing) {
			if (ticket.getState().isOpen()) {
				return ticket;
			}
		}

		return null;
	}

	protected Ticket createTicket(String title, ProgressIndicator indicator)
			throws IOException {

		return repository.createTicket(indicator, title, spec -> {
			for (Milestone milestone : milestones) {
				spec.milestone(milestone);
			}
			spec.label(labels);
		});
	}

	static class TicketFailure extends RuntimeException {

		private final String ticket;

		public TicketFailure(Throwable cause, String ticket) {
			super(cause);
			this.ticket = ticket;
		}

		public String getTicket() {
			return ticket;
		}

	}

}
