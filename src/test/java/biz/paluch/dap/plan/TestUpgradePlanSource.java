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

package biz.paluch.dap.plan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.fixtures.TestAssistant;
import biz.paluch.dap.fixtures.TestCandidates;
import biz.paluch.dap.plan.UpgradePlanState.Content;
import biz.paluch.dap.plan.UpgradePlanState.Plan;
import biz.paluch.dap.plan.UpgradePlanState.Upgrade;
import biz.paluch.dap.state.Cache;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;

class TestUpgradePlanSource implements UpgradePlanSource {

	public static final UpgradePlanLoader LOADER = new UpgradePlanLoader(List.of(TestAssistant.INSTANCE), null,
			new Cache());

	private final String name;

	private final List<DependencyUpgradeCandidate> upgrades;

	TestUpgradePlanSource(DependencyUpgradeCandidate upgrade) {
		this(upgrade.getArtifactId().artifactId(), List.of(upgrade));
	}

	TestUpgradePlanSource(String name, List<DependencyUpgradeCandidate> upgrades) {
		this.upgrades = List.copyOf(upgrades);
		this.name = name;
	}

	/**
	 * Materialize the given planned upgrades toward {@code target} into a plan
	 * persisted on the project and return the loaded items, with undo history
	 * cleared.
	 */
	static List<PlannedUpgrade> create(Project project, ArtifactVersion target,
			TestUpgradePlanSource... candidates) {
		return create(project, target, List.of(candidates));
	}

	/**
	 * Materialize the given planned upgrades toward {@code target} into a plan
	 * persisted on the project and return the loaded items, with undo history
	 * cleared.
	 */
	static List<PlannedUpgrade> create(Project project, ArtifactVersion target,
			Collection<TestUpgradePlanSource> candidates) {

		Content content = new Content();
		content.getAffectedFiles().add("pom.xml");
		List<PlannedUpgrade> items = new ArrayList<>();
		for (TestUpgradePlanSource candidate : candidates) {

			List<UpgradePlanState.Dependency> members = candidate.getUpgrades().stream()
					.map(UpgradePlanState.Dependency::of).toList();
			Upgrade stored = Upgrade.from(candidate.name, target, members, candidate.getUpgrades());
			PlannedUpgrade item = LOADER.create(stored);
			stored.setMaterialized(item);
			content.getItems().add(stored);
			items.add(item);
		}

		Plan persisted = new Plan();
		persisted.setContent(content);
		UpgradePlanState.getInstance(project).loadState(persisted);
		((UndoManagerImpl) UndoManager.getInstance(project)).dropHistoryInTests();
		return items;
	}

	/**
	 * Materialize a single detached plan item for the given candidate coordinates
	 * upgrading to {@code target}, without persisting a plan. Use this variant when
	 * a test feeds items directly (for example into a tree) and each item carries
	 * its own target version.
	 */
	static PlannedUpgrade item(String coordinates, String target) {

		TestUpgradePlanSource candidate = new TestUpgradePlanSource(
				TestCandidates.candidate(coordinates, it -> it.releases(target)));
		Upgrade stored = Upgrade.from(candidate, ArtifactVersion.of(target));
		return LOADER.create(stored);
	}

	@Override
	public String getDisplayName() {
		return name;
	}

	@Override
	public List<DependencyUpgradeCandidate> getUpgrades() {
		return upgrades;
	}

}
