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

import java.util.List;
import java.util.stream.Stream;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.fixtures.TestCandidates;
import biz.paluch.dap.state.ApplicationSettings;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradePlanService} command history and plan actions.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpgradePlanServiceUnitTests {

	private static final ArtifactVersion CURRENT = ArtifactVersion.of("1.0.0");

	private static final ArtifactVersion TARGET = ArtifactVersion.of("1.1.0");

	@Test
	void repeatedDeletesUndoAndRedoInSequence(Project project) {

		List<UpgradePlanItem> items = TestPlannedUpgrade.create(project, TARGET, Stream.of("alpha", "bravo")
				.map(UpgradePlanServiceUnitTests::candidate).toList());
		UpgradePlanService service = UpgradePlanService.getInstance(project);
		UndoManager undoManager = UndoManager.getInstance(project);
		try {

			items.forEach(service::removeItem);
			assertThat(service.getUpgradePlan().isEmpty()).isTrue();

			undoManager.undo(null);
			assertThat(service.getUpgradePlan().getItems()).containsExactly(items.get(1));

			undoManager.undo(null);
			assertThat(service.getUpgradePlan().getItems()).containsExactlyElementsOf(items);

			undoManager.redo(null);
			assertThat(service.getUpgradePlan().getItems()).containsExactly(items.get(1));

			undoManager.redo(null);
			assertThat(service.getUpgradePlan().isEmpty()).isTrue();
		} finally {
			((UndoManagerImpl) undoManager).dropHistoryInTests();
		}
	}

	@Test
	void pasteActionSnapshotsContentForRedo() {

		UpgradePlanState.Content pasted = new UpgradePlanState.Content();
		pasted.getAffectedFiles().add("pasted.xml");
		UpgradePlanState.Content content = new UpgradePlanState.Content();
		content.getAffectedFiles().add("current.xml");
		PlanAction action = PlanAction.pasteItems(pasted, content);

		pasted.getAffectedFiles().add("later.xml");
		action.apply();
		assertThat(content.getAffectedFiles()).containsExactly("current.xml", "pasted.xml");

		action.undo();
		action.apply();

		assertThat(content.getAffectedFiles()).containsExactly("current.xml", "pasted.xml");
	}

	@Test
	void renameActionAppliesUndoesAndRedoes() {

		UpgradePlanState.Item stored = UpgradePlanState.Item.from(candidate("alpha"), TARGET);
		UpgradePlanItem item = TestPlannedUpgrade.LOADER.create(stored);
		stored.setMaterialized(item);
		UpgradePlanState.Content content = new UpgradePlanState.Content();
		content.getItems().add(stored);

		PlanAction action = PlanAction.renameItem(content, item, "Alpha", false);
		action.apply();
		assertThat(stored.getDisplayName()).isEqualTo("Alpha");
		assertThat(item.getDisplayName()).isEqualTo("Alpha");

		action.undo();
		assertThat(stored.getDisplayName()).isEqualTo("alpha");
		assertThat(item.getDisplayName()).isEqualTo("alpha");

		action.apply();
		assertThat(stored.getDisplayName()).isEqualTo("Alpha");
		assertThat(item.getDisplayName()).isEqualTo("Alpha");
	}

	@Test
	void renameActionRemembersNameWhenRequested() {

		UpgradePlanState.Item stored = UpgradePlanState.Item.from(candidate("alpha"), TARGET);
		UpgradePlanItem item = TestPlannedUpgrade.LOADER.create(stored);
		stored.setMaterialized(item);
		UpgradePlanState.Content content = new UpgradePlanState.Content();
		content.getItems().add(stored);
		List<PackageIdentity> packages = List.of(item.getMembers().getFirst().getPackageIdentity());
		ApplicationSettings settings = ApplicationSettings.getInstance();
		settings.doWithState((Consumer<? super ApplicationSettings.State>) state -> state.getNameHints().clear());

		PlanAction action = PlanAction.renameItem(content, item, "Alpha", true);
		try {
			action.apply();
			assertThat(settings.findNameHint(packages)).isEqualTo("Alpha");

			action.undo();
			assertThat(settings.findNameHint(packages)).isNull();
		} finally {
			settings.removeNameHint("Alpha", packages);
		}
	}

	@Test
	void renameItemIsUndoable(Project project) {

		ApplicationSettings settings = ApplicationSettings.getInstance();
		settings.doWithState((Consumer<? super ApplicationSettings.State>) state -> state.getNameHints().clear());
		List<UpgradePlanItem> items = TestPlannedUpgrade.create(project, TARGET, candidate("alpha"));
		UpgradePlanService service = UpgradePlanService.getInstance(project);
		UndoManager undoManager = UndoManager.getInstance(project);
		try {

			assertThat(settings.findNameHint(items.getFirst().getMembers().getFirst()
					.getPackageIdentity())).isNull();

			service.renameItem(items.getFirst(), "Alpha", true);
			assertThat(service.getUpgradePlan().getItems().getFirst().getDisplayName()).isEqualTo("Alpha");

			assertThat(settings.findNameHint(items.getFirst().getMembers().getFirst()
					.getPackageIdentity())).isEqualTo("Alpha");

			undoManager.undo(null);
			assertThat(service.getUpgradePlan().getItems().getFirst().getDisplayName()).isEqualTo("alpha");

			assertThat(settings.findNameHint(items.getFirst().getMembers().getFirst()
					.getPackageIdentity())).isNull();

			undoManager.redo(null);
			assertThat(service.getUpgradePlan().getItems().getFirst().getDisplayName()).isEqualTo("Alpha");

			assertThat(settings.findNameHint(items.getFirst().getMembers().getFirst()
					.getPackageIdentity())).isEqualTo("Alpha");
		} finally {
			((UndoManagerImpl) undoManager).dropHistoryInTests();
		}
	}

	private static TestPlannedUpgrade candidate(String name) {
		return new TestPlannedUpgrade(TestCandidates.candidate(ArtifactId.of("org.example", name), CURRENT,
				it -> it.releases(TARGET)));
	}

}
