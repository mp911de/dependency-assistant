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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.fixtures.TestAssistant;
import biz.paluch.dap.fixtures.TestCandidates;
import biz.paluch.dap.plan.UpgradePlanState.Content;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.plan.UpgradePlanState.Plan;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;

class TestPlannedUpgrade implements PlannedUpgrade {

	private final List<DependencyUpgradeCandidate> upgrades;

	private final String name;

	TestPlannedUpgrade(DependencyUpgradeCandidate upgrade) {
		this(List.of(upgrade), upgrade.getArtifactId().artifactId());
	}

	TestPlannedUpgrade(List<DependencyUpgradeCandidate> upgrades, String name) {
		this.upgrades = List.copyOf(upgrades);
		this.name = name;
	}

	/**
	 * Materialize the given planned upgrades toward {@code target} into a plan
	 * persisted on the project and return the loaded items, with undo history
	 * cleared.
	 */
	static List<UpgradePlanItem> create(Project project, ArtifactVersion target,
			TestPlannedUpgrade... candidates) {
		return create(project, target, List.of(candidates));
	}

	/**
	 * Materialize the given planned upgrades toward {@code target} into a plan
	 * persisted on the project and return the loaded items, with undo history
	 * cleared.
	 */
	static List<UpgradePlanItem> create(Project project, ArtifactVersion target,
			Collection<TestPlannedUpgrade> candidates) {

		Content content = new Content();
		content.getAffectedFiles().add("pom.xml");
		List<UpgradePlanItem> items = new ArrayList<>();
		for (TestPlannedUpgrade candidate : candidates) {

			Item stored = Item.from(candidate, target);
			UpgradePlanItem item = new UpgradePlanLoader(List.of(TestAssistant.INSTANCE), null).create(stored);
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
	static UpgradePlanItem item(String coordinates, String target) {

		TestPlannedUpgrade candidate = new TestPlannedUpgrade(
				TestCandidates.candidate(coordinates, it -> it.releases(target)));
		Item stored = Item.from(candidate, ArtifactVersion.of(target));
		return Objects
				.requireNonNull(new UpgradePlanLoader(List.of(TestAssistant.INSTANCE), null).create(stored));
	}

	@Override
	public List<DependencyUpgradeCandidate> getUpgradeCandidates() {
		return upgrades;
	}

	@Override
	public String getName() {
		return name;
	}

}
