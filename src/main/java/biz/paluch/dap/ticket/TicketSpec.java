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

import org.jspecify.annotations.Nullable;

/**
 * Mutable options for one ticket creation. Milestones and labels must come from
 * the creating repository. Mutators return this specification. Do not retain it
 * after the creation callback or share it across threads.
 *
 * @author Mark Paluch
 */
public class TicketSpec {

	private @Nullable String description;

	private @Nullable Milestone milestone;

	private final List<Label> labels = new ArrayList<>();

	public TicketSpec description(String description) {
		this.description = description;
		return this;
	}

	public TicketSpec milestone(Milestone milestone) {
		this.milestone = milestone;
		return this;
	}

	/**
	 * Append labels.
	 */
	public TicketSpec label(Label... labels) {
		return label(Arrays.asList(labels));
	}

	/**
	 * Append labels.
	 */
	public TicketSpec label(Collection<Label> labels) {
		this.labels.addAll(labels);
		return this;
	}

	public @Nullable String getDescription() {
		return description;
	}

	public @Nullable Milestone getMilestone() {
		return milestone;
	}

	/**
	 * Return the live label list.
	 */
	public List<Label> getLabels() {
		return labels;
	}

}
