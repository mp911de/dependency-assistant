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

import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.Ticket;
import biz.paluch.dap.ticket.TicketKey;
import biz.paluch.dap.ticket.TicketQuery;
import biz.paluch.dap.ticket.TicketRepository;
import biz.paluch.dap.ticket.TicketSpec;
import biz.paluch.dap.ticket.TicketState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.TimeoutUtil;
import org.jspecify.annotations.Nullable;

/**
 * Offline, in-memory {@link TicketRepository} used to exercise the Upgrade Plan
 * ticket workflow without a live ticket system. Milestones and labels are fixed
 * catalogs; searches and creation add simulated network latency and keep
 * created tickets in memory for the IDE session only. Not for production use.
 *
 * @author Mark Paluch
 */
public enum InMemoryTicketRepository implements TicketRepository {

	INSTANCE;

	public enum InMemoryState implements TicketState {

		OPEN,
		CLOSED;

		@Override
		public boolean isOpen() {
			return this == OPEN;
		}

	}

	private final Random random = new Random();

	private final AtomicLong sequence = new AtomicLong(4);

	private final List<InMemoryTicket> tickets = Collections.synchronizedList(new ArrayList<>());

	private final List<InMemoryLabel> labels = List.of(
			new InMemoryLabel("type: dependency-upgrade", new Color(0xE3D9FC)),
			new InMemoryLabel("type: bug", new Color(0xE3D9FC)),
			new InMemoryLabel("type: enhancement", new Color(0xE3D9FC)),
			new InMemoryLabel("type: task", new Color(0xE3D9FC)),
			new InMemoryLabel("in: core", new Color(0xE8F9DE)),
			new InMemoryLabel("in: maven", new Color(0xE8F9DE)),
			new InMemoryLabel("in: gradle", new Color(0xE8F9DE)),
			new InMemoryLabel("for: upgrade-attention", new Color(0xC5DEF5)),
			new InMemoryLabel("status: waiting-for-triage", new Color(0xFEF2C0)));

	private final List<InMemoryMilestone> milestones = createMilestones();

	InMemoryTicketRepository() {

		Label upgradeLabel = labels.getFirst();
		create("Upgrade to Spring Framework 7.0.6", milestone("3.5.0"), List.of(upgradeLabel));
		create("Upgrade to HTTP Core 5.4.3", milestone("3.5.0"), List.of(upgradeLabel));
		create("Upgrade to jackson-core 2.22.0", milestone("3.5.0"), List.of(upgradeLabel));
		create("Upgrade to Netty 4.2.5", null, List.of(upgradeLabel));
		create("Upgrade to Micrometer 1.15.0", milestone("3.4.3"), List.of(upgradeLabel));
	}

	@Override
	public List<? extends Ticket> findTickets(ProgressIndicator indicator, Consumer<TicketQuery> query) {

		simulateNetwork(indicator);

		TicketQuery ticketQuery = new TicketQuery();
		query.accept(ticketQuery);

		synchronized (tickets) {
			return tickets.stream().filter(ticket -> matches(ticket, ticketQuery))
					.toList();
		}
	}

	@Override
	public Ticket createTicket(ProgressIndicator indicator, String title, Consumer<TicketSpec> spec) {

		simulateNetwork(indicator);

		TicketSpec ticketSpec = new TicketSpec();
		spec.accept(ticketSpec);
		return create(title, ticketSpec.getMilestone(), ticketSpec.getLabels());
	}

	@Override
	public List<? extends TicketState> getTicketStates(ProgressIndicator indicator) {
		return List.of(InMemoryState.OPEN, InMemoryState.CLOSED);
	}

	@Override
	public List<? extends Milestone> getMilestones(ProgressIndicator indicator) {
		simulateNetwork(indicator);
		return milestones;
	}

	@Override
	public List<? extends Label> getLabels(ProgressIndicator indicator) {
		simulateNetwork(indicator);
		return labels;
	}

	@Override
	public TicketRepository cached() {
		return new TicketRepositoryWrapper(this) {


			@Override
			public List<? extends Milestone> getMilestones(ProgressIndicator indicator) throws IOException {
				return milestones;
			}

			@Override
			public List<? extends Label> getLabels(ProgressIndicator indicator) throws IOException {
				return labels;
			}

		};
	}

