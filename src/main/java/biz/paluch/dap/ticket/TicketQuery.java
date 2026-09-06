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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Mutable search criteria. Criteria combine as AND, values within a criterion
 * as OR. Empty criteria impose no constraint. Titles match exactly.
 *
 * <p>Use repository-owned values. Mutators append values and return this query.
 * Accessors expose live lists. Do not retain the query after its search
 * callback or share it across threads.
 *
 * @author Mark Paluch
 */
public class TicketQuery {

	private final List<String> titles = new ArrayList<>();

	private final List<TicketState> states = new ArrayList<>();

	private final List<Milestone> milestones = new ArrayList<>();

	private final List<Label> labels = new ArrayList<>();

	public TicketQuery title(String... titles) {
		return title(Arrays.asList(titles));
	}

	public TicketQuery title(Collection<String> titles) {

		this.titles.addAll(titles);
		return this;
	}

	public TicketQuery state(TicketState... states) {
		return state(Arrays.asList(states));
	}

	public TicketQuery state(Collection<TicketState> states) {

		this.states.addAll(states);
		return this;
	}

	public TicketQuery milestone(Milestone... milestones) {
		return milestone(Arrays.asList(milestones));
	}

	public TicketQuery milestone(Collection<Milestone> milestones) {

		this.milestones.addAll(milestones);
		return this;
	}

	public TicketQuery label(Label... labels) {
		return label(Arrays.asList(labels));
	}

	public TicketQuery label(Collection<Label> labels) {

		this.labels.addAll(labels);
		return this;
	}

	public List<String> getTitles() {
		return titles;
	}

	public List<TicketState> getStates() {
		return states;
	}

	public List<Milestone> getMilestones() {
		return milestones;
	}

	public List<Label> getLabels() {
		return labels;
	}

}
