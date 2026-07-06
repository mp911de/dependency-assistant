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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.github.GitHubTicketRepository.GitHubIssueDto;
import biz.paluch.dap.github.GitHubTicketRepository.GitHubMilestoneDto;
import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.TicketQuery;
import biz.paluch.dap.ticket.TicketState;
import org.jetbrains.plugins.github.api.data.GithubIssueLabel;
import org.jspecify.annotations.Nullable;

/**
 * GitHub {@link TicketQuery} implementation.
 *
 * @author Mark Paluch
 */
class GitHubTicketQuery extends TicketQuery {

	String getState() {

		List<TicketState> states = getStates();
		if (states.isEmpty() || states.size() == GitHubTicketRepository.TICKET_STATES.size()) {
			return "all";
		}

		boolean open = false;
		boolean closed = false;
		for (TicketState state : states) {
			if (state instanceof GitHubTicketState gitHubState) {
				open |= gitHubState.isOpen();
				closed |= gitHubState.isClosed();
			}
		}

		return open == closed ? "all" : open ? "open" : "closed";
	}

	boolean matches(GitHubIssueDto issue) {

		if (issue.pullRequest() != null) {
			return false;
		}

		List<String> titles = getTitles();
		if (!titles.isEmpty() && !titles.contains(issue.title())) {
			return false;
		}

		List<TicketState> states = getStates();
		if (!states.isEmpty() && !matchesState(issue.state())) {
			return false;
		}

		List<Milestone> milestones = getMilestones();
		if (!milestones.isEmpty() && !matchesMilestone(issue.milestone())) {
			return false;
		}

		List<Label> labels = getLabels();
		return labels.isEmpty() || matchesLabel(issue.labels());
	}

	private boolean matchesState(GitHubTicketState issueState) {

		for (TicketState state : getStates()) {
			if (state instanceof GitHubTicketState gitHubState && gitHubState == issueState) {
				return true;
			}
		}

		return false;
	}

	private boolean matchesMilestone(@Nullable GitHubMilestoneDto issueMilestone) {

		if (issueMilestone == null) {
			return false;
		}

		for (Milestone milestone : getMilestones()) {
			if (milestone instanceof GitHubMilestone gitHubMilestone
					&& gitHubMilestone.getNumber() == issueMilestone.number()) {
				return true;
			}
		}

		return false;
	}

	private boolean matchesLabel(List<GithubIssueLabel> issueLabels) {

		Set<String> names = new HashSet<>(issueLabels.size());
		for (GithubIssueLabel issueLabel : issueLabels) {
			names.add(issueLabel.getName());
		}

		for (Label label : getLabels()) {
			if (label instanceof GitHubLabel gitHubLabel && names.contains(gitHubLabel.getName())) {
				return true;
			}
		}

		return false;
	}

	String searchQuery() {

		StringBuilder q = new StringBuilder("type:issue");
		appendOrGroup(q, milestoneTerms());
		appendOrGroup(q, labelTerms());

		return q.toString();
	}

	private List<String> milestoneTerms() {

		List<String> terms = new ArrayList<>();
		for (Milestone milestone : getMilestones()) {
			if (milestone instanceof GitHubMilestone) {
				terms.add("milestone:" + searchValue(milestone.getTitle()));
			}
		}

		return terms;
	}

	private List<String> labelTerms() {

		List<String> terms = new ArrayList<>();
		for (Label label : getLabels()) {
			if (label instanceof GitHubLabel) {
				terms.add("label:" + searchValue(label.getName()));
			}
		}

		return terms;
	}

	private static void appendOrGroup(StringBuilder query, List<String> terms) {

		if (terms.isEmpty()) {
			return;
		}

		query.append(" AND ");
		if (terms.size() == 1) {
			query.append(terms.getFirst());
			return;
		}

		query.append('(');
		for (int i = 0; i < terms.size(); i++) {
			if (i > 0) {
				query.append(" OR ");
			}
			query.append(terms.get(i));
		}
		query.append(')');
	}

	private static String searchValue(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

}
