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
import java.util.List;

import biz.paluch.dap.artifact.GitRepositoryMetadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitHubTicketCache}.
 *
 * @author Mark Paluch
 */
class GitHubTicketCacheUnitTests {

	private static final GitRepositoryMetadata COORDINATES = new GitRepositoryMetadata("github.com", "mp911de",
			"dependency-assistant");

	private static final String KEY = "github.com/mp911de/dependency-assistant";

	@Test
	void getStateReturnsSnapshotDetachedFromLiveState() {

		GitHubTicketCache cache = new GitHubTicketCache();
		cache.storeLabels(COORDINATES, List.of(new GitHubLabel("bug", "", "d93f0b")));
		cache.storeMilestones(COORDINATES, List.of(new GitHubMilestone(7, "2026.1", true, "First release",
				LocalDateTime.of(2026, 8, 1, 0, 0))));

		GitHubTicketCache.TicketData snapshot = cache.getState();
		GitHubTicketCache.RepositoryData repository = snapshot.repositories.get(KEY);
		repository.labels.getFirst().name = "changed";
		repository.milestones.getFirst().title = "changed";
		snapshot.repositories.clear();

		assertThat(cache.getLabels(COORDINATES)).singleElement().satisfies(label -> {
			assertThat(label.getName()).isEqualTo("bug");
			assertThat(label.getHexColor()).isEqualTo("d93f0b");
		});
		assertThat(cache.getMilestones(COORDINATES)).singleElement().satisfies(milestone -> {
			assertThat(milestone.getTitle()).isEqualTo("2026.1");
			assertThat(milestone.getReleaseDate()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
		});
	}

	@Test
	void loadStateCopiesIncomingData() {

		GitHubTicketCache.CachedLabel label = new GitHubTicketCache.CachedLabel();
		label.name = "bug";
		label.color = "d93f0b";

		GitHubTicketCache.CachedMilestone milestone = new GitHubTicketCache.CachedMilestone();
		milestone.number = 7;
		milestone.title = "2026.1";
		milestone.description = "First release";
		milestone.releaseDate = "2026-08-01T00:00";

		GitHubTicketCache.RepositoryData repository = new GitHubTicketCache.RepositoryData();
		repository.labels.add(label);
		repository.milestones.add(milestone);

		GitHubTicketCache.TicketData state = new GitHubTicketCache.TicketData();
		state.repositories.put(KEY, repository);

		GitHubTicketCache cache = new GitHubTicketCache();
		cache.loadState(state);
		label.name = "changed";
		milestone.title = "changed";
		state.repositories.clear();

		assertThat(cache.getLabels(COORDINATES)).extracting(GitHubLabel::getName).containsExactly("bug");
		assertThat(cache.getMilestones(COORDINATES)).extracting(GitHubMilestone::getTitle).containsExactly("2026.1");
	}

}
