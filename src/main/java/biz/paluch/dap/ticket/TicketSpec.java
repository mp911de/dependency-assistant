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

package biz.paluch.dap.ticket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Mutable specification object for ticket creation.
 *
 * <p>The repository creates a fresh {@code TicketSpec} for each
 * {@link TicketRepository#createTicket(com.intellij.openapi.progress.ProgressIndicator, String, java.util.function.Consumer)}
 * call, passes it to the caller's configurer, and consumes the configured
 * values for one create request. The title is not part of the spec; it is the
 * required positional argument of the create operation.
 *
 * <p>Milestone and label values must be instances handed out by the creating
 * repository. A spec is not thread-safe and should not be retained after the
 * create callback returns.
 *
 * @author Mark Paluch
 */
public class TicketSpec {

	private @Nullable String description;

	private @Nullable Milestone milestone;

	private final List<Label> labels = new ArrayList<>();

	/**
	 * Set the ticket description or body.
	 *
	 * @param description the description to send with the create request.
	 * @return {@code this} spec.
	 */
	public TicketSpec description(String description) {
		this.description = description;
		return this;
	}

	/**
	 * Set the milestone to attach the ticket to.
	 *
	 * @param milestone a milestone obtained from the creating repository.
	 * @return {@code this} spec.
	 */
	public TicketSpec milestone(Milestone milestone) {
		this.milestone = milestone;
		return this;
	}

	/**
	 * Add labels to attach to the ticket.
	 *
	 * @param labels labels obtained from the creating repository.
	 * @return {@code this} spec.
	 * @see #label(Collection)
	 */
	public TicketSpec label(Label... labels) {
		return label(Arrays.asList(labels));
	}

	/**
	 * Add labels to attach to the ticket.
	 *
	 * @param labels labels obtained from the creating repository.
	 * @return {@code this} spec.
	 */
	public TicketSpec label(Collection<Label> labels) {
		this.labels.addAll(labels);
		return this;
	}

	/**
	 * Return the configured ticket description.
	 *
	 * @return the ticket description, or {@literal null} if no description has been
	 * configured.
	 */
	public @Nullable String getDescription() {
		return description;
	}

	/**
	 * Return the configured milestone to attach.
	 *
	 * @return the milestone to attach, or {@literal null} if none has been
	 * configured.
	 */
	public @Nullable Milestone getMilestone() {
		return milestone;
	}

	/**
	 * Return the configured labels to attach.
	 *
	 * @return the live label list; empty if none have been configured.
	 */
	public List<Label> getLabels() {
		return labels;
	}

}