	private InMemoryTicket create(String title, @Nullable Milestone milestone, List<? extends Label> labels) {

		TicketKey key = TicketKey.of(Long.toString(sequence.incrementAndGet()));
		URI webLink = URI.create("https://tickets.local/" + key.getValue());
		List<Milestone> attachedMilestones = milestone != null ? List.of(milestone) : List.of();

		InMemoryTicket ticket = new InMemoryTicket(key, title, InMemoryState.OPEN, webLink, attachedMilestones, labels);
		tickets.add(ticket);
		return ticket;
	}

	private @Nullable Milestone milestone(String title) {

		for (InMemoryMilestone milestone : milestones) {
			if (milestone.getTitle().equals(title)) {
				return milestone;
			}
		}
		return null;
	}

	private static boolean matches(InMemoryTicket ticket, TicketQuery query) {

		if (!query.getTitles().isEmpty()) {
			boolean found = false;
			for (String title : query.getTitles()) {
				if (ticket.getTitle().contains(title)) {
					found = true;
					break;
				}
			}

			if (!found) {
				return false;
			}
		}
		if (!query.getStates().isEmpty()
		    && query.getStates().stream()
				    .noneMatch(state -> state.isOpen() == ticket.getState().isOpen())) {
			return false;
		}
		if (!query.getMilestones()
				.isEmpty() && Collections.disjoint(query.getMilestones(), ticket.getMilestones())) {
			return false;
		}
		return query.getLabels()
				       .isEmpty() || !Collections.disjoint(query.getLabels(), ticket.getLabels());
	}

	private void simulateNetwork(ProgressIndicator indicator) {

		int iterations = random.nextInt(5, 10);
		int ex = random.nextInt(0, 100);
		if (ex > 95) {
			throw new IllegalStateException("Simulated exception");
		}
		for (int i = 0; i < iterations; i++) {
			indicator.checkCanceled();
			TimeoutUtil.sleep(80);
		}
	}

	private static List<InMemoryMilestone> createMilestones() {

		List<String> titles = List.of("3.4.2", "3.4.3", "3.5.0", "3.5.1", "4.0.0-M3", "4.0.0", "4.1.0", "4.1.0-M1",
				"4.1.1", "4.2.0-M1");

		LocalDate base = LocalDate.now().minusDays(4);
		List<InMemoryMilestone> milestones = new ArrayList<>(titles.size());
		for (int i = 0; i < titles.size(); i++) {

			LocalDateTime releaseDate = base
					.plusDays(i + 1L).atStartOfDay();
			boolean open = true;
			if (i < 2) {
				open = false;
			}

			if (i > 5) {
				releaseDate = null;
			}
			milestones.add(new InMemoryMilestone(titles.get(i), releaseDate, open));
		}

		return milestones;
	}

	public static class InMemoryTicket implements Ticket {

		private final TicketKey key;

		private final String title;

		private final TicketState state;

		private final URI webLink;

		private final List<? extends Milestone> milestones;

		private final List<? extends Label> labels;

		public InMemoryTicket(TicketKey key, String title, TicketState state, URI webLink,
				List<? extends Milestone> milestones,
				List<? extends Label> labels) {

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
			return (List) milestones;
		}

		@Override
		public List<Label> getLabels() {
			return (List) labels;
		}

	}

	public static class InMemoryLabel implements Label {

		private final String name;

		private final Color color;

		public InMemoryLabel(String name, Color color) {

			this.name = name;
			this.color = color;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public @Nullable Color getColor() {
			return color;
		}

	}

	public static class InMemoryMilestone implements Milestone {

		private final String title;

		private final @Nullable LocalDateTime releaseDate;

		private final boolean open;

		public InMemoryMilestone(String title, @Nullable LocalDateTime releaseDate, boolean open) {

			this.title = title;
			this.releaseDate = releaseDate;
			this.open = open;
		}

		@Override
		public String getTitle() {
			return title;
		}

		@Override
		public boolean isOpen() {
			return open;
		}

		@Override
		public String getDescription() {
			return "Release milestone for " + title + " on " + releaseDate + " open: " + isOpen();
		}

		@Override
		public @Nullable LocalDateTime getReleaseDate() {
			return releaseDate;
		}

	}

}
