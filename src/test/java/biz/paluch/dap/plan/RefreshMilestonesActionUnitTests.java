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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.plan.InMemoryTicketRepository.InMemoryLabel;
import biz.paluch.dap.plan.InMemoryTicketRepository.InMemoryMilestone;
import biz.paluch.dap.ticket.Milestone;
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

		InMemoryMilestone milestone = InMemoryMilestone.open("6.2");
		Milestones milestones = new Milestones(List.of(milestone));

		assertThat(milestones.findMilestone("6.2")).isSameAs(milestone);
	}

	@Test
	void defaultsToLowestOpenMilestoneFromBranch() {

		InMemoryMilestone patch = InMemoryMilestone.open("6.2.1");
		InMemoryMilestone line = InMemoryMilestone.open("6.2");
		InMemoryMilestone closed = InMemoryMilestone.closed("6.1");
		Milestones milestones = new Milestones(
				List.of(patch, closed, line));

		Milestone selected = milestones.findDefaultMilestone("release/6.2.x", projectVersion("7.1.0"));

		assertThat(selected).isSameAs(line);
	}

	@Test
	void retainsPersistedMilestoneBeforeApplyingDefault() {

		InMemoryMilestone persisted = InMemoryMilestone.open("custom");
		InMemoryMilestone branchDefault = InMemoryMilestone.open("6.2");
		Milestones milestones = new Milestones(
				List.of(branchDefault, persisted));

		Milestone selected = milestones.findOrDefault("custom", "release/6.2.x",
				Versioned.unversioned());

		assertThat(selected).isSameAs(persisted);
	}

	@Test
	void defaultsFromProjectVersionWhenBranchHasNoVersion() {

		InMemoryMilestone patch = InMemoryMilestone.open("7.1.1");
		InMemoryMilestone line = InMemoryMilestone.open("7.1");
		Milestones milestones = new Milestones(List.of(patch, line));

		Milestone selected = milestones.findDefaultMilestone("main", projectVersion("7.1.3"));

		assertThat(selected).isSameAs(line);
	}

	@Test
	void rebindsPersistedLabelBeforeApplyingDefault() {

		InMemoryLabel custom = InMemoryLabel.of("custom");
		InMemoryLabel dependency = InMemoryLabel.of("dependencies");
		RefreshMilestonesAction.Labels labels = new RefreshMilestonesAction.Labels(List.of(dependency, custom));

		assertThat(labels.getSelection("custom")).isSameAs(custom);
		assertThat(labels.getSelection(null)).isSameAs(dependency);
	}

	private static Versioned projectVersion(String version) {
		return Versioned.of(ArtifactVersion.of(version));
	}

}
