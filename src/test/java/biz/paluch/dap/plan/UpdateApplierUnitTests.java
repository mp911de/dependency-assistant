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
import java.util.Map;
import java.util.Objects;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.plan.UpgradePlanState.Content;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.plan.UpgradePlanState.Plan;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeResult;
import biz.paluch.dap.upgrade.UpgradeDecision;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link UpdateApplier}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdateApplierUnitTests {

	private static final ArtifactVersion CURRENT = ArtifactVersion.of("1.0.0");

	private static final ArtifactVersion TARGET = ArtifactVersion.of("1.1.0");

	@Test
	void appliesSeveralItemsAsSeparatePlatformUndoSteps(Project project) throws Exception {

		TestUpgradePlanCapture alpha = candidate("alpha");
		TestUpgradePlanCapture bravo = candidate("bravo");
		List<UpgradePlanItem> items = materializedItems(project, alpha, bravo);
		UpgradePlanItem first = items.get(0);
		UpgradePlanItem second = items.get(1);

		UpgradePlanService service = UpgradePlanService.getInstance(project);
		ChangedFileUpdateEngine engine = new ChangedFileUpdateEngine(project);
		UpdateApplier applier = new UpdateApplier(service, engine);

		UpgradePlan plan = UpgradePlan.of(FileScope.of(), items);

		int applied = applier.apply(plan, new EmptyProgressIndicator());
		assertThat(applied).isEqualTo(2);
		assertThat(service.getUpgradePlan().isEmpty()).isTrue();
		assertThat(engine.document.getText()).isEqualTo("before applied applied");

		UndoManager undoManager = UndoManager.getInstance(project);
		TestDialog previous = TestDialogManager.setTestDialog(TestDialog.YES);
		try {
			undoManager.undo(null);
			assertThat(service.getUpgradePlan()).containsExactly(second);
			assertThat(engine.document.getText()).isEqualTo("before applied");

			undoManager.undo(null);
			assertThat(service.getUpgradePlan()).containsExactly(first, second);
			assertThat(engine.document.getText()).isEqualTo("before");

			undoManager.redo(null);
			assertThat(service.getUpgradePlan()).containsExactly(second);
			assertThat(engine.document.getText()).isEqualTo("before applied");

			undoManager.redo(null);
		} finally {
			((UndoManagerImpl) undoManager).dropHistoryInTests();
			TestDialogManager.setTestDialog(previous);
		}
		assertThat(service.getUpgradePlan().isEmpty()).isTrue();
		assertThat(engine.document.getText()).isEqualTo("before applied applied");
	}

	private static TestUpgradePlanCapture candidate(String name) {

		Dependency dependency = new Dependency(ArtifactId.of("org.example", name), CURRENT);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.declared(CURRENT.toString()));
		VulnerabilityRepository vulnerabilities = VulnerabilityRepository.of(
				Map.of(CURRENT, Vulnerabilities.clean(), TARGET, Vulnerabilities.clean()));
		return new TestUpgradePlanCapture(
				UpgradeDecision.create(dependency, Releases.just(Release.of(TARGET)), vulnerabilities));
	}

	private static List<UpgradePlanItem> materializedItems(Project project, TestUpgradePlanCapture... candidates) {

		Content content = new Content();
		content.getAffectedFiles().add("pom.xml");
		List<UpgradePlanItem> items = new java.util.ArrayList<>();
		for (TestUpgradePlanCapture candidate : candidates) {
			Item stored = Item.from(candidate, TARGET);
			UpgradePlanItem item = Objects.requireNonNull(
					new UpgradePlanLoader(List.of(TestInterfaceAssistant.INSTANCE), null).create(stored));
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

	private static class ChangedFileUpdateEngine extends FileUpdateEngine {

		private final Document document = EditorFactory.getInstance().createDocument("before");

		ChangedFileUpdateEngine(Project project) {
			super(project);
		}

		@Override
		UpgradeResult apply(FileScope scope, List<DependencyUpdate> updates) {
			WriteAction.run(() -> document.insertString(document.getTextLength(), " applied"));
			return UpgradeResult.changed();
		}

	}

}
