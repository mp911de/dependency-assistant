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
import java.util.concurrent.atomic.AtomicInteger;

import biz.paluch.dap.notify.NotificationChannel;
import biz.paluch.dap.notify.Notifications;
import biz.paluch.dap.ticket.Ticket;
import biz.paluch.dap.ticket.TicketRepository;
import biz.paluch.dap.ticket.TicketState;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StepsProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;

/**
 * Find existing upgrade tickets in the background.
 * <p>The first failure stops pending work. In-flight items may still link
 * tickets, and successful work is reported with the failure.
 *
 * @author Mark Paluch
 */
class FindAndLinkUpgradeTickets extends TicketTaskSupport {

	private final AtomicInteger found = new AtomicInteger();

	private final AtomicInteger failed = new AtomicInteger();

	private final UpgradePlanService service;

	FindAndLinkUpgradeTickets(UpgradePlanService service, List<PlannedUpgrade> items) {

		super(service, items, MessageBundle.message("plan.tickets.search.progress"));
		this.service = service;
	}

	@Override
	void doRun(TicketRepository repository, List<TicketState> openStates, StepsProgressIndicator indicator,
			PlannedUpgrade item) {

		String title = service.getTicketTitle(item);
		try {
			indicator.checkCanceled();
			indicator.setText(MessageBundle.message("plan.tickets.search.progress.item", item.getDisplayName(),
					item.getToVersion()));

			Ticket ticket = findExisting(title, openStates, indicator);
			if (ticket != null) {
				found.incrementAndGet();
				service.linkTicket(item, repository, ticket);
			}

		} catch (ProcessCanceledException e) {
			throw e;
		} catch (IOException | RuntimeException e) {
			failed.incrementAndGet();
			log.warn("Failed to create or find ticket for " + title, e);
			throw new TicketFailure(e, title);
		} finally {
			indicator.nextStep();
		}
	}

	@Override
	public void onSuccess() {
		notifySummary();
	}

	private void notifySummary() {
		Notifications.info(NotificationChannel.PLAN, MessageBundle.message("plugin.name"), getSummary())
				.notify(service.getProject());
	}

	@Override
	public void onThrowable(Throwable error) {

		log.warn("Ticket search failed", error);

		String title = MessageBundle.message("plan.tickets.search.error.title", getTicketSystem().getDisplayName());

		if (error instanceof TicketFailure failure) {
			HtmlBuilder builder = new HtmlBuilder();
			builder.append(
					HtmlChunk.p()
							.addText(MessageBundle.message("plan.tickets.search.error.detail", failure.getTicket())));
			builder.append(HtmlChunk.p().addText(Notifications.errorMessage(failure)));

			Notifications.error(NotificationChannel.PLAN, title, builder.toString()).notify(service.getProject());
		} else {
			Notifications.error(NotificationChannel.PLAN, title, Notifications.errorMessage(error))
					.notify(service.getProject());
		}

		// items in flight when the failure hit still completed, report them
		if (found.get() > 0) {
			notifySummary();
		}
	}

	@Override
	public void onFinished() {
		service.setBusy(false);
	}

	private String getSummary() {

		int found = this.found.get();
		int failed = this.failed.get();
		String message;
		if (found > 0) {
			message = MessageBundle.message("plan.tickets.summary.found", found);
		} else {
			message = MessageBundle.message("plan.tickets.summary.not-found");
		}

		return failed > 0 ? message + MessageBundle.message("plan.tickets.summary.failed", failed) : message;
	}

}
