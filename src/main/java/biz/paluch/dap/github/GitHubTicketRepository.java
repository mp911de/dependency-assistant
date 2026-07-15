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

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.Ticket;
import biz.paluch.dap.ticket.TicketKey;
import biz.paluch.dap.ticket.TicketQuery;
import biz.paluch.dap.ticket.TicketRepository;
import biz.paluch.dap.ticket.TicketSpec;
import biz.paluch.dap.ticket.TicketState;
import biz.paluch.dap.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubIssueLabel;
import org.jetbrains.plugins.github.api.data.GithubResponsePage;
import org.jetbrains.plugins.github.api.data.request.GithubCreateIssueRequest;
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination;
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader;
import org.jspecify.annotations.Nullable;

/**
 * {@link TicketRepository} backed by the GitHub issues REST API.
 *
 * <p>Searches use the GitHub search API through the IntelliJ Platform search
 * page request shape. Repository, issue type, state, and grouped label or
 * milestone criteria narrow the request; everything else is matched
 * client-side, including exact titles. Pull requests are filtered out.
 *
 * <p>Label and milestone listings refresh the {@link GitHubTicketCache} on
 * success and fall back to its stored entries when GitHub cannot be reached;
 * without stored entries the failure propagates.
 *
 * <p>The account-bound request executor is supplied by the caller so account
 * resolution and request execution share the same lifecycle.
 *
 * @author Mark Paluch
 */
class GitHubTicketRepository implements TicketRepository {

	private static final String ACCEPT = "application/vnd.github+json";

	private static final int PAGE_SIZE = 100;

	public static final List<TicketState> TICKET_STATES = List.of(GitHubTicketState.OPEN, GitHubTicketState.CLOSED);

	private final GHRepositoryCoordinates repository;

	private final GitRepositoryMetadata coordinates;

	private final GithubApiRequestExecutor executor;

	private final GitHubTicketCache cache;

	GitHubTicketRepository(GitRepositoryMetadata coordinates, GithubApiRequestExecutor executor,
			GitHubTicketCache cache) {
		this(toRepositoryCoordinates(coordinates), coordinates, executor, cache);
	}

	GitHubTicketRepository(GHRepositoryCoordinates repository, GithubApiRequestExecutor executor,
			GitHubTicketCache cache) {
		this(repository, toRepositoryMetadata(repository), executor, cache);
	}

	private GitHubTicketRepository(GHRepositoryCoordinates repository, GitRepositoryMetadata coordinates,
			GithubApiRequestExecutor executor, GitHubTicketCache cache) {
		this.repository = repository;
		this.coordinates = coordinates;
		this.executor = executor;
		this.cache = cache;
	}

	GitHubTicketRepository(GithubServerPath server, GitRepositoryMetadata coordinates,
			GithubApiRequestExecutor executor, GitHubTicketCache cache) {
		this(toRepositoryCoordinates(server, coordinates), coordinates, executor, cache);
	}

	@Override
	public List<Ticket> findTickets(ProgressIndicator indicator, Consumer<TicketQuery> configurer) throws IOException {

		GitHubTicketQuery query = new GitHubTicketQuery();
		configurer.accept(query);

		List<GitHubIssueDto> issues = loadSearchPages(query, GitHubIssueDto.class, indicator);
		List<Ticket> tickets = new ArrayList<>(issues.size());
		for (GitHubIssueDto issue : issues) {
			if (!query.matches(issue)) {
				continue;
			}
			tickets.add(issue.toTicket());
		}

		return tickets;
	}

	@Override
	public Ticket createTicket(ProgressIndicator indicator, String title, Consumer<TicketSpec> configurer)
			throws IOException {

		TicketSpec spec = new TicketSpec();
		configurer.accept(spec);

		Long milestoneNumber = null;
		Milestone milestone = spec.getMilestone();
		if (milestone instanceof GitHubMilestone gitHubMilestone) {
			milestoneNumber = gitHubMilestone.getNumber();
		}

		List<String> labelNames = new ArrayList<>(spec.getLabels().size());
		for (Label label : spec.getLabels()) {
			labelNames.add(label.getName());
		}

		GHRepositoryPath repositoryPath = repositoryPath();
		String url = repository.getServerPath().toApiUrl()
				+ "/repos/%s/%s/issues".formatted(repositoryPath.getOwner(), repositoryPath.getRepository());
		GithubCreateIssueRequest body = new GithubCreateIssueRequest(title, spec.getDescription(), milestoneNumber,
				labelNames.isEmpty() ? null : labelNames, null);

		GitHubIssueDto issue = executor.execute(indicator,
				new GithubApiRequest.Post.Json<>(url, body, GitHubIssueDto.class, ACCEPT));
		return issue.toTicket();
	}

