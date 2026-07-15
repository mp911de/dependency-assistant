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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.check.DeclaredVersions;
import biz.paluch.dap.assistant.check.DependencyUpdateCandidate;
import biz.paluch.dap.assistant.check.UpgradeCandidate;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.plan.UpgradePlanState.Content;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.plan.UpgradePlanState.Plan;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradePlanService} command history.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpgradePlanServiceUnitTests {

	private static final ArtifactVersion CURRENT = ArtifactVersion.of("1.0.0");

	private static final ArtifactVersion TARGET = ArtifactVersion.of("1.1.0");

	@Test
	void repeatedDeletesUndoAndRedoInSequence(Project project) {

		List<UpgradePlanItem> items = materializedItems(project, "alpha", "bravo");
		UpgradePlanService service = UpgradePlanService.getInstance(project);
		UndoManager undoManager = UndoManager.getInstance(project);
		try {
			service.removeItems(List.of(items.get(0)));
			service.removeItems(List.of(items.get(1)));
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

	private static List<UpgradePlanItem> materializedItems(Project project, String... names) {

		Content content = new Content();
		content.getAffectedFiles().add("pom.xml");
		List<UpgradePlanItem> items = new java.util.ArrayList<>();
		for (String name : names) {
			UpgradeCandidate candidate = candidate(name);
			Item stored = Item.from(candidate, TARGET);
			UpgradePlanItem item = new UpgradePlanItem(stored.getId(), candidate, TARGET);
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

	private static UpgradeCandidate candidate(String name) {

		Dependency dependency = new Dependency(ArtifactId.of("org.example", name), CURRENT);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.declared(CURRENT.toString()));
		VulnerabilityRepository vulnerabilities = VulnerabilityRepository.of(
				Map.of(CURRENT, Vulnerabilities.clean(), TARGET, Vulnerabilities.clean()));
		return new UpgradeCandidate(
				new DependencyUpdateCandidate(dependency, Releases.just(Release.of(TARGET)), vulnerabilities),
				TestInterfaceAssistant.INSTANCE, DeclaredVersions.of(CURRENT));
	}

}
