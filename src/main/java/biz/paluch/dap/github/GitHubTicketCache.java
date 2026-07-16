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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import biz.paluch.dap.artifact.GitRepositoryMetadata;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jspecify.annotations.Nullable;

/**
 * Project-level service persisting GitHub label and milestone listings across
 * IDE restarts.
 *
 * <p>Entries are keyed by {@code host/owner/repository} and replaced wholesale
 * after each successful listing. {@link GitHubTicketRepository} serves the
 * stored entries as fallback when GitHub cannot be reached. Milestones are
 * persisted from the open-only listing, so rehydrated milestones are open by
 * definition; the milestone number is retained so persisted entries remain
 * valid for issue assignment and query filtering.
 *
 * @author Mark Paluch
 */
@State(name = "DependencyAssistantGitHubTickets", storages = @Storage("dependency-assistant-github.xml"))
public class GitHubTicketCache implements PersistentStateComponent<GitHubTicketCache.TicketData> {

	private final TicketData data = new TicketData();

	/**
	 * Return the project-scoped service instance.
	 *
	 * @param project the IntelliJ project.
	 * @return the corresponding service instance.
	 */
	public static GitHubTicketCache getInstance(Project project) {
		return project.getService(GitHubTicketCache.class);
	}

	@Override
	public TicketData getState() {
		return data.snapshot();
	}

	@Override
	public void loadState(TicketData state) {

		TicketData snapshot = state.snapshot();
		synchronized (data.repositories) {
			data.repositories.clear();
			data.repositories.putAll(snapshot.repositories);
		}
	}

	/**
	 * Return the stored labels for the given repository coordinates.
	 *
	 * @return the stored labels; empty if the repository has no stored listing.
	 */
	List<GitHubLabel> getLabels(GitRepositoryMetadata coordinates) {

		synchronized (data.repositories) {
			RepositoryData repository = data.repositories.get(key(coordinates));
			if (repository == null) {
				return List.of();
			}

			List<GitHubLabel> labels = new ArrayList<>(repository.labels.size());
			for (CachedLabel label : repository.labels) {
				labels.add(label.toGitHubLabel());
			}

			return labels;
		}
	}

	/**
	 * Return the stored open milestones for the given repository coordinates.
	 *
	 * @return the stored milestones; empty if the repository has no stored listing.
	 */
	List<GitHubMilestone> getMilestones(GitRepositoryMetadata coordinates) {

		synchronized (data.repositories) {
			RepositoryData repository = data.repositories.get(key(coordinates));
			if (repository == null) {
				return List.of();
			}

			List<GitHubMilestone> milestones = new ArrayList<>(repository.milestones.size());
			for (CachedMilestone milestone : repository.milestones) {
				milestones.add(milestone.toGitHubMilestone());
			}

			return milestones;
		}
	}

	/**
	 * Replace the stored labels for the given repository coordinates.
	 */
	void storeLabels(GitRepositoryMetadata coordinates, List<GitHubLabel> labels) {

		List<CachedLabel> stored = new ArrayList<>(labels.size());
		for (GitHubLabel label : labels) {
			stored.add(CachedLabel.fromGitHubLabel(label));
		}

		String repositoryKey = key(coordinates);
		synchronized (data.repositories) {
			RepositoryData existing = data.repositories.get(repositoryKey);

			RepositoryData fresh = new RepositoryData();
			fresh.labels = stored;
			if (existing != null) {
				fresh.milestones = existing.milestones;
			}
			data.repositories.put(repositoryKey, fresh);
		}
	}

	/**
	 * Replace the stored milestones for the given repository coordinates.
	 */
	void storeMilestones(GitRepositoryMetadata coordinates, List<GitHubMilestone> milestones) {

		List<CachedMilestone> stored = new ArrayList<>(milestones.size());
		for (GitHubMilestone milestone : milestones) {
			stored.add(CachedMilestone.fromGitHubMilestone(milestone));
		}

		String repositoryKey = key(coordinates);
		synchronized (data.repositories) {
			RepositoryData existing = data.repositories.get(repositoryKey);

			RepositoryData fresh = new RepositoryData();
			fresh.milestones = stored;
			if (existing != null) {
				fresh.labels = existing.labels;
			}
			data.repositories.put(repositoryKey, fresh);
		}
	}

	private static String key(GitRepositoryMetadata coordinates) {
		return "%s/%s/%s".formatted(coordinates.host(), coordinates.owner(), coordinates.repository());
	}

	/**
	 * Persisted service state.
	 */
	public static class TicketData {

		public Map<String, RepositoryData> repositories = new ConcurrentHashMap<>();

		TicketData snapshot() {

			TicketData copy = new TicketData();
			synchronized (repositories) {
				for (Map.Entry<String, RepositoryData> entry : repositories.entrySet()) {
					copy.repositories.put(entry.getKey(), entry.getValue().snapshot());
				}
			}

			return copy;
		}

	}

	/**
	 * Stored listings of one GitHub repository.
	 */
	public static class RepositoryData {

		@Tag
		@XCollection(propertyElementName = "labels", elementName = "label", style = XCollection.Style.v2)
		public List<CachedLabel> labels = new ArrayList<>();

		@Tag
		@XCollection(propertyElementName = "milestones", elementName = "milestone", style = XCollection.Style.v2)
		public List<CachedMilestone> milestones = new ArrayList<>();

		RepositoryData snapshot() {

			RepositoryData copy = new RepositoryData();
			for (CachedLabel label : labels) {
				copy.labels.add(label.snapshot());
			}
			for (CachedMilestone milestone : milestones) {
				copy.milestones.add(milestone.snapshot());
			}

			return copy;
		}

	}

	/**
	 * Stored label entry.
	 */
	@Tag("label")
	public static class CachedLabel {

		public String name = "";

		public String description = "";

		public @Nullable String color;

		GitHubLabel toGitHubLabel() {
			return new GitHubLabel(name, description, color);
		}

		CachedLabel snapshot() {

			CachedLabel copy = new CachedLabel();
			copy.name = this.name;
			copy.description = this.description;
			copy.color = this.color;

			return copy;
		}

		static CachedLabel fromGitHubLabel(GitHubLabel label) {

			CachedLabel cachedLabel = new CachedLabel();
			cachedLabel.name = label.getName();
			cachedLabel.description = label.getDescription();
			cachedLabel.color = label.getHexColor();

			return cachedLabel;
		}

	}

	/**
	 * Stored milestone entry.
	 */
	@Tag("milestone")
	public static class CachedMilestone {

		public long number;

		public String title = "";

		public @Nullable String description;

		public @Nullable String releaseDate;

		GitHubMilestone toGitHubMilestone() {

			LocalDateTime releaseDate = this.releaseDate != null ? LocalDateTime.parse(this.releaseDate) : null;

			return new GitHubMilestone(number, title, true, description, releaseDate);
		}

		CachedMilestone snapshot() {

			CachedMilestone copy = new CachedMilestone();
			copy.number = this.number;
			copy.title = this.title;
			copy.description = this.description;
			copy.releaseDate = this.releaseDate;

			return copy;
		}

		static CachedMilestone fromGitHubMilestone(GitHubMilestone milestone) {

			CachedMilestone cachedMilestone = new CachedMilestone();
			cachedMilestone.number = milestone.getNumber();
			cachedMilestone.title = milestone.getTitle();
			cachedMilestone.description = milestone.getDescription();
			LocalDateTime releaseDate = milestone.getReleaseDate();
			cachedMilestone.releaseDate = releaseDate != null ? releaseDate.toString() : null;

			return cachedMilestone;
		}

	}

}