	@Override
	public List<TicketState> getTicketStates(ProgressIndicator indicator) {
		return TICKET_STATES;
	}

	@Override
	public List<GitHubMilestone> getMilestones(ProgressIndicator indicator) throws IOException {

		GHRepositoryPath repositoryPath = repositoryPath();
		String path = "/repos/%s/%s/milestones?state=open&sort=due_on&per_page=%d".formatted(
				repositoryPath.getOwner(), repositoryPath.getRepository(), PAGE_SIZE);

		List<GitHubMilestoneDto> dtos;
		try {
			dtos = loadPages(path, GitHubMilestoneDto.class, indicator);
		} catch (IOException ex) {
			List<GitHubMilestone> fallback = cache.getMilestones(coordinates);
			if (!fallback.isEmpty()) {
				return fallback;
			}
			throw ex;
		}

		List<GitHubMilestone> milestones = new ArrayList<>(dtos.size());
		for (GitHubMilestoneDto dto : dtos) {
			milestones.add(dto.toMilestone());
		}

		cache.storeMilestones(coordinates, milestones);
		return milestones;
	}

	@Override
	public List<GitHubLabel> getLabels(ProgressIndicator indicator) throws IOException {

		GHRepositoryPath repositoryPath = repositoryPath();
		String path = "/repos/%s/%s/labels?per_page=%d".formatted(repositoryPath.getOwner(),
				repositoryPath.getRepository(), PAGE_SIZE);

		List<GithubIssueLabel> dtos;
		try {
			dtos = loadPages(path, GithubIssueLabel.class, indicator);
		} catch (IOException ex) {
			List<GitHubLabel> fallback = cache.getLabels(coordinates);
			if (!fallback.isEmpty()) {
				return fallback;
			}
			throw ex;
		}

		List<GitHubLabel> labels = new ArrayList<>(dtos.size());
		for (GithubIssueLabel dto : dtos) {
			labels.add(GitHubLabel.of(dto));
		}

		cache.storeLabels(coordinates, labels);
		return labels;
	}

	@Override
	public TicketRepository cached() {
		return new TicketRepository() {

			@Override
			public List<Ticket> findTickets(ProgressIndicator indicator, Consumer<TicketQuery> query) {
				return List.of();
			}

			@Override
			public Ticket createTicket(ProgressIndicator indicator, String title, Consumer<TicketSpec> spec) {
				throw new UnsupportedOperationException();
			}

			@Override
			public List<TicketState> getTicketStates(ProgressIndicator indicator) {
				return GitHubTicketRepository.this.getTicketStates(indicator);
			}

			@Override
			public List<GitHubMilestone> getMilestones(ProgressIndicator indicator) {
				return cache.getMilestones(coordinates);
			}

			@Override
			public List<GitHubLabel> getLabels(ProgressIndicator indicator) {
				return cache.getLabels(coordinates);
			}

			@Override
			public TicketRepository cached() {
				return this;
			}

			@Override
			public String getDisplayReference(TicketKey key) {
				return GitHubTicketRepository.this.getDisplayReference(key);
			}

			@Override
			public String getCloseReference(TicketKey key) {
				return GitHubTicketRepository.this.getCloseReference(key);
			}
		};
	}

	@Override
	public String getDisplayReference(TicketKey key) {
		return GitHubConventions.INSTANCE.getDisplayReference(key);
	}

	@Override
	public String getCloseReference(TicketKey key) {
		return GitHubConventions.INSTANCE.getCloseReference(key);
	}

	private GHRepositoryPath repositoryPath() {
		return repository.getRepositoryPath();
	}

	private <T> List<T> loadPages(String path, Class<T> type, ProgressIndicator indicator) throws IOException {

		String apiBase = repository.getServerPath().toApiUrl();
		GithubApiRequest<GithubResponsePage<T>> initial = new GithubApiRequest.Get.JsonPage<>(apiBase + path, type,
				ACCEPT);
		GithubApiPagesLoader.Request<T> pages = new GithubApiPagesLoader.Request<>(initial, nextUrl -> {

			if (!nextUrl.startsWith(apiBase)) {
				throw new IllegalStateException("Pagination URL does not match expected server: %s".formatted(nextUrl));
			}

			return new GithubApiRequest.Get.JsonPage<>(nextUrl, type, ACCEPT);
		});

		return GithubApiPagesLoader.loadAll(executor, indicator, pages);
	}

