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
 * Mutable criteria object for ticket search.
 *
 * Criteria combine as AND, values within one criterion combine as OR, and an
 * empty criterion is unconstrained. Titles match exactly.
 *
 * <p>A query is not thread-safe and should not be retained after the search
 * callback returns.
 *
 * @author Mark Paluch
 */
public class TicketQuery {

	private final List<String> titles = new ArrayList<>();

	private final List<TicketState> states = new ArrayList<>();

	private final List<Milestone> milestones = new ArrayList<>();

	private final List<Label> labels = new ArrayList<>();

	/**
	 * Add exact-title values to match.
	 *
	 * @param titles the exact titles to add.
	 * @return {@code this} query.
	 * @see #title(Collection)
	 */
	public TicketQuery title(String... titles) {
		return title(Arrays.asList(titles));
	}

	/**
	 * Add exact-title values to match.
	 *
	 * @param titles the exact titles to add.
	 * @return {@code this} query.
	 */
	public TicketQuery title(Collection<String> titles) {

		this.titles.addAll(titles);
		return this;
	}

	/**
	 * Add ticket states to match.
	 *
	 * @param states states obtained from the queried repository.
	 * @return {@code this} query.
	 * @see #state(Collection)
	 */
	public TicketQuery state(TicketState... states) {
		return state(Arrays.asList(states));
	}

	/**
	 * Add ticket states to match.
	 *
	 * @param states states obtained from the queried repository.
	 * @return {@code this} query.
	 */
	public TicketQuery state(Collection<TicketState> states) {

		this.states.addAll(states);
		return this;
	}

	/**
	 * Add milestones to match.
	 *
	 * @param milestones milestones obtained from the queried repository.
	 * @return {@code this} query.
	 * @see #milestone(Collection)
	 */
	public TicketQuery milestone(Milestone... milestones) {
		return milestone(Arrays.asList(milestones));
	}

	/**
	 * Add milestones to match.
	 *
	 * @param milestones milestones obtained from the queried repository.
	 * @return {@code this} query.
	 */
	public TicketQuery milestone(Collection<Milestone> milestones) {

		this.milestones.addAll(milestones);
		return this;
	}

	/**
	 * Add labels to match.
	 *
	 * @param labels labels obtained from the queried repository.
	 * @return {@code this} query.
	 * @see #label(Collection)
	 */
	public TicketQuery label(Label... labels) {
		return label(Arrays.asList(labels));
	}

	/**
	 * Add labels to match.
	 *
	 * @param labels labels obtained from the queried repository.
	 * @return {@code this} query.
	 */
	public TicketQuery label(Collection<Label> labels) {

		this.labels.addAll(labels);
		return this;
	}

	/**
	 * Return the configured exact-title values.
	 *
	 * @return the live exact-title list; empty if unconstrained.
	 */
	public List<String> getTitles() {
		return titles;
	}

	/**
	 * Return the configured states to match.
	 *
	 * @return the live state list; empty if unconstrained.
	 */
	public List<TicketState> getStates() {
		return states;
	}

	/**
	 * Return the configured milestones to match.
	 *
	 * @return the live milestone list; empty if unconstrained.
	 */
	public List<Milestone> getMilestones() {
		return milestones;
	}

	/**
	 * Return the configured labels to match.
	 *
	 * @return the live label list; empty if unconstrained.
	 */
	public List<Label> getLabels() {
		return labels;
	}

}
