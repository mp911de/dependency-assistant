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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;

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
import biz.paluch.dap.upgrade.UpgradeDecision;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
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

	@Test
	void everyRowPaintsItsOwnBadgeWidth(Project project) {

		ApplicationManager.getApplication().invokeAndWait(() -> {
			UpgradePlanItem patch = item("patch", "1.0.1");
			UpgradePlanItem major = item("major", "2.0.0");
			UpgradePlanTree planTree = new UpgradePlanTree(() -> {
			}, () -> {
			}, item -> {
			},
					UpgradePlanService.getInstance(project), false, false);
			planTree.setItems(List.of(patch, major));

			Tree tree = (Tree) planTree.focusTarget();
			tree.setSize(600, 300);

			// the tree stamps one renderer per row and reuses it at one size, so a row
			// that does not lay itself out paints the previous row's badge, or none
			assertThat(paintedBadgeWidth(tree, 0)).isEqualTo(badgeWidth(patch.getAttentionBadge()));
			assertThat(paintedBadgeWidth(tree, 1)).isEqualTo(badgeWidth(major.getAttentionBadge()));
		});
	}

	@Test
	void badgesHaveDistinctHitZones(Project project) {

		ApplicationManager.getApplication().invokeAndWait(() -> {
			UpgradePlanItem item = item("major", "2.0.0");
			item.setTicket(ticket());
			UpgradePlanTree planTree = new UpgradePlanTree(() -> {
			}, () -> {
			}, selected -> {
			},
					UpgradePlanService.getInstance(project), false, false);
			planTree.setItems(List.of(item));

			Tree tree = (Tree) planTree.focusTarget();
			tree.setSize(600, 300);
			List<Integer> ticketHits = badgeHits(tree, 0, item.getTicketBadge().tooltip());
			List<Integer> attentionHits = badgeHits(tree, 0, item.getAttentionBadge().tooltip());

			assertThat(ticketHits).isNotEmpty();
			assertThat(attentionHits).isNotEmpty();
			assertThat(ticketHits.getLast()).isLessThan(attentionHits.getFirst());
			assertThat(attentionHits.getFirst() - ticketHits.getLast() - 1).isEqualTo(JBUI.scale(6));
		});
	}

	@Test
	void gutterWidthUsesWidestTicketBadge(Project project) {

		ApplicationManager.getApplication().invokeAndWait(() -> {
			UpgradePlanItem shortTicket = item("short", "1.0.1");
			shortTicket.setTicket(ticket("#1"));
			UpgradePlanTree compactPlanTree = new UpgradePlanTree(() -> {
			}, () -> {
			}, selected -> {
			},
					UpgradePlanService.getInstance(project), false, false);
			compactPlanTree.setItems(List.of(shortTicket));

			UpgradePlanItem longTicket = item("long", "1.0.1");
			longTicket.setTicket(ticket("#123456789"));
			UpgradePlanTree mixedPlanTree = new UpgradePlanTree(() -> {
			}, () -> {
			}, selected -> {
			},
					UpgradePlanService.getInstance(project), false, false);
			mixedPlanTree.setItems(List.of(shortTicket, longTicket));

			Tree compactTree = (Tree) compactPlanTree.focusTarget();
			compactTree.setSize(600, 300);
			Tree mixedTree = (Tree) mixedPlanTree.focusTarget();
			mixedTree.setSize(600, 300);
			int expectedGrowth = badgeWidth(longTicket.getTicketBadge()) - badgeWidth(shortTicket.getTicketBadge());
			int mixedWidth = paintedGutterWidth(mixedTree, 0);

			assertThat(mixedWidth - paintedGutterWidth(compactTree, 0)).isEqualTo(expectedGrowth);
			assertThat(paintedGutterWidth(mixedTree, 1)).isEqualTo(mixedWidth);
		});
	}

	@Test
	void refreshingItemRefreshesTicketHitZone(Project project) {

		ApplicationManager.getApplication().invokeAndWait(() -> {
			UpgradePlanItem item = item("patch", "1.0.1");
			UpgradePlanTree planTree = new UpgradePlanTree(() -> {
			}, () -> {
			}, selected -> {
			},
					UpgradePlanService.getInstance(project), false, false);
			planTree.setItems(List.of(item));

			Tree tree = (Tree) planTree.focusTarget();
			tree.setSize(600, 300);
			item.setTicket(ticket());
			String tooltip = item.getTicketBadge().tooltip();
			planTree.refreshItems(List.of(item));

			assertThat(badgeHits(tree, 0, tooltip)).isNotEmpty();

			item.setTicket(null);
			planTree.refreshItems(List.of(item));

			assertThat(badgeHits(tree, 0, tooltip)).isEmpty();
		});
	}

	/**
	 * Return the badge width the given row paints, stamping and painting the shared
	 * renderer the way the tree does.
	 */
	private static int paintedBadgeWidth(Tree tree, int row) {

		Object node = tree.getPathForRow(row).getLastPathComponent();
		JComponent renderer = (JComponent) tree.getCellRenderer()
				.getTreeCellRendererComponent(tree, node, false, false, false, row, false);
		renderer.setBounds(0, 0, tree.getWidth(), tree.getRowBounds(row).height);
		// the tree lays the row out once and then reuses it; only the row itself can
		// keep its badges current
		renderer.doLayout();

		BufferedImage image = new BufferedImage(tree.getWidth(), 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		renderer.paint(g);
		g.dispose();

		return UIUtil.findComponentsOfType(renderer, UpgradePlanTree.BadgeComponent.class).stream()
				.mapToInt(Component::getWidth).max().orElse(0);
	}

	/**
	 * Return the width the badge sizes itself to, independently of any row.
	 */
	private static int badgeWidth(Badge badge) {

		UpgradePlanTree.BadgeComponent component = new UpgradePlanTree.BadgeComponent();
		component.setBadge(badge);
		return component.getPreferredSize().width;
	}

	private static int paintedGutterWidth(Tree tree, int row) {

		Object node = tree.getPathForRow(row).getLastPathComponent();
		JComponent renderer = (JComponent) tree.getCellRenderer()
				.getTreeCellRendererComponent(tree, node, false, false, false, row, false);
		renderer.setBounds(0, 0, tree.getWidth(), tree.getRowBounds(row).height);
		renderer.doLayout();

		JComponent badge = UIUtil.findComponentsOfType(renderer, UpgradePlanTree.BadgeComponent.class).getFirst();
		return badge.getParent().getPreferredSize().width;
	}

	private static List<Integer> badgeHits(Tree tree, int row, String tooltip) {

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

	private static UpgradeTicket ticket() {
		return ticket("#123");
	}

	private static UpgradeTicket ticket(String displayReference) {
		return new UpgradeTicket("123", displayReference, "https://tickets.example/123", "test");
	}

	private static UpgradePlanItem item(String name, String target) {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		ArtifactVersion targetVersion = ArtifactVersion.of(target);
		Dependency dependency = new Dependency(ArtifactId.of("org.example", name), current);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.declared(current.toString()));
		VulnerabilityRepository vulnerabilities = VulnerabilityRepository.of(
				Map.of(current, Vulnerabilities.clean(), targetVersion, Vulnerabilities.clean()));
		TestUpgradePlanCapture candidate = new TestUpgradePlanCapture(
				UpgradeDecision.create(dependency, Releases.just(Release.of(targetVersion)), vulnerabilities));
		UpgradePlanState.Item stored = UpgradePlanState.Item.from(candidate, targetVersion);
		return Objects.requireNonNull(
				new UpgradePlanLoader(List.of(TestInterfaceAssistant.INSTANCE), null).create(stored));
	}

}
