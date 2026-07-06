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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
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

	private static final int TICKET_SLOT = 70;

	private static final int ATTENTION_SLOT = 80;

	private static final int BADGE_GAP = 6;

	private static final int RIGHT_MARGIN = 14;

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

		// the platform Tree selects the node under a right-click before the popup
		// shows, so no manual selection handling is needed here
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

		// StatusText owns the cursor while the tree is empty. With rows present, this
		// listener owns the ticket-badge cursor and resets it outside the badge.
		tree.addMouseMotionListener(new MouseAdapter() {

			@Override
			public void mouseMoved(MouseEvent event) {
				if (shownItems.isEmpty()) {
					return;
				}
				tree.setCursor(isTicketBadgeHit(event) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
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
	JComponent focusTarget() {
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
	 * selection while every renderer, hit test, and action sees the current
	 * immutable item instance.
	 */
	void refreshItems(List<UpgradePlanItem> planItems) {

		this.planItems = List.copyOf(planItems);
		List<UpgradePlanItem> shownItems = PlanItemOrdering.order(this.planItems, sortByAttention, sortAlphabetically);
		if (shownItems.size() != root.getChildCount()) {
			rebuild();
			return;
		}

		for (int i = 0; i < shownItems.size(); i++) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(i);
			if (!shownItems.get(i).equals(node.getUserObject())) {
				rebuild();
				return;
			}
		}

		this.shownItems = shownItems;
		for (int i = 0; i < shownItems.size(); i++) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(i);
			node.setUserObject(shownItems.get(i));
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

	boolean requestFocusInWindow() {
		return tree.requestFocusInWindow();
	}

	private void rebuild() {

		boolean emptyStateChanged = shownItems.isEmpty() != planItems.isEmpty();
		this.shownItems = PlanItemOrdering.order(planItems, sortByAttention, sortAlphabetically);
		if (emptyStateChanged) {
			tree.setCursor(Cursor.getDefaultCursor());
		}

		// preserve expansion and selection by item identity across the rebuild;
		// the panel overrides the selection afterwards when a reload demands it
		TreeState state = TreeState.createOn(tree, root);

		root.removeAllChildren();
		for (UpgradePlanItem item : shownItems) {

			DefaultMutableTreeNode node = new DefaultMutableTreeNode(item, !item.getMembers().isEmpty());
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

		if (event.getButton() != MouseEvent.BUTTON1 || !isTicketBadgeHit(event)) {
			return false;
		}
		UpgradePlanItem item = itemAtRow(rowAt(event));
		UpgradeTicket ticket = item != null ? item.getTicket() : null;
		if (ticket != null) {
			BrowserUtil.browse(ticket.getUrl());
			return true;
		}
		return false;
	}

	/**
	 * Return whether the mouse is over the ticket badge of a row that carries a
	 * linked ticket. Shared by the click handler and the hand-cursor feedback.
	 */
	private boolean isTicketBadgeHit(MouseEvent event) {

		if (!service.hasTicketSystem()) {
			return false;
		}

		int row = rowAt(event);
		UpgradePlanItem item = itemAtRow(row);
		if (item == null || !item.hasTicket()) {
			return false;
		}
		return ticketSlot(tree.getRowBounds(row)).contains(event.getPoint());
	}

	private boolean previewAt(MouseEvent event) {

		UpgradePlanItem item = itemAtRow(rowAt(event));
		if (item != null) {
			itemDoubleClickAction.accept(item);
			return true;
		}
		return false;
	}

	/**
	 * Resolve the row under the pointer by vertical position: the badges paint in
	 * the renderer's stretched right gutter, which lies outside the node's path
	 * bounds, so {@code getRowForLocation} (which requires the pointer inside those
	 * bounds) misses them. The closest row by y with an in-bounds check does not.
	 */
	private int rowAt(MouseEvent event) {

		int row = tree.getClosestRowForLocation(event.getX(), event.getY());
		if (row < 0) {
			return -1;
		}

		Rectangle bounds = tree.getRowBounds(row);
		return bounds != null && event.getY() >= bounds.y && event.getY() < bounds.y + bounds.height ? row : -1;
	}

	private Rectangle ticketSlot(@Nullable Rectangle rowBounds) {
		return badgeSlot(rowBounds, TICKET_SLOT + BADGE_GAP + ATTENTION_SLOT, TICKET_SLOT);
	}

	private Rectangle attentionSlot(@Nullable Rectangle rowBounds) {
		return badgeSlot(rowBounds, ATTENTION_SLOT, ATTENTION_SLOT);
	}

	/**
	 * Compute a badge slot rectangle anchored to the tree's right edge (past the
	 * right margin) for the given row, mirroring the renderer's gutter layout.
	 */
	private Rectangle badgeSlot(@Nullable Rectangle rowBounds, int offsetFromMargin, int slotWidth) {

		if (rowBounds == null) {
			return new Rectangle();
		}

		int right = tree.getWidth() - JBUI.scale(RIGHT_MARGIN) - JBUI.scale(offsetFromMargin - slotWidth);
		int left = right - JBUI.scale(slotWidth);
		return new Rectangle(left, rowBounds.y, JBUI.scale(slotWidth), rowBounds.height);
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
			UpgradePlanItem item = itemAtRow(row);
			if (item == null) {
				return null;
			}

			Rectangle bounds = getRowBounds(row);
			if (item.getTicketKey() != null && service.hasTicketSystem()
					&& ticketSlot(bounds).contains(event.getPoint())) {
				String displayReference = service.getTicketSystem()
						.getDisplayReference(item.getTicketKey());
				return MessageBundle.message("plan.badge.ticket.tooltip", displayReference);
			}
			if (attentionSlot(bounds).contains(event.getPoint())) {
				return item.getAttentionBadge().tooltip();
			}
			return null;
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

		private final BadgeGutter gutter = new BadgeGutter();

		PlanCellRenderer() {

			panel.setOpaque(false);
			text.setOpaque(false);
			panel.add(text, BorderLayout.CENTER);
			panel.add(gutter, BorderLayout.EAST);
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
				boolean leaf, int row, boolean hasFocus) {

			text.clear();
			text.setForeground(RenderingUtil.getForeground(tree, selected));

			Object node = ((DefaultMutableTreeNode) value).getUserObject();
			if (node instanceof UpgradePlanItem item) {

				text.setIcon(item.getIcon());
				text.append(item.getDisplayName(), item.isGroup()
						? new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, null)
						: SimpleTextAttributes.REGULAR_ATTRIBUTES);
				text.append("   " + item.getFromVersion() + " -> " + item.getToVersion(),
						SimpleTextAttributes.GRAYED_ATTRIBUTES);

				gutter.setBadges(ticketBadge(item), item.getAttentionBadge());
			} else if (node instanceof UpgradeCandidate member) {

				text.setIcon(AllIcons.Nodes.PpLib);
				text.append(member.getRowLabel(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
				text.append("   " + member.getCurrentVersion(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
				gutter.setBadges(null, null);
			}

			SpeedSearchUtil.applySpeedSearchHighlighting(tree, text, true, selected);
			return panel;
		}

		private @Nullable Badge ticketBadge(UpgradePlanItem item) {

			Badge ticket = item.getTicketBadge();
			return service.hasTicketSystem() ? ticket : null;
		}

	}

	/**
	 * Fixed-width right gutter painting the ticket and attention badge pills in
	 * aligned slots.
	 */
	private static class BadgeGutter extends JComponent {

		private static final int MIN_PILL_WIDTH = 50;

		private @Nullable Badge ticket;

		private @Nullable Badge attention;

		BadgeGutter() {

			setOpaque(false);
			int width = JBUI.scale(TICKET_SLOT + BADGE_GAP + ATTENTION_SLOT + RIGHT_MARGIN);
			setPreferredSize(new Dimension(width, 0));
		}

		void setBadges(@Nullable Badge ticket, @Nullable Badge attention) {
			this.ticket = ticket;
			this.attention = attention;
		}

		@Override
		protected void paintComponent(Graphics g) {

			GraphicsUtil.setupAAPainting(g);
			paintBadge(g, ticket, 0, JBUI.scale(TICKET_SLOT));
			paintBadge(g, attention, JBUI.scale(TICKET_SLOT + BADGE_GAP), JBUI.scale(ATTENTION_SLOT));
		}

		private void paintBadge(Graphics g, @Nullable Badge badge, int slotX, int slotWidth) {

			if (badge == null) {
				return;
			}

			Font font = JBFont.small();
			FontMetrics metrics = g.getFontMetrics(font);
			int hPadding = JBUI.scale(12);
			int vPadding = JBUI.scale(1);

			String label = badge.text();
			int textWidth = metrics.stringWidth(label);
			// a minimum pill width so short badges (single-digit issue numbers) keep a
			// consistent, unfussy size instead of hugging the digits
			int pillWidth = Math.min(Math.max(textWidth + 2 * hPadding, JBUI.scale(MIN_PILL_WIDTH)), slotWidth);
			int pillHeight = metrics.getHeight() + 2 * vPadding;
			int x = slotX + (slotWidth - pillWidth) / 2;
			int y = (getHeight() - pillHeight) / 2;

			g.setColor(badge.colorType().background());
			g.fillRoundRect(x, y, pillWidth, pillHeight, pillHeight, pillHeight);

			g.setColor(badge.colorType().foreground());
			g.setFont(font);
			int textX = x + (pillWidth - textWidth) / 2;
			int textY = y + (pillHeight - metrics.getHeight()) / 2 + metrics.getAscent();
			g.drawString(label, textX, textY);
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