	private <T> List<T> loadSearchPages(GitHubTicketQuery query, Class<T> type, ProgressIndicator indicator)
			throws IOException {

		GithubServerPath server = repository.getServerPath();
		String apiBase = server.toApiUrl();
		GithubApiRequest<GithubResponsePage<T>> initial = searchIssuesRequest(server, query, type);
		GithubApiPagesLoader.Request<T> pages = new GithubApiPagesLoader.Request<>(initial, nextUrl -> {

			if (!nextUrl.startsWith(apiBase)) {
				throw new IllegalStateException("Pagination URL does not match expected server: %s".formatted(nextUrl));
			}

			return new GithubApiRequest.Get.JsonSearchPage<>(nextUrl, type, ACCEPT);
		});

		return GithubApiPagesLoader.loadAll(executor, indicator, pages);
	}

	private <T> GithubApiRequest<GithubResponsePage<T>> searchIssuesRequest(GithubServerPath server,
			GitHubTicketQuery query, Class<T> type) {

		String state = query.getState();
		String searchState = "all".equals(state) ? null : state;
		GithubApiRequest<?> request = GithubApiRequests.Search.Issues.get(server, repositoryPath(), searchState, null,
				query.searchQuery(), new GithubRequestPagination(1, PAGE_SIZE));

		return new GithubApiRequest.Get.JsonSearchPage<>(request.getUrl(), type, ACCEPT);
	}

	@Override
	public String toString() {
		GHRepositoryPath repositoryPath = repositoryPath();
		return "GitHub[%s/%s/%s]".formatted(repository.getServerPath().getHost(), repositoryPath.getOwner(),
				repositoryPath.getRepository());
	}

	private static GHRepositoryCoordinates toRepositoryCoordinates(GitRepositoryMetadata coordinates) {
		return toRepositoryCoordinates(GithubServerPath.from(coordinates.host()), coordinates);
	}

	private static GHRepositoryCoordinates toRepositoryCoordinates(GithubServerPath server,
			GitRepositoryMetadata coordinates) {
		return new GHRepositoryCoordinates(server, new GHRepositoryPath(coordinates.owner(), coordinates.repository()));
	}

	private static GitRepositoryMetadata toRepositoryMetadata(GHRepositoryCoordinates repository) {

		GHRepositoryPath repositoryPath = repository.getRepositoryPath();
		return new GitRepositoryMetadata(repository.getServerPath().getHost(), repositoryPath.getOwner(),
				repositoryPath.getRepository());
	}

	/**
	 * DTO for the GitHub {@code /repos/{owner}/{repo}/issues} response items.
	 */
	record GitHubIssueDto(@JsonProperty("number") long number,
			@JsonProperty("title") String title,
			@JsonProperty("state") GitHubTicketState state,
			@JsonProperty("html_url") String htmlUrl,
			@JsonProperty("labels") List<GithubIssueLabel> labels,
			@JsonProperty("milestone") @Nullable GitHubMilestoneDto milestone,
			@JsonProperty("pull_request") @Nullable Object pullRequest) {

		private Ticket toTicket() {

			GitHubMilestoneDto issueMilestone = milestone();
			List<Milestone> milestones = issueMilestone != null ? List.of(issueMilestone.toMilestone()) : List.of();

			List<Label> labels = new ArrayList<>(labels().size());
			for (GithubIssueLabel label : labels()) {
				labels.add(GitHubLabel.of(label));
			}

			return new GitHubTicket(TicketKey.of(String.valueOf(number())), title(), state(),
					URI.create(htmlUrl()), milestones, labels);
		}

	}

	/**
	 * DTO for the GitHub {@code /repos/{owner}/{repo}/milestones} response items.
	 */
	record GitHubMilestoneDto(@JsonProperty("number") long number,
			@JsonProperty("title") String title,
			@JsonProperty("state") GitHubTicketState state,
			@JsonProperty("description") @Nullable String description,
			@JsonProperty("due_on") @Nullable String dueOn) {

		private GitHubMilestone toMilestone() {

			String dueOn = dueOn();
			LocalDateTime releaseDate = StringUtils.hasText(dueOn) ? OffsetDateTime.parse(dueOn).toLocalDateTime()
					: null;
			return new GitHubMilestone(number(), title(), state() == GitHubTicketState.OPEN, description(),
					releaseDate);
		}

	}

}
