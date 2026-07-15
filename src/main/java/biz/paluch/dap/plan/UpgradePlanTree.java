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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import biz.paluch.dap.assistant.check.UpgradeCandidate;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.tree.TreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Upgrade plan tree showing upgrade plan items with groups and badges for
 * linked tickets and the upgrade attention level.
 *
 * @author Mark Paluch
 */
class UpgradePlanTree {

	private final UpgradePlanService service;

	private final Runnable emptyAction;

	private final Runnable pasteAction;

	private final Consumer<UpgradePlanItem> itemDoubleClickAction;

	private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();

	private final DefaultTreeModel model = new DefaultTreeModel(root);

	private final PlanTree tree = new PlanTree(model);

	private final JScrollPane component;

	private boolean sortByAttention;

	private boolean sortAlphabetically;

	private boolean pasteAvailable;

	private List<UpgradePlanItem> planItems = List.of();

	private List<UpgradePlanItem> shownItems = List.of();

	UpgradePlanTree(Runnable emptyAction, Runnable pasteAction, Consumer<UpgradePlanItem> itemDoubleClickAction,
			UpgradePlanService service, boolean sortByAttention, boolean sortAlphabetically) {

		this.service = service;
		this.emptyAction = emptyAction;
		this.pasteAction = pasteAction;
		this.itemDoubleClickAction = itemDoubleClickAction;
		this.sortByAttention = sortByAttention;
		this.sortAlphabetically = sortAlphabetically;
		this.component = ScrollPaneFactory.createScrollPane(tree, SideBorder.NONE);

		configure();
		refreshEmptyText();
	}

	private void configure() {

		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
		tree.setCellRenderer(new PlanCellRenderer());

		TreeSpeedSearch.installOn(tree, false, UpgradePlanTree::nodeText);

		PopupHandler.installPopupMenu(tree, "DependencyAssistant.UpgradePlan.Popup",
				"DependencyAssistant.UpgradePlanPopup");

		new ClickListener() {

			@Override
			public boolean onClick(MouseEvent event, int clickCount) {
				return openTicketAt(event);
			}

		}.installOn(tree);

		new DoubleClickListener() {

			@Override
			protected boolean onDoubleClick(MouseEvent event) {
				return previewAt(event);
			}

		}.installOn(tree);

		tree.addMouseMotionListener(new MouseAdapter() {

			@Override
			public void mouseMoved(MouseEvent event) {
				if (shownItems.isEmpty()) {
					return;
				}
				tree.setCursor(ticketAt(event) != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
						: Cursor.getDefaultCursor());
			}

		});
	}

	JComponent component() {
		return component;
	}

	/**
	 * Return the component that takes focus for the tree, anchoring the plan action
	 * context in the tool window even while the plan is empty.
	 */
	JTree focusTarget() {
		return tree;
	}

	void setPasteAvailable(boolean pasteAvailable) {

		if (this.pasteAvailable != pasteAvailable) {
			this.pasteAvailable = pasteAvailable;
			refreshEmptyText();
		}
	}

	void setItems(List<UpgradePlanItem> planItems) {
		this.planItems = List.copyOf(planItems);
		rebuild();
	}

	boolean isSortByAttention() {
		return sortByAttention;
	}

	void setSortByAttention(boolean sortByAttention) {

		if (this.sortByAttention != sortByAttention) {
			this.sortByAttention = sortByAttention;
			rebuild();
		}
	}

	boolean isSortAlphabetically() {
		return sortAlphabetically;
	}

	void setSortAlphabetically(boolean sortAlphabetically) {

		if (this.sortAlphabetically != sortAlphabetically) {
			this.sortAlphabetically = sortAlphabetically;
			rebuild();
		}
	}

	/**
	 * Replace the item values held by the existing tree nodes after an item-scoped
	 * change such as linking a ticket. Keeping the nodes preserves expansion and
	 * selection while every renderer, hit test, and action sees the current item
	 * state.
	 */
	void refreshItems(List<UpgradePlanItem> planItems) {

		this.planItems = List.copyOf(planItems);
		List<UpgradePlanItem> shownItems = PlanItemOrdering.order(this.planItems, sortByAttention, sortAlphabetically);
		if (shownItems.size() != root.getChildCount()) {
			rebuild();
			return;
		}

		for (int i = 0; i < shownItems.size(); i++) {
			PlanTreeNode node = (PlanTreeNode) root.getChildAt(i);
			if (!shownItems.get(i).equals(node.getUserObject())) {
				rebuild();
				return;
			}
		}

		this.shownItems = shownItems;
		BadgeColumns badgeColumns = new BadgeColumns(shownItems);
		for (int i = 0; i < shownItems.size(); i++) {
			PlanTreeNode node = (PlanTreeNode) root.getChildAt(i);
			node.setItem(shownItems.get(i), badgeColumns);
			model.nodeChanged(node);
		}
	}

