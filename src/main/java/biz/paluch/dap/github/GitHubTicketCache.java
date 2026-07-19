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
import java.util.function.Consumer;
import java.util.function.Function;

import biz.paluch.dap.artifact.GitRepositoryMetadata;
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jspecify.annotations.Nullable;

/**
 * Project-level service persisting GitHub label and milestone listings across
 * IDE restarts.
 * <p>Mutations count against a modification tracker so the persistence layer
 * skips snapshotting and serialization while the listings are unchanged.
 *
 * @author Mark Paluch
 */
@State(name = "DependencyAssistantGitHubTickets", storages = @Storage("dependency-assistant-github.xml"))
public class GitHubTicketCache
		implements PersistentStateComponentWithModificationTracker<GitHubTicketCache.Repositories> {

	private final Repositories state = new Repositories();

	private final SimpleModificationTracker modificationTracker = new SimpleModificationTracker();

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
	public Repositories getState() {
		return readRepositories(Repositories::snapshot);
	}

	@Override
	public long getStateModificationCount() {
		return modificationTracker.getModificationCount();
	}

	@Override
	public void loadState(Repositories state) {

		Repositories snapshot = state.snapshot();
		writeRepositories(current -> {
			current.repositories.clear();
			current.repositories.addAll(snapshot.repositories);
		});
	}

	/**
	 * Return the stored labels for the given repository coordinates.
	 *
	 * @return the stored labels; empty if the repository has no stored listing.
	 */
	List<GitHubLabel> getLabels(GitRepositoryMetadata coordinates) {
		return readRepositories(state -> {

			Repository repository = state.getRepository(coordinates);
			if (repository == null) {
				return List.of();
			}

			List<GitHubLabel> labels = new ArrayList<>(repository.labels.size());
			for (CachedLabel label : repository.labels) {
				labels.add(label.toGitHubLabel());
			}

			return labels;
		});
	}

	/**
	 * Return the stored open milestones for the given repository coordinates.
	 *
	 * @return the stored milestones; empty if the repository has no stored listing.
	 */
	List<GitHubMilestone> getMilestones(GitRepositoryMetadata coordinates) {
		return readRepositories(repositories -> {

			Repository repository = repositories.getRepository(coordinates);
			if (repository == null) {
				return List.of();
			}

			List<GitHubMilestone> milestones = new ArrayList<>(repository.milestones.size());
			for (CachedMilestone milestone : repository.milestones) {
				milestones.add(milestone.toGitHubMilestone());
			}

			return milestones;
		});
	}

	/**
	 * Replace the stored labels for the given repository coordinates.
	 */
	void storeLabels(GitRepositoryMetadata coordinates, List<GitHubLabel> labels) {

		List<CachedLabel> stored = new ArrayList<>(labels.size());
		for (GitHubLabel label : labels) {
			stored.add(CachedLabel.fromGitHubLabel(label));
		}

		writeRepositories(repositories -> {
			Repository repository = repositories.getOrCreateRepository(coordinates);
			repository.labels = stored;
		});
	}

	/**
	 * Replace the stored milestones for the given repository coordinates.
	 */
	void storeMilestones(GitRepositoryMetadata coordinates, List<GitHubMilestone> milestones) {

		List<CachedMilestone> stored = new ArrayList<>(milestones.size());
		for (GitHubMilestone milestone : milestones) {
			stored.add(CachedMilestone.fromGitHubMilestone(milestone));
		}

		writeRepositories(repositories -> {
			Repository repository = repositories.getOrCreateRepository(coordinates);
			repository.milestones = stored;
		});
	}

	private <T extends @Nullable Object> T readRepositories(Function<Repositories, T> action) {
		synchronized (state.repositories) {
			return action.apply(state);
		}
	}

	private void writeRepositories(Consumer<Repositories> action) {
		synchronized (state.repositories) {
			modificationTracker.incModificationCount();
			action.accept(state);
		}
	}


	/**
	 * Persisted service state.
	 */
	@Tag("repositories")
	public static class Repositories {

		@XCollection(propertyElementName = "repositories", style = XCollection.Style.v2)
		public final List<Repository> repositories = new ArrayList<>();

		Repositories snapshot() {

			Repositories copy = new Repositories();
			for (Repository repository : repositories) {
				copy.repositories.add(repository.snapshot());
			}

			return copy;
		}

		public @Nullable Repository getRepository(GitRepositoryMetadata coordinates) {

			String key = key(coordinates);
			for (Repository repository : repositories) {
				if (key.equals(repository.key)) {
					return repository;
				}
			}
			return null;
		}

		public Repository getOrCreateRepository(GitRepositoryMetadata coordinates) {

			Repository repository = getRepository(coordinates);
			if (repository == null) {
				repository = new Repository();
				repository.key = key(coordinates);
				repositories.add(repository);
			}
			return repository;
		}

		private static String key(GitRepositoryMetadata coordinates) {
			return "%s/%s/%s".formatted(coordinates.host(), coordinates.owner(), coordinates.repository());
		}

	}

	/**
	 * Stored listings of one GitHub repository, keyed by host, owner, and
	 * repository name.
	 */
	@Tag("repository")
	public static class Repository {

		public @Attribute String key = "";

		@XCollection(propertyElementName = "labels", elementName = "label", style = XCollection.Style.v2)
		public List<CachedLabel> labels = new ArrayList<>();

		@XCollection(propertyElementName = "milestones", elementName = "milestone", style = XCollection.Style.v2)
		public List<CachedMilestone> milestones = new ArrayList<>();

		Repository snapshot() {

			Repository copy = new Repository();
			copy.key = this.key;
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

		public @Attribute String name = "";

		public @Attribute String description = "";

		public @Attribute @Nullable String color;

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

		public @Attribute long number;

		public @Attribute String title = "";

		public @Attribute @Nullable String description;

		public @Attribute @Nullable String releaseDate;

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
