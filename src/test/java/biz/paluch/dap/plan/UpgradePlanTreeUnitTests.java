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

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;

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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.StatusText;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradePlanTree} interaction behavior.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpgradePlanTreeUnitTests {

	@Test
	void disablingSortingRestoresLatestPlanOrder(Project project) {

		ApplicationManager.getApplication().invokeAndWait(() -> {
			UpgradePlanItem patch = item("patch", "1.0.1");
			UpgradePlanItem minor = item("minor", "1.1.0");
			UpgradePlanItem major = item("major", "2.0.0");
			UpgradePlanTree planTree = new UpgradePlanTree(() -> {
			}, () -> {
			}, item -> {
			},
					UpgradePlanService.getInstance(project), false, false);
			planTree.setItems(List.of(patch, minor, major));
			planTree.setSortByAttention(true);
			planTree.refreshItems(List.of(minor, patch, major));
			planTree.setSortByAttention(false);

			Tree tree = (Tree) planTree.focusTarget();
			DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
			assertThat(root.children().asIterator())
					.toIterable()
					.extracting(node -> ((UpgradePlanItem) ((DefaultMutableTreeNode) node)
							.getUserObject()).getDisplayName())
					.containsExactly("minor", "patch", "major");
		});
	}

	@Test
	void emptyStateLinkUsesHandCursor(Project project) {

		ApplicationManager.getApplication().invokeAndWait(() -> {
			UpgradePlanTree planTree = new UpgradePlanTree(() -> {
			}, () -> {
			}, item -> {
			},
					UpgradePlanService.getInstance(project), false, false);
			JComponent component = planTree.focusTarget();
			component.setSize(480, 320);

			StatusText emptyText = ((Tree) component).getEmptyText();
			Dimension size = emptyText.getPreferredSize();
			Point below = emptyText.getPointBelow();
			boolean handCursorFound = false;
			for (int y = below.y - size.height; y < below.y && !handCursorFound; y++) {
				for (int x = below.x; x < below.x + size.width; x++) {
					component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_MOVED,
							System.currentTimeMillis(), 0, x, y, 0, false));
					if (component.getCursor().getType() == Cursor.HAND_CURSOR) {
						handCursorFound = true;
						break;
					}
				}
			}

			assertThat(handCursorFound).isTrue();
		});
	}

	private static UpgradePlanItem item(String name, String target) {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		ArtifactVersion targetVersion = ArtifactVersion.of(target);
		Dependency dependency = new Dependency(ArtifactId.of("org.example", name), current);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.declared(current.toString()));
		VulnerabilityRepository vulnerabilities = VulnerabilityRepository.of(
				Map.of(current, Vulnerabilities.clean(), targetVersion, Vulnerabilities.clean()));
		UpgradeCandidate candidate = new UpgradeCandidate(
				new DependencyUpdateCandidate(dependency, Releases.just(Release.of(targetVersion)), vulnerabilities),
				TestInterfaceAssistant.INSTANCE, DeclaredVersions.of(current));
		UpgradePlanState.Item stored = UpgradePlanState.Item.from(candidate, targetVersion);
		return new UpgradePlanItem(stored.getId(), candidate, targetVersion);
	}

}
