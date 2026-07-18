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

import java.awt.Color;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Ticket;
import biz.paluch.dap.ticket.TicketKey;
import biz.paluch.dap.ticket.TicketState;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.*;
import static org.assertj.core.api.Assertions.*;

/**
 * WireMock tests for {@link GitHubTicketRepository}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class GitHubTicketRepositoryTests {

	static final GitRepositoryMetadata COORDINATES = new GitRepositoryMetadata("localhost", "mp911de",
			"dependency-assistant");

	static final EmptyProgressIndicator EMPTY_INDICATOR = new EmptyProgressIndicator(ModalityState.NON_MODAL);

	WireMockServer server;

	GitHubTicketCache cache = new GitHubTicketCache();

	GitHubTicketRepository repository;

	@BeforeEach
	void setUp() {

		server = new WireMockServer(wireMockConfig().dynamicPort());
		server.start();

		GithubServerPath serverPath = new GithubServerPath(true, "localhost", server.port(), null);
		GithubApiRequestExecutor executor = GithubApiRequestExecutor.Factory.getInstance().create();
		repository = new GitHubTicketRepository(serverPath, COORDINATES, executor, cache);
	}

	@AfterEach
	void tearDown() {

		if (server != null) {
			server.stop();
		}
	}

	@Test
	void findTicketsMatchesTitlesExactlyAndSkipsPullRequests() throws IOException {

		server.stubFor(get(urlPathEqualTo("/api/v3/search/issues"))
				.willReturn(okJson(searchResult(issue(1, "Upgrade Spring"),
						issue(2, "Upgrade Spring Framework"),
						issue(3, "Upgrade Spring", "null", true)))));

		List<Ticket> tickets = repository.findTickets(EMPTY_INDICATOR, query -> query.title("Upgrade Spring"));

		assertThat(tickets).hasSize(1);
		assertThat(tickets.getFirst().getKey()).isEqualTo(TicketKey.of("1"));
		assertThat(requestedUrl()).contains("/api/v3/search/issues")
				.contains("q=repo%3Amp911de%2Fdependency-assistant%20type%3Aissue")
				.contains("advanced_search=true").contains("per_page=100").doesNotContain("sort=")
				.doesNotContain("order=");
	}

	@Test
	void findTicketsDerivesStateParameter() throws IOException {

		server.stubFor(get(urlPathEqualTo("/api/v3/search/issues")).willReturn(okJson(searchResult())));

		repository.findTickets((ProgressIndicator) EMPTY_INDICATOR, query -> query.state(GitHubTicketState.OPEN));

		assertThat(requestedUrl()).contains("state%3Aopen");
	}

	@Test
	void findTicketsNarrowsSingleLabelServerSide() throws IOException {

		server.stubFor(get(urlPathEqualTo("/api/v3/search/issues")).willReturn(okJson(searchResult())));

		repository.findTickets((ProgressIndicator) EMPTY_INDICATOR,
				query -> query.label(new GitHubLabel("type: dependency-upgrade", "", null)));

		assertThat(requestedUrl()).contains("type%3Aissue%20AND%20label%3A%22type%3A%20dependency-upgrade%22");
	}

	@Test
	void findTicketsMatchesAnyQueriedLabel() throws IOException {

		server.stubFor(get(urlPathEqualTo("/api/v3/search/issues"))
				.willReturn(okJson(searchResult(issue(1, "Upgrade Spring", "dependencies"),
						issue(2, "Upgrade Netty", "task"), issue(3, "Upgrade Jackson", "documentation")))));

		List<Ticket> tickets = repository.findTickets(EMPTY_INDICATOR,
				query -> query.label(new GitHubLabel("dependencies", "", null), new GitHubLabel("task", "", null)));

		assertThat(tickets).hasSize(2);
		assertThat(requestedUrl()).contains("AND%20(label%3A%22dependencies%22%20OR%20label%3A%22task%22)");
	}

	@Test
	void findTicketsNarrowsMilestonesAndLabelsAsOrGroups() throws IOException {

		server.stubFor(get(urlPathEqualTo("/api/v3/search/issues")).willReturn(okJson(searchResult())));

		repository.findTickets((ProgressIndicator) EMPTY_INDICATOR,
				query -> query.milestone(gitHubMilestone(7), gitHubMilestone(8))
						.label(new GitHubLabel("dependencies", "", null), new GitHubLabel("task", "", null)));

		assertThat(requestedUrl()).contains(
				"type%3Aissue%20AND%20(milestone%3A%22milestone-7%22%20OR%20milestone%3A%22milestone-8%22)%20AND%20(label%3A%22dependencies%22%20OR%20label%3A%22task%22)");
	}

	@Test
	void findTicketsLeavesEmptyCriteriaUnconstrained() throws IOException {

		server.stubFor(get(urlPathEqualTo("/api/v3/search/issues"))
				.willReturn(okJson(searchResult(issue(1, "Upgrade Spring", "dependencies"),
						issueWithMilestone(2, "Upgrade Netty", 7, "2026.1", null, "task")))));

		List<Ticket> tickets = repository.findTickets(EMPTY_INDICATOR,
				query -> query.title(List.of()).state(List.of()).milestone(List.of()).label(List.of()));

		assertThat(tickets).extracting(Ticket::getKey).containsExactly(TicketKey.of("1"), TicketKey.of("2"));
		assertThat(requestedUrl()).contains("/api/v3/search/issues").doesNotContain("state%3A")
				.doesNotContain("milestone%3A").doesNotContain("label%3A");
	}

	@Test
	void findTicketsTreatsForeignLabelsAsNonMatching() throws IOException {

		server.stubFor(get(urlPathEqualTo("/api/v3/search/issues"))
				.willReturn(okJson(searchResult(issue(1, "Upgrade Spring", "dependencies")))));

		Label foreign = new Label() {

			@Override
			public String getName() {
				return "dependencies";
			}

			@Override
			public @Nullable Color getColor() {
				return null;
			}

			@Override
			public String toString() {
				return "foreign";
			}

		};

		List<Ticket> tickets = repository.findTickets(EMPTY_INDICATOR, query -> query.label(foreign));

		assertThat(tickets).isEmpty();
		assertThat(requestedUrl()).contains("/api/v3/search/issues").doesNotContain("label%3A");
	}

	@Test
	void findTicketsMatchesAnyQueriedMilestone() throws IOException {

		server.stubFor(get(urlPathEqualTo("/api/v3/search/issues"))
				.willReturn(okJson(searchResult(issueWithMilestone(1, "Upgrade Spring", 7, "2026.1", null),
						issueWithMilestone(2, "Upgrade Netty", 9, "2026.2", null),
						issue(3, "Upgrade Jackson")))));

		List<Ticket> tickets = repository.findTickets(EMPTY_INDICATOR,
				query -> query.milestone(gitHubMilestone(7), gitHubMilestone(8)));

		assertThat(tickets).hasSize(1);
		assertThat(tickets.getFirst().getKey()).isEqualTo(TicketKey.of("1"));
		assertThat(requestedUrl())
				.contains("AND%20(milestone%3A%22milestone-7%22%20OR%20milestone%3A%22milestone-8%22)");
	}

	@Test
	void createTicketSendsSpecAndMapsResponse() throws IOException {

		server.stubFor(post(urlEqualTo("/api/v3/repos/mp911de/dependency-assistant/issues"))
				.willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
						.withBody(issueWithMilestone(42, "Upgrade Netty", 7, "2026.1", null,
								"type: dependency-upgrade"))));

		Ticket ticket = repository.createTicket(EMPTY_INDICATOR, "Upgrade Netty",
				spec -> spec.description("Upgrade to 4.2.x").milestone(gitHubMilestone(7))
						.label(new GitHubLabel("type: dependency-upgrade", "", null)));

		assertThat(ticket.getKey()).isEqualTo(TicketKey.of("42"));
		assertThat(ticket.getMilestones()).hasSize(1);
		assertThat(requestedUrl()).isEqualTo("/api/v3/repos/mp911de/dependency-assistant/issues");
		assertThat(server.getAllServeEvents().getFirst().getRequest().getBodyAsString())
				.contains("\"title\":\"Upgrade Netty\"").contains("\"milestone\":7")
				.contains("\"labels\":[\"type: dependency-upgrade\"]").contains("\"body\":\"Upgrade to 4.2.x\"");
	}

	@Test
	void getMilestonesMapsAndStoresListing() throws IOException {

		server.stubFor(get(urlPathEqualTo("/api/v3/repos/mp911de/dependency-assistant/milestones"))
				.willReturn(okJson("[" + milestone(7, "2026.1", "2026-08-01T00:00:00Z") + "]")));

		List<GitHubMilestone> milestones = repository.getMilestones(EMPTY_INDICATOR);

		assertThat(milestones).hasSize(1);
		assertThat(milestones.getFirst().getTitle()).isEqualTo("2026.1");
		assertThat(milestones.getFirst().getReleaseDate()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
		assertThat(cache.getMilestones(COORDINATES)).hasSize(1);
		assertThat(requestedUrl()).contains("state=open").contains("sort=due_on");
	}

	@Test
	void getMilestonesServesStoredFallbackOnFailure() throws IOException {

		cache.storeMilestones(COORDINATES, List.of(new GitHubMilestone(7, "2026.1", true, null, null)));
		server.stubFor(get(urlPathEqualTo("/api/v3/repos/mp911de/dependency-assistant/milestones"))
				.willReturn(aResponse().withStatus(503)));

		List<GitHubMilestone> milestones = repository.getMilestones(EMPTY_INDICATOR);

		assertThat(milestones).hasSize(1);
		assertThat(milestones.getFirst().getTitle()).isEqualTo("2026.1");
	}

	@Test
	void getMilestonesPropagatesFailureWithoutStoredData() {

		server.stubFor(get(urlPathEqualTo("/api/v3/repos/mp911de/dependency-assistant/milestones"))
				.willReturn(aResponse().withStatus(503)));

		assertThatExceptionOfType(IOException.class)
				.isThrownBy(() -> repository.getMilestones((ProgressIndicator) EMPTY_INDICATOR));
	}

	@Test
	void getLabelsServesStoredFallbackOnFailure() throws IOException {

		cache.storeLabels(COORDINATES, List.of(new GitHubLabel("bug", "", "d93f0b")));
		server.stubFor(get(urlPathEqualTo("/api/v3/repos/mp911de/dependency-assistant/labels"))
				.willReturn(aResponse().withStatus(503)));

		List<GitHubLabel> labels = repository.getLabels(EMPTY_INDICATOR);

		assertThat(labels).hasSize(1);
		assertThat(labels.getFirst().getColor()).isEqualTo(new Color(0xd93f0b));
	}

	@Test
	void getTicketStatesExposesOpenAndClosed() {

		List<TicketState> states = repository.getTicketStates(EMPTY_INDICATOR);

		assertThat(states).hasSize(2);
		assertThat(states.getFirst().isOpen()).isTrue();
		assertThat(states.getLast().isClosed()).isTrue();
	}

	private String requestedUrl() {
		return server.getAllServeEvents().getFirst().getRequest().getUrl();
	}

	private static String searchResult(String... items) {

		return """
				{
				  "total_count": %d,
				  "incomplete_results": false,
				  "items": [%s]
				}
				""".formatted(items.length, String.join(",", items));
	}

	private static String issue(long number, String title, String... labels) {
		return issue(number, title, "null", false, labels);
	}

	private static String issueWithMilestone(long number, String title, long milestoneNumber, String milestoneTitle,
			@Nullable String dueOn, String... labels) {
		return issue(number, title, milestone(milestoneNumber, milestoneTitle, dueOn), false, labels);
	}

	private static String issue(long number, String title, String milestone, boolean pullRequest, String... labels) {

		StringBuilder labelJson = new StringBuilder();
		for (int i = 0; i < labels.length; i++) {
			if (i > 0) {
				labelJson.append(',');
			}
			labelJson.append("""

					{
					  "name": "%s",
					  "color": "000000"
					}""".formatted(labels[i]));
		}

		String pullRequestJson = pullRequest ? """
				,
				  "pull_request": {}
				""" : "";

		return """
				{
				  "number": %d,
				  "title": "%s",
				  "state": "open",
				  "html_url": "http://localhost/mp911de/dependency-assistant/issues/%d",
				  "labels": [%s
				  ],
				  "milestone": %s%s
				}
				""".formatted(number, title, number, labelJson, milestone, pullRequestJson);
	}

	private static String milestone(long number, String title, @Nullable String dueOn) {

		String dueDate = dueOn != null ? "\"" + dueOn + "\"" : "null";
		return """
				{
				  "number": %d,
				  "title": "%s",
				  "state": "open",
				  "description": null,
				  "due_on": %s
				}
				""".formatted(number, title, dueDate);
	}

	private static GitHubMilestone gitHubMilestone(long number) {
		return new GitHubMilestone(number, "milestone-" + number, true, null, null);
	}

}