	PlanSelection getSelection() {

		Set<UpgradePlanItem> selectionSet = getSelectionSet();

		if (selectionSet.isEmpty()) {
			return PlanSelection.empty();
		}

		List<UpgradePlanItem> selection = new ArrayList<>();
		for (UpgradePlanItem item : planItems) {
			if (selectionSet.contains(item)) {
				selection.add(item);
			}
		}

		return PlanSelection.of(selection);
	}

	Set<UpgradePlanItem> getSelectionSet() {

		TreePath[] paths = tree.getSelectionPaths();
		if (paths == null) {
			return Set.of();
		}

		Set<UpgradePlanItem> selectionSet = new LinkedHashSet<>();
		for (TreePath path : paths) {
			UpgradePlanItem item = itemOf((DefaultMutableTreeNode) path.getLastPathComponent());
			if (item == null) {
				continue;
			}
			selectionSet.add(item);
		}
		return selectionSet;
	}

	@Nullable
	UpgradePlanItem selectionAfterRemoving(Set<UpgradePlanItem> items) {
		return PlanItemOrdering.selectionAfterRemoval(this.shownItems, items);
	}

	void selectItem(@Nullable UpgradePlanItem item) {

		if (item == null) {
			tree.clearSelection();
			return;
		}

		DefaultMutableTreeNode node = findNode(item);
		if (node != null) {
			TreeUtil.selectPath(tree, new TreePath(node.getPath()));
		}
	}

	void requestFocusInWindow() {
		tree.requestFocusInWindow();
	}

	private void rebuild() {

		boolean emptyStateChanged = shownItems.isEmpty() != planItems.isEmpty();
		this.shownItems = PlanItemOrdering.order(planItems, sortByAttention, sortAlphabetically);
		if (emptyStateChanged) {
			tree.setCursor(Cursor.getDefaultCursor());
		}

		TreeState state = TreeState.createOn(tree, root);

		root.removeAllChildren();
		BadgeColumns badgeColumns = new BadgeColumns(shownItems);
		for (UpgradePlanItem item : shownItems) {

			PlanTreeNode node = new PlanTreeNode(item, badgeColumns);
			for (UpgradeCandidate member : item.getMembers()) {
				node.add(new DefaultMutableTreeNode(member, false));
			}
			root.add(node);
		}
		model.reload();
		state.applyTo(tree, root);
	}

