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

package biz.paluch.dap.plan;

import java.awt.Color;
import java.time.LocalDateTime;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link RefreshMilestonesAction} selection policy.
 *
 * @author Mark Paluch
 */
class RefreshMilestonesActionUnitTests {

	@Test
	void rebindsPersistedMilestoneByTitle() {

		TestMilestone milestone = new TestMilestone("6.2", true);
		RefreshMilestonesAction.Milestones milestones = new RefreshMilestonesAction.Milestones(List.of(milestone));

		assertThat(milestones.getSelection("6.2")).isSameAs(milestone);
	}

	@Test
	void defaultsToLowestOpenMilestoneFromBranch() {

		TestMilestone patch = new TestMilestone("6.2.1", true);
		TestMilestone line = new TestMilestone("6.2", true);
		TestMilestone closed = new TestMilestone("6.1", false);
		RefreshMilestonesAction.Milestones milestones = new RefreshMilestonesAction.Milestones(
				List.of(patch, closed, line));

		Milestone selected = milestones.getDefaultMilestone("release/6.2.x",
				Versioned.of(ArtifactVersion.of("7.1.0")));

		assertThat(selected).isSameAs(line);
	}

	@Test
	void retainsPersistedMilestoneBeforeApplyingDefault() {

		TestMilestone persisted = new TestMilestone("custom", true);
		TestMilestone branchDefault = new TestMilestone("6.2", true);
		RefreshMilestonesAction.Milestones milestones = new RefreshMilestonesAction.Milestones(
				List.of(branchDefault, persisted));

		Milestone selected = milestones.getSelectionOrDefault("custom", "release/6.2.x",
				Versioned.unversioned());

		assertThat(selected).isSameAs(persisted);
	}

	@Test
	void defaultsFromProjectVersionWhenBranchHasNoVersion() {

		TestMilestone patch = new TestMilestone("7.1.1", true);
		TestMilestone line = new TestMilestone("7.1", true);
		RefreshMilestonesAction.Milestones milestones = new RefreshMilestonesAction.Milestones(List.of(patch, line));

		Milestone selected = milestones.getDefaultMilestone("main",
				Versioned.of(ArtifactVersion.of("7.1.3")));

		assertThat(selected).isSameAs(line);
	}

	@Test
	void rebindsPersistedLabelBeforeApplyingDefault() {

		TestLabel custom = new TestLabel("custom");
		TestLabel dependency = new TestLabel("dependencies");
		RefreshMilestonesAction.Labels labels = new RefreshMilestonesAction.Labels(List.of(dependency, custom));

		assertThat(labels.getSelection("custom")).isSameAs(custom);
		assertThat(labels.getSelection(null)).isSameAs(dependency);
	}

	private static class TestMilestone implements Milestone {

		private final String title;

		private final boolean open;

		TestMilestone(String title, boolean open) {
			this.title = title;
			this.open = open;
		}

		@Override
		public String getTitle() {
			return title;
		}

		@Override
		public boolean isOpen() {
			return open;
		}

		@Override
		public @Nullable String getDescription() {
			return null;
		}

		@Override
		public @Nullable LocalDateTime getReleaseDate() {
			return null;
		}

	}

	private static class TestLabel implements Label {

		private final String name;

		TestLabel(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public @Nullable Color getColor() {
			return null;
		}

	}

}
