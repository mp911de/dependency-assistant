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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.fixtures.TestCandidates;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.support.UpgradeResult;
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

	static ArtifactVersion CURRENT = ArtifactVersion.of("1.0.0");

	static ArtifactVersion TARGET = ArtifactVersion.of("1.1.0");

	@Test
	void appliesSeveralItemsAsSeparatePlatformUndoSteps(Project project) throws Exception {

		TestPlannedUpgrade alpha = candidate("alpha");
		TestPlannedUpgrade bravo = candidate("bravo");
		List<UpgradePlanItem> items = TestPlannedUpgrade.create(project, TARGET, alpha, bravo);
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

	private static TestPlannedUpgrade candidate(String name) {
		return new TestPlannedUpgrade(TestCandidates.candidate(ArtifactId.of("org.example", name), CURRENT,
				it -> it.releases(TARGET)));
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
