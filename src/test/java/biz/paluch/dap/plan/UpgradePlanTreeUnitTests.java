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

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.fixtures.TestCandidates;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
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

		UpgradePlanItem patch = TestPlannedUpgrade.item("org.example:patch:1.0.0", "1.0.1");
		UpgradePlanItem minor = TestPlannedUpgrade.item("org.example:minor:1.0.0", "1.1.0");
		UpgradePlanItem major = TestPlannedUpgrade.item("org.example:major:1.0.0", "2.0.0");
		PlanTreeProbe probe = new PlanTreeProbe(project, patch, minor, major);

		probe.planTree.setSortByAttention(true);
		probe.planTree.refreshItems(List.of(minor, patch, major));
		probe.planTree.setSortByAttention(false);

		assertThat(probe.displayedNames()).containsExactly("minor", "patch", "major");
	}

	@Test
	void everyRowPaintsItsOwnBadgeWidth(Project project) {

		UpgradePlanItem patch = TestPlannedUpgrade.item("org.example:patch:1.0.0", "1.0.1");
		UpgradePlanItem major = TestPlannedUpgrade.item("org.example:major:1.0.0", "2.0.0");
		PlanTreeProbe probe = new PlanTreeProbe(project, patch, major);

		// the tree stamps one renderer per row and reuses it at one size, so a row
		// that does not lay itself out paints the previous row's badge, or none
		assertThat(probe.paintedBadgeWidth(0)).isEqualTo(PlanTreeProbe.badgeWidth(patch.getAttentionBadge()));
		assertThat(probe.paintedBadgeWidth(1)).isEqualTo(PlanTreeProbe.badgeWidth(major.getAttentionBadge()));
	}

	@Test
	void badgesHaveDistinctHitZones(Project project) {

		UpgradePlanItem item = TestPlannedUpgrade.item("org.example:major:1.0.0", "2.0.0");
		item.setTicket(new UpgradeTicket("123", "#123", "https://tickets.example/123", "test"));
		PlanTreeProbe probe = new PlanTreeProbe(project, item);

		List<Integer> ticketHits = probe.badgeHits(0, item.getTicketBadge().tooltip());
		List<Integer> attentionHits = probe.badgeHits(0, item.getAttentionBadge().tooltip());

		assertThat(ticketHits).isNotEmpty();
		assertThat(attentionHits).isNotEmpty();
		assertThat(ticketHits.getLast()).isLessThan(attentionHits.getFirst());
		assertThat(attentionHits.getFirst() - ticketHits.getLast() - 1).isEqualTo(JBUI.scale(6));
	}

	@Test
	void gutterWidthUsesWidestTicketBadge(Project project) {

		UpgradePlanItem shortTicket = TestPlannedUpgrade.item("org.example:short:1.0.0", "1.0.1");
		shortTicket.setTicket(new UpgradeTicket("123", "#1", "https://tickets.example/123", "test"));
		UpgradePlanItem longTicket = TestPlannedUpgrade.item("org.example:long:1.0.0", "1.0.1");
		longTicket.setTicket(new UpgradeTicket("123", "#123456789", "https://tickets.example/123", "test"));

		PlanTreeProbe compact = new PlanTreeProbe(project, shortTicket);
		PlanTreeProbe mixed = new PlanTreeProbe(project, shortTicket, longTicket);
		int expectedGrowth = PlanTreeProbe.badgeWidth(longTicket.getTicketBadge())
				- PlanTreeProbe.badgeWidth(shortTicket.getTicketBadge());
		int mixedWidth = mixed.paintedGutterWidth(0);

		assertThat(mixedWidth - compact.paintedGutterWidth(0)).isEqualTo(expectedGrowth);
		assertThat(mixed.paintedGutterWidth(1)).isEqualTo(mixedWidth);
	}

	@Test
	void refreshingItemRefreshesTicketHitZone(Project project) {

		UpgradePlanItem item = TestPlannedUpgrade.item("org.example:patch:1.0.0", "1.0.1");
		PlanTreeProbe probe = new PlanTreeProbe(project, item);

		item.setTicket(new UpgradeTicket("123", "#123", "https://tickets.example/123", "test"));
		String tooltip = item.getTicketBadge().tooltip();
		probe.planTree.refreshItems(List.of(item));

		assertThat(probe.badgeHits(0, tooltip)).isNotEmpty();

		item.setTicket(null);
		probe.planTree.refreshItems(List.of(item));

		assertThat(probe.badgeHits(0, tooltip)).isEmpty();
	}

	@Test
	void renameTargetIsTheSingleSelectedTopLevelItem(Project project) {

		UpgradePlanItem alpha = TestPlannedUpgrade.item("org.example:alpha:1.0.0", "1.1.0");
		UpgradePlanItem bravo = TestPlannedUpgrade.item("org.example:bravo:1.0.0", "1.1.0");
		PlanTreeProbe probe = new PlanTreeProbe(project, alpha, bravo);

		assertThat(probe.planTree.getRenameTarget()).isNull();

		probe.tree.setSelectionRow(0);
		assertThat(probe.planTree.getRenameTarget()).isEqualTo(alpha);

		probe.tree.addSelectionRow(1);
		assertThat(probe.planTree.getRenameTarget()).isNull();
	}

	@Test
	void renameTargetIgnoresGroupMemberRows(Project project) {

		TestPlannedUpgrade group = new TestPlannedUpgrade(List.of(
				TestCandidates.candidate("org.springframework:spring-core:6.0.0",
						it -> it.releases("6.1.0").versionProperty("spring.version")),
				TestCandidates.candidate("org.springframework:spring-context:6.0.0",
						it -> it.releases("6.1.0").versionProperty("spring.version"))),
				"spring.version");
		UpgradePlanItem item = TestPlannedUpgrade.create(project, ArtifactVersion.of("6.1.0"), group).getFirst();
		PlanTreeProbe probe = new PlanTreeProbe(project, item);

		probe.tree.expandRow(0);
		probe.tree.setSelectionRow(1);
		assertThat(probe.planTree.getRenameTarget()).isNull();

		probe.tree.setSelectionRow(0);
		assertThat(probe.planTree.getRenameTarget()).isEqualTo(item);
	}

	/**
	 * Probe around an {@link UpgradePlanTree} showing the given items: stamps row
	 * renderers the way the tree does and reads back the badge geometry a user
	 * would see.
	 */
	private static class PlanTreeProbe {

		final UpgradePlanTree planTree;

		final Tree tree;

		PlanTreeProbe(Project project, UpgradePlanItem... items) {

			this.planTree = new UpgradePlanTree(() -> {
			}, () -> {
			}, item -> {
			}, UpgradePlanService.getInstance(project), false, false);
			this.planTree.setItems(List.of(items));
			this.tree = (Tree) planTree.focusTarget();
			this.tree.setSize(600, 300);
		}

		List<String> displayedNames() {

			DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
			List<String> names = new ArrayList<>();
			root.children().asIterator().forEachRemaining(node -> names
					.add(((UpgradePlanItem) ((DefaultMutableTreeNode) node).getUserObject()).getDisplayName()));
			return names;
		}

		/**
		 * Return the widest badge width the given row paints. Painting into an image
		 * exercises the renderer's paint-time layout; the image content is discarded.
		 */
		int paintedBadgeWidth(int row) {

			JComponent renderer = stampRow(row);
			BufferedImage image = new BufferedImage(tree.getWidth(), 64, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			renderer.paint(graphics);
			graphics.dispose();

			return UIUtil.findComponentsOfType(renderer, UpgradePlanTree.BadgeComponent.class).stream()
					.mapToInt(Component::getWidth).max().orElse(0);
		}

		/**
		 * Return the width the given row reserves for its badge gutter.
		 */
		int paintedGutterWidth(int row) {

			JComponent renderer = stampRow(row);
			JComponent badge = UIUtil.findComponentsOfType(renderer, UpgradePlanTree.BadgeComponent.class).getFirst();
			return badge.getParent().getPreferredSize().width;
		}

		/**
		 * Return the x coordinates within the given row where the tree reports the
		 * given badge tooltip.
		 */
		List<Integer> badgeHits(int row, String tooltip) {

			Rectangle bounds = tree.getRowBounds(row);
			List<Integer> hits = new ArrayList<>();
			for (int x = 0; x < tree.getWidth(); x++) {
				MouseEvent event = new MouseEvent(tree, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x,
						bounds.y + bounds.height / 2, 0, false);
				if (tooltip.equals(tree.getToolTipText(event))) {
					hits.add(x);
				}
			}
			return hits;
		}

		/**
		 * Return the width the badge sizes itself to, independently of any row.
		 */
		static int badgeWidth(Badge badge) {

			UpgradePlanTree.BadgeComponent component = new UpgradePlanTree.BadgeComponent();
			component.setBadge(badge);
			return component.getPreferredSize().width;
		}

		private JComponent stampRow(int row) {

			Object node = tree.getPathForRow(row).getLastPathComponent();
			JComponent renderer = (JComponent) tree.getCellRenderer()
					.getTreeCellRendererComponent(tree, node, false, false, false, row, false);
			renderer.setBounds(0, 0, tree.getWidth(), tree.getRowBounds(row).height);
			renderer.doLayout();
			return renderer;
		}

	}

}