	private void refreshEmptyText() {

		StatusText emptyText = tree.getEmptyText();
		emptyText.setText(MessageBundle.message("plan.empty.text"));
		appendLink(emptyText, MessageBundle.message("plan.empty.link"), emptyAction,
				KeymapUtil.getFirstKeyboardShortcutText(CommonShortcuts.getNew()));
		if (pasteAvailable) {
			appendLink(emptyText, MessageBundle.message("plan.empty.paste"), pasteAction,
					KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_PASTE));
		}
	}

	private static void appendLink(StatusText emptyText, String text, Runnable action, String shortcut) {

		emptyText.appendLine(text, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, event -> action.run());
		if (!shortcut.isEmpty()) {
			emptyText.appendText("  " + shortcut, SimpleTextAttributes.GRAYED_ATTRIBUTES);
		}
	}

	private boolean openTicketAt(MouseEvent event) {

		if (event.getButton() != MouseEvent.BUTTON1) {
			return false;
		}

		UpgradeTicket ticket = ticketAt(event);
		if (ticket == null) {
			return false;
		}

		BrowserUtil.browse(ticket.getUrl());
		return true;
	}

	private @Nullable UpgradeTicket ticketAt(MouseEvent event) {

		int row = rowAt(event);
		PlanTreeNode node = planNodeAtRow(row);
		Rectangle rowBounds = node != null ? tree.getRowBounds(row) : null;
		if (rowBounds == null) {
			return null;
		}

		UpgradePlanItem item = node.getItem();
		Badge ticket = ticketBadge(item);
		return ticket != null && badgeAt(node, rowBounds, event.getPoint()) == ticket
				? item.getTicket()
				: null;
	}

	private @Nullable Badge badgeAt(PlanTreeNode node, Rectangle rowBounds, Point point) {

		BadgeGutter gutter = node.getBadgeGutter();
		JViewport viewport = ComponentUtil.getViewport(tree);
		int visibleRight = viewport != null ? viewport.getWidth() - tree.getX() : tree.getWidth();
		int originX = Math.max(visibleRight, rowBounds.x + rowBounds.width) - gutter.getPreferredSize().width;
		Point local = new Point(point.x - originX, point.y - rowBounds.y);
		return gutter.badgeAt(rowBounds.height, local);
	}

	private @Nullable Badge ticketBadge(UpgradePlanItem item) {
		return service.hasTicketSystem() ? item.getTicketBadge() : null;
	}

	private boolean previewAt(MouseEvent event) {

		UpgradePlanItem item = itemAtRow(rowAt(event));
		if (item != null) {
			itemDoubleClickAction.accept(item);
			return true;
		}
		return false;
	}

	private int rowAt(MouseEvent event) {

		int row = tree.getClosestRowForLocation(event.getX(), event.getY());
		if (row < 0) {
			return -1;
		}

		Rectangle bounds = tree.getRowBounds(row);
		return bounds != null && event.getY() >= bounds.y && event.getY() < bounds.y + bounds.height ? row : -1;
	}

	private @Nullable PlanTreeNode planNodeAtRow(int row) {

		TreePath path = row >= 0 ? tree.getPathForRow(row) : null;
		return path != null && path.getLastPathComponent() instanceof PlanTreeNode node ? node : null;
	}

	private @Nullable UpgradePlanItem itemAtRow(int row) {

		TreePath path = row >= 0 ? tree.getPathForRow(row) : null;
		return path != null ? itemOf((DefaultMutableTreeNode) path.getLastPathComponent()) : null;
	}

	/**
	 * Resolve the plan item a node belongs to: the node itself when it is a plan
	 * item, or its parent group when it is a member row.
	 */
	private static @Nullable UpgradePlanItem itemOf(DefaultMutableTreeNode node) {

		if (node.getUserObject() instanceof UpgradePlanItem item) {
			return item;
		}
		if (node.getParent() instanceof DefaultMutableTreeNode parent
				&& parent.getUserObject() instanceof UpgradePlanItem item) {
			return item;
		}
		return null;
	}

	private @Nullable DefaultMutableTreeNode findNode(UpgradePlanItem item) {

		for (int i = 0; i < root.getChildCount(); i++) {

			DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(i);
			if (item.equals(node.getUserObject())) {
				return node;
			}
		}
		return null;
	}

	private static String nodeText(TreePath path) {

		Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (value instanceof UpgradePlanItem item) {
			return item.getDisplayName();
		}
		if (value instanceof UpgradeCandidate member) {
			return member.getRowLabel();
		}
		return "";
	}

	/**
	 * {@link Tree} with per-badge tooltips; the plan panel owns the action data
	 * context.
	 */
	private class PlanTree extends Tree {

		PlanTree(DefaultTreeModel model) {
			super(model);
		}

		@Override
		public @Nullable String getToolTipText(MouseEvent event) {

			int row = rowAt(event);
			PlanTreeNode node = planNodeAtRow(row);
			Rectangle rowBounds = node != null ? getRowBounds(row) : null;
			if (rowBounds == null) {
				return null;
			}

			Badge badge = badgeAt(node, rowBounds, event.getPoint());
			return badge != null ? badge.tooltip() : null;
		}

	}

	/**
	 * Row renderer stretched to the full row width by the tree UI: item content on
	 * the left, the badge gutter on the right. Non-opaque so the wide rounded
	 * selection paints behind it.
	 */
	private class PlanCellRenderer implements TreeCellRenderer {

		private final JPanel panel = new JPanel(new BorderLayout());

		private final SimpleColoredComponent text = new SimpleColoredComponent();

		PlanCellRenderer() {

			panel.setOpaque(false);
			text.setOpaque(false);
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
				boolean leaf, int row, boolean hasFocus) {

			text.clear();
			text.setForeground(RenderingUtil.getForeground(tree, selected));
			panel.removeAll();
			panel.add(text, BorderLayout.CENTER);

			if (value instanceof PlanTreeNode node) {

				UpgradePlanItem item = node.getItem();
				text.setIcon(item.getIcon());
				text.append(item.getDisplayName(), item.isGroup()
						? new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, null)
						: SimpleTextAttributes.REGULAR_ATTRIBUTES);
				text.append("   " + item.getFromVersion() + " -> " + item.getToVersion(),
						SimpleTextAttributes.GRAYED_ATTRIBUTES);

				panel.add(node.getBadgeGutter(), BorderLayout.EAST);
			} else if (((DefaultMutableTreeNode) value).getUserObject() instanceof UpgradeCandidate member) {

				text.setIcon(AllIcons.Nodes.Library);
				text.append(member.getRowLabel(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
				text.append("   " + member.getCurrentVersion(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
			}

			SpeedSearchUtil.applySpeedSearchHighlighting(tree, text, true, selected);
			return panel;
		}

	}

	/**
	 * Tree node for a plan item and its row-local badge presentation.
	 */
	private static class PlanTreeNode extends DefaultMutableTreeNode {

		private final BadgeGutter badgeGutter;

		PlanTreeNode(UpgradePlanItem item, BadgeColumns badgeColumns) {
			super(item, !item.getMembers().isEmpty());
			this.badgeGutter = new BadgeGutter(badgeColumns, item.getTicketBadge(), item.getAttentionBadge());
		}

		UpgradePlanItem getItem() {
			return (UpgradePlanItem) getUserObject();
		}

		BadgeGutter getBadgeGutter() {
			return badgeGutter;
		}

		void setItem(UpgradePlanItem item, BadgeColumns badgeColumns) {
			setUserObject(item);
			badgeGutter.setBadges(badgeColumns, item.getTicketBadge(), item.getAttentionBadge());
		}

	}

	/**
	 * Measured-width right gutter of a plan row: the ticket badge, the attention
	 * badge, and a trailing margin.
	 */
	private static class BadgeGutter extends JComponent {

		private BadgeColumns badgeColumns;

		private final BadgeComponent ticket = new BadgeComponent();

		private final BadgeComponent attention = new BadgeComponent();

		BadgeGutter(BadgeColumns badgeColumns, @Nullable Badge ticket, Badge attention) {

			setOpaque(false);
			add(this.ticket);
			add(this.attention);
			setBadges(badgeColumns, ticket, attention);
		}

		void setBadges(BadgeColumns badgeColumns, @Nullable Badge ticket, Badge attention) {

			this.badgeColumns = badgeColumns;
			this.ticket.setBadge(ticket);
			this.attention.setBadge(attention);
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(badgeColumns.width(), 0);
		}

		/**
		 * Return the badge drawn under the given gutter-local point, or {@literal null}
		 * where the row draws none.
		 */
		@Nullable
		Badge badgeAt(int rowHeight, Point point) {

			setSize(badgeColumns.width(), rowHeight);
			doLayout();
			if (ticket.getBounds().contains(point)) {
				return ticket.getBadge();
			}
			return attention.getBounds().contains(point) ? attention.getBadge() : null;
		}

		@Override
		protected void paintChildren(Graphics g) {

			doLayout();
			super.paintChildren(g);
		}

		@Override
		public void doLayout() {
			badgeColumns.layout(ticket, attention, getHeight());
		}

	}

	/**
	 * Measured badge columns shared by every displayed plan row. Ticket badges
	 * align towards the attention column and attention badges align towards the
	 * ticket column, keeping exactly one deliberate gap between their inner edges.
	 */
	private static class BadgeColumns {

		private static final int BADGE_GAP = 6;

		private static final int RIGHT_MARGIN = 14;

		private final int ticketWidth;

		private final int attentionWidth;

		private final int gap;

		BadgeColumns(List<UpgradePlanItem> items) {

			BadgeComponent measurer = new BadgeComponent();
			int ticketWidth = 0;
			int attentionWidth = 0;
			for (UpgradePlanItem item : items) {
				measurer.setBadge(item.getTicketBadge());
				ticketWidth = Math.max(ticketWidth, measurer.getPreferredSize().width);
				measurer.setBadge(item.getAttentionBadge());
				attentionWidth = Math.max(attentionWidth, measurer.getPreferredSize().width);
			}
			this.ticketWidth = ticketWidth;
			this.attentionWidth = attentionWidth;
			this.gap = ticketWidth > 0 && attentionWidth > 0 ? JBUI.scale(BADGE_GAP) : 0;
		}

		int width() {
			return ticketWidth + gap + attentionWidth + JBUI.scale(RIGHT_MARGIN);
		}

		void layout(BadgeComponent ticket, BadgeComponent attention, int height) {

			Dimension ticketSize = ticket.getPreferredSize();
			ticket.setBounds(ticketWidth - ticketSize.width, (height - ticketSize.height) / 2,
					ticketSize.width, ticketSize.height);

			Dimension attentionSize = attention.getPreferredSize();
			attention.setBounds(ticketWidth + gap, (height - attentionSize.height) / 2,
					attentionSize.width, attentionSize.height);
		}

	}

	/**
	 * One badge, hugging its label down to {@link #MIN_WIDTH} so short labels keep
	 * a consistent size. Without a badge it measures empty and paints nothing.
	 */
	static class BadgeComponent extends JComponent {

		private static final int MIN_WIDTH = 50;

		private static final int H_PADDING = 12;

		private static final int V_PADDING = 1;

		private @Nullable Badge badge;

		private @Nullable Dimension dimension;

		BadgeComponent() {

			setOpaque(false);
			setFont(JBFont.small());
		}

		void setBadge(@Nullable Badge badge) {
			this.badge = badge;
			this.dimension = null;
		}

		@Nullable
		Badge getBadge() {
			return badge;
		}

		@Override
		public Dimension getPreferredSize() {

			if (badge == null) {
				return new Dimension();
			}

			if (dimension == null) {
				FontMetrics metrics = getFontMetrics(getFont());
				dimension = new Dimension(
						Math.max(metrics.stringWidth(badge.text()) + 2 * JBUI.scale(H_PADDING), JBUI.scale(MIN_WIDTH)),
						metrics.getHeight() + 2 * JBUI.scale(V_PADDING));
			}
			return dimension;
		}

		@Override
		protected void paintComponent(Graphics g) {

			if (badge == null) {
				return;
			}

			GraphicsUtil.setupAAPainting(g);
			int height = getHeight();
			g.setColor(badge.colorType().background());
			g.fillRoundRect(0, 0, getWidth(), height, height, height);

			FontMetrics metrics = getFontMetrics(getFont());
			g.setColor(badge.colorType().foreground());
			g.setFont(getFont());
			g.drawString(badge.text(), (getWidth() - metrics.stringWidth(badge.text())) / 2,
					(height - metrics.getHeight()) / 2 + metrics.getAscent());
		}

	}

	/**
	 * View-only ordering and positional selection of Upgrade Plan items.
	 *
	 * @author Mark Paluch
	 */
	static class PlanItemOrdering {

		private static final Comparator<UpgradePlanItem> BY_ATTENTION = Comparator
				.comparing(UpgradePlanItem::getAttentionLevel);

		private static final Comparator<UpgradePlanItem> ALPHABETICALLY = Comparator.comparing(
				UpgradePlanItem::getDisplayName,
				String.CASE_INSENSITIVE_ORDER);

		private PlanItemOrdering() {
		}

		static List<UpgradePlanItem> order(List<UpgradePlanItem> items, boolean byAttention, boolean alphabetically) {

			if (byAttention && alphabetically) {
				return items.stream().sorted(BY_ATTENTION.thenComparing(ALPHABETICALLY)).toList();
			}
			if (byAttention) {
				return items.stream().sorted(BY_ATTENTION).toList();
			}
			if (alphabetically) {
				return items.stream().sorted(ALPHABETICALLY).toList();
			}
			return List.copyOf(items);
		}

		static @Nullable UpgradePlanItem selectionAfterRemoval(List<UpgradePlanItem> shownItems,
				Set<UpgradePlanItem> removedItems) {

			List<UpgradePlanItem> remaining = new ArrayList<>(shownItems.size());
			int firstRemovedIndex = -1;
			for (int i = 0; i < shownItems.size(); i++) {
				UpgradePlanItem item = shownItems.get(i);
				if (!removedItems.contains(item)) {
					remaining.add(item);
				} else if (firstRemovedIndex == -1) {
					firstRemovedIndex = i;
				}
			}

			if (firstRemovedIndex == -1 || remaining.isEmpty()) {
				return null;
			}
			return remaining.get(Math.min(firstRemovedIndex, remaining.size() - 1));
		}

	}

}
