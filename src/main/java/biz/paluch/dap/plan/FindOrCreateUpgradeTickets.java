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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.Ticket;
import biz.paluch.dap.ticket.TicketRepository;
import biz.paluch.dap.ticket.TicketState;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.WeightedStepsProgressIndicator;
import com.intellij.concurrency.virtualThreads.IntelliJVirtualThreads;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jspecify.annotations.Nullable;

/**
 * Background task to find or create dependency upgrade tickets. Pending plan
 * items are processed by at most four concurrent virtual-thread workers.
 *
 * @author Mark Paluch
 */
class FindOrCreateUpgradeTickets extends Task.Backgroundable {

	private static final Logger LOG = Logger.getInstance(FindOrCreateUpgradeTickets.class);

	private static final int MAX_CONCURRENT_TASKS = 4;

	private static final ThreadFactory THREAD_FACTORY = IntelliJVirtualThreads.ofVirtual()
			.name("DependencyAssistant")
			.factory();

	private final UpgradePlanService service;

	private final TicketSystem ticketSystem;

	private final List<Milestone> milestones;

	private final List<Label> labels;

	private final List<UpgradePlanItem> items;

	private final AtomicInteger created = new AtomicInteger();

	private final AtomicInteger found = new AtomicInteger();

	private final AtomicInteger failed = new AtomicInteger();

	FindOrCreateUpgradeTickets(UpgradePlanService service, TicketSystem ticketSystem, List<Milestone> milestones,
			List<Label> labels, List<UpgradePlanItem> items) {

		super(service.getProject(), MessageBundle.message("plan.tickets.progress"), true);
		this.service = service;
		this.ticketSystem = ticketSystem;
		this.milestones = milestones;
		this.labels = labels;
		this.items = items;
	}

	void start() {

		if (service.isBusy() || items.stream()
				.allMatch(UpgradePlanItem::hasTicket)) {
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

		List<UpgradePlanItem> pending = items.stream()
				.filter(item -> !item.hasTicket()).toList();
		if (pending.isEmpty()) {
			return;
		}

		WeightedStepsProgressIndicator steps = WeightedStepsProgressIndicator.forTasks(indicator, pending.size());
		TicketRepository repository = ticketSystem.getRepository();
		List<TicketState> openStates = getOpenStates(repository, indicator);
		AtomicReference<Throwable> taskFailure = new AtomicReference<>();

		try (ExecutorService virtualExecutor = Executors.newThreadPerTaskExecutor(THREAD_FACTORY);
				ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
						"Find or Create Upgrade Tickets", virtualExecutor,
						MAX_CONCURRENT_TASKS)) {

			for (UpgradePlanItem item : pending) {
				executor.execute(() -> findOrCreate(repository, openStates, steps, item, taskFailure));
			}
		}

		ExceptionUtil.rethrowAllAsUnchecked(taskFailure.get());
	}

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

	private void findOrCreate(TicketRepository repository, List<TicketState> openStates,
			WeightedStepsProgressIndicator indicator, UpgradePlanItem item, AtomicReference<Throwable> taskFailure) {

		if (taskFailure.get() != null) {
			indicator.nextStep();
			return;
		}

		String title = service.getTicketTitle(item);
		try {
			indicator.checkCanceled();
			indicator
					.setText(MessageBundle.message("plan.tickets.progress.item", item.getDisplayName(),
							item.getToVersion()));

			Ticket ticket = findExisting(repository, openStates, indicator, title);
			if (ticket != null) {
				found.incrementAndGet();
			} else {
				ticket = createTicket(repository, indicator, title);
				created.incrementAndGet();
			}
			Ticket linkedTicket = ticket;
			ApplicationManager.getApplication()
					.invokeAndWait(() -> service.linkTicket(item, repository, linkedTicket));
		} catch (ProcessCanceledException e) {
			throw e;
		} catch (IOException | RuntimeException e) {
			failed.incrementAndGet();
			LOG.warn("Failed to create or find ticket for " + title, e);
			taskFailure.compareAndSet(null, e);
		} finally {
			indicator.nextStep();
		}
	}

	@Override
	public void onSuccess() {
		new PlanNotifications().info(service.getProject(), summary());
	}

	private String summary() {

		int created = this.created.get();
		int found = this.found.get();
		int failed = this.failed.get();
		String message;
		if (created > 0 && found > 0) {
			message = MessageBundle.message("plan.tickets.summary.created-and-found", created, found);
		} else if (found > 0) {
			message = MessageBundle.message("plan.tickets.summary.found", found);
		} else {
			message = MessageBundle.message("plan.tickets.summary.created", created);
		}

		return failed > 0 ? message + MessageBundle.message("plan.tickets.summary.failed", failed) : message;
	}

	@Override
	public void onThrowable(Throwable error) {

		LOG.warn("Ticket creation failed", error);
		new PlanNotifications().error(service.getProject(), MessageBundle.message("plan.tickets.error"), error);
	}

	@Override
	public void onFinished() {
		service.setBusy(false);
	}

	private static @Nullable Ticket findExisting(TicketRepository repository, List<TicketState> openStates,
			ProgressIndicator indicator, String title) throws IOException {

		List<? extends Ticket> existing = repository.findTickets(indicator,
				query -> query.title(title).state(openStates));

		for (Ticket ticket : existing) {
			if (ticket.getState().isOpen()) {
				return ticket;
			}
		}

		return null;
	}

	private Ticket createTicket(TicketRepository repository, ProgressIndicator indicator, String title)
			throws IOException {

		return repository.createTicket(indicator, title, spec -> {
			for (Milestone milestone : milestones) {
				spec.milestone(milestone);
			}
			spec.label(labels);
		});
	}

}
