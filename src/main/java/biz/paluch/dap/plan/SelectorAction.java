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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import biz.paluch.dap.util.MessageBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ClickListener;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;
import org.jspecify.annotations.Nullable;

/**
 * Dropdown selector for the tool window tab row, rendered by a
 * {@link SelectorButton} in the toolbar-combo-widget style: icon, selected
 * value, chevron. Opens a filterable action popup; the full "Milestone: x"
 * wording moves to the tooltip.
 */
class SelectorAction<T> extends DumbAwareAction implements CustomComponentAction {

	private static final int MAX_POPUP_ROWS = 20;

	private final UpgradePlanService service;

	private final Component parent;

	private final String tooltipKey;

	private final Icon icon;

	private final SelectorModel<T> model;

	SelectorAction(UpgradePlanService service, Component parent, String tooltipKey, Icon icon, SelectorModel<T> model) {
		this.service = service;
		this.parent = parent;
		this.tooltipKey = tooltipKey;
		this.icon = icon;
		this.model = model;
	}

	private void updateButton(SelectorButton button, Presentation presentation) {
		button.setText(shownText());
		button.setVisible(presentation.isVisible());
		button.setEnabled(service.isRefreshingListsEnabled() && presentation.isEnabled());
		button.setClearVisible(model.getSelected() != null);
		button.setTooltips(tooltipText(), MessageBundle.message(tooltipKey + ".clear"));
	}

	private String shownText() {
		T selection = model.getSelected();
		return selection != null ? model.getText(selection) : MessageBundle.message(tooltipKey + ".placeholder");
	}

	private String tooltipText() {

		T selection = model.getSelected();
		if (selection == null) {
			return MessageBundle.message(tooltipKey + ".empty");
		}

		return tooltipText(selection);
	}

	private String tooltipText(T value) {
		String tooltip = model.getToolTip(value);
		return tooltip != null && !tooltip.isBlank() ? tooltip : model.getText(value);
	}

	@Override
	public JComponent createCustomComponent(Presentation presentation, String place) {

		class BoundSelectorButton extends SelectorButton implements ListDataListener {

			@Override
			public void addNotify() {
				model.addListDataListener(this);
				super.addNotify();
				updateButton(this, presentation);
			}

			@Override
			public void removeNotify() {
				model.removeListDataListener(this);
				super.removeNotify();
			}

			@Override
			public void intervalAdded(ListDataEvent e) {
				updateButton(this, presentation);
			}

			@Override
			public void intervalRemoved(ListDataEvent e) {
				updateButton(this, presentation);
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				updateButton(this, presentation);
			}

		}

		SelectorButton button = new BoundSelectorButton();
		button.setIcon(icon);
		updateButton(button, presentation);
		button.onPopup(() -> showPopup(button));
		button.onClear(() -> model.setSelectedItem(null));
		return button;
	}

	@Override
	public void updateCustomComponent(JComponent component, Presentation presentation) {
		if (component instanceof SelectorButton button) {
			updateButton(button, presentation);
		}
	}

	private void showPopup(SelectorButton button) {

		DefaultActionGroup values = new DefaultActionGroup();
		T previous = null;
		for (T value : model.getItems()) {

			if (previous != null && model.startsNewGroup(previous, value)) {
				values.addSeparator();
			}
			previous = value;

			String string = model.getText(value);

			DumbAwareAction option = new DumbAwareAction(() -> string) {

				@Override
				public void actionPerformed(AnActionEvent e) {
					model.setSelectedItem(value);
				}

			};
			Presentation templatePresentation = option.getTemplatePresentation();
			templatePresentation.setIcon(model.getIcon(value));
			templatePresentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, tooltipText(value));
			templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT,
					model.getSecondaryText(value));
			values.add(option);
		}

		JBPopup popup = JBPopupFactory.getInstance()
				.createActionGroupPopup(null, values,
						DataManager.getInstance().getDataContext(parent),
						JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true, null, MAX_POPUP_ROWS);

		button.setPopupShowing(true);
		popup.addListener(new JBPopupListener() {

			@Override
			public void onClosed(LightweightWindowEvent event) {
				button.setPopupShowing(false);
			}

		});
		popup.showUnderneathOf(button);
	}

	@Override
	public void actionPerformed(AnActionEvent e) {
	}

	@Override
	public void update(AnActionEvent e) {

		boolean visible = service.hasTicketSystem();
		String shown = shownText();
		e.getPresentation().setVisible(visible);
		e.getPresentation().setEnabled(visible && service.isRefreshingListsEnabled());
		e.getPresentation().setText(shown, false);
		e.getPresentation().setDescription(tooltipText());
	}

	@Override
	public ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.EDT;
	}

	/**
	 * Dropdown selector-
	 *
	 * @author Mark Paluch
	 */
	static class SelectorButton extends JComponent {

		private static final JBValue ARC = JBUI.CurrentTheme.MainToolbar.Dropdown.hoverArc();

		private static final Color HOVER_BACKGROUND = JBUI.CurrentTheme.ToolWindow.hoverBackground();

		private static final int ICON_TEXT_GAP = 6;

		private static final int BEFORE_CHEVRON_GAP = 2;

		private static final int CHEVRON_CLOSE_GAP = 1;

		private static final int LEFT_MARGIN = 4;

		private static final int RIGHT_MARGIN = 3;

		private static final int VERTICAL_MARGIN = 2;

		private static final int CONTENT_SHIFT = 0;

		private static final int ICON_SIZE = 14;

		private @Nullable Icon icon;

		private String text = "";

		private boolean clearVisible;

		private boolean popupShowing;

		private boolean mouseInside;

		private @Nullable Point mousePosition;

		private @Nullable String tooltip;

		private @Nullable String clearTooltip;

		private @Nullable IdeTooltip shownTooltip;

		private boolean shownTooltipForClear;

		private Runnable popupHandler = () -> {
		};

		private Runnable clearHandler = () -> {
		};

		private final BaseButtonBehavior behavior = new BaseButtonBehavior(this, TimedDeadzone.NULL, null) {

			@Override
			protected void execute(MouseEvent event) {
				// Click dispatch stays with ClickListener, matching ToolbarComboButtonUI.
			}

		};

		SelectorButton() {

			setForeground(JBUI.CurrentTheme.Label.foreground());
			setFont(headerLabelFont());
			setOpaque(false);

			behavior.setupListeners();
			behavior.setActionTrigger(BaseButtonBehavior.MOUSE_PRESSED_RELEASED);
			behavior.setMouseDeadzone(TimedDeadzone.NULL);
			new ClickListener() {

				@Override
				public boolean onClick(MouseEvent event, int clickCount) {

					if (!isEnabled() || !UIUtil.isActionClick(event, event.getID())) {
						return false;
					}
					handleClick(event);
					return true;
				}

			}.installOn(this);
		}

		void setIcon(@Nullable Icon icon) {
			this.icon = icon == null ? null : IconUtil.scaleByIconWidth(icon, this, EmptyIcon.create(ICON_SIZE));
			revalidate();
			repaint();
		}

		void setText(String text) {
			this.text = text;
			revalidate();
			repaint();
		}

		void setClearVisible(boolean clearVisible) {

			if (this.clearVisible != clearVisible) {
				this.clearVisible = clearVisible;
				revalidate();
				repaint();
			}
		}

		void setPopupShowing(boolean popupShowing) {
			this.popupShowing = popupShowing;
			repaint();
		}

		@Override
		public void setEnabled(boolean enabled) {

			if (isEnabled() != enabled) {

				mouseInside = false;
				mousePosition = null;
				hideTooltip();
			}
			super.setEnabled(enabled);
			repaint();
		}

		void setTooltips(@Nullable String tooltip, @Nullable String clearTooltip) {
			this.tooltip = tooltip;
			this.clearTooltip = clearTooltip;
		}

		void onPopup(Runnable popupHandler) {
			this.popupHandler = popupHandler;
		}

		void onClear(Runnable clearHandler) {
			this.clearHandler = clearHandler;
		}

		@Override
		protected void processMouseEvent(MouseEvent event) {

			super.processMouseEvent(event);

			if (event.getID() == MouseEvent.MOUSE_EXITED) {
				mouseInside = false;
				mousePosition = null;
				hideTooltip();
				repaint();
				return;
			}

			if (event.getID() == MouseEvent.MOUSE_ENTERED || event.getID() == MouseEvent.MOUSE_PRESSED
					|| event.getID() == MouseEvent.MOUSE_RELEASED) {
				mouseInside = true;
				mousePosition = event.getPoint();
				repaint();
			}
		}

		@Override
		protected void processMouseMotionEvent(MouseEvent event) {

			super.processMouseMotionEvent(event);

			mouseInside = true;
			mousePosition = event.getPoint();
			showTooltip(event.getPoint());
			repaint();
		}

		/**
		 * Show the balloon tooltip with the pointer callout, like the platform's tab
		 * close cross (see ContentLabel): anchored at the cross center while over the
		 * clear zone, at the mouse position otherwise. The Swing tooltip path would
		 * render the flat rectangle instead.
		 */
		private void showTooltip(Point point) {

			boolean overClear = inClearZone(point);

			if (shownTooltip != null) {
				if (shownTooltipForClear == overClear) {
					IdeTooltipManager.getInstance().show(shownTooltip, false, false);
					return;
				}
				hideTooltip();
			}

			String text = overClear ? clearTooltip : tooltip;
			if (text == null || text.isEmpty()) {
				return;
			}

			Rectangle clearZone = clearZone();
			Point anchor = overClear
					? new Point(clearZone.x + clearZone.width / 2, clearZone.y + clearZone.height / 2)
					: point;
			IdeTooltip balloon = new IdeTooltip(this, anchor, new JLabel(text));
			shownTooltip = IdeTooltipManager.getInstance().show(balloon, false, false);
			shownTooltipForClear = overClear;
		}

		private void hideTooltip() {

			if (shownTooltip != null) {
				shownTooltip.hide();
				shownTooltip = null;
			}
		}

		private void handleClick(MouseEvent event) {

			if (inClearZone(event.getPoint())) {
				clearHandler.run();
				return;
			}
			popupHandler.run();
		}

		@Override
		public Dimension getPreferredSize() {

			Insets margin = selectorMargin();
			FontMetrics metrics = getFontMetrics(getFont());
			Icon chevron = AllIcons.General.LinkDropTriangle;

			int width = margin.left + margin.right + UIUtil.computeStringWidth(this, metrics, text)
					+ JBUIScale.scale(BEFORE_CHEVRON_GAP) + chevron.getIconWidth();
			int contentHeight = Math.max(metrics.getHeight(), chevron.getIconHeight());

			if (icon != null) {
				width += icon.getIconWidth() + JBUIScale.scale(ICON_TEXT_GAP);
				contentHeight = Math.max(contentHeight, icon.getIconHeight());
			}

			if (clearVisible) {
				width += JBUIScale.scale(CHEVRON_CLOSE_GAP) + AllIcons.Actions.Close.getIconWidth();
			}

			int height = Math.max(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height,
					contentHeight + margin.top + margin.bottom);
			return new Dimension(width, height);
		}

		@Override
		protected void paintComponent(Graphics g) {

			Graphics2D graphics = (Graphics2D) g.create();
			try {
				GraphicsUtil.setupAAPainting(graphics);

				boolean enabled = isEnabled();

				if (enabled && (isHovered() || popupShowing)) {
					int arc = ARC.get();
					graphics.setColor(HOVER_BACKGROUND);
					graphics.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
				}

				Insets margin = selectorMargin();
				int x = margin.left;

				int shift = JBUIScale.scale(CONTENT_SHIFT);

				if (icon != null) {
					Icon shown = enabled ? icon : IconLoader.getDisabledIcon(icon);
					shown.paintIcon(this, graphics, x, (getHeight() - shown.getIconHeight()) / 2 + shift);
					x += shown.getIconWidth() + JBUIScale.scale(ICON_TEXT_GAP);
				}

				FontMetrics metrics = graphics.getFontMetrics(getFont());
				graphics.setColor(enabled ? getForeground() : UIUtil.getLabelDisabledForeground());
				graphics.setFont(getFont());
				graphics.drawString(text, x, (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2 + shift);
				x += metrics.stringWidth(text) + JBUIScale.scale(BEFORE_CHEVRON_GAP);

				Icon chevron = AllIcons.General.LinkDropTriangle;
				chevron.paintIcon(this, graphics, x, (getHeight() - chevron.getIconHeight()) / 2 + shift);

				if (clearVisible) {

					Rectangle clearZone = clearZone();
					boolean clearHovered = isHovered() && mousePosition != null && clearZone.contains(mousePosition);

					// CloseHovered carries the platform's round highlight
					Icon close = clearHovered ? AllIcons.Actions.CloseHovered : AllIcons.Actions.Close;
					close.paintIcon(this, graphics, clearZone.x, clearZone.y);
				}
			} finally {
				graphics.dispose();
			}
		}

		private boolean inClearZone(Point point) {
			return clearVisible && clearZone().contains(point);
		}

		private Rectangle clearZone() {

			Icon close = AllIcons.Actions.Close;
			Insets margin = selectorMargin();
			return new Rectangle(getWidth() - margin.right - close.getIconWidth(),
					(getHeight() - close.getIconHeight()) / 2 + JBUIScale.scale(CONTENT_SHIFT), close.getIconWidth(),
					close.getIconHeight());
		}

		private boolean isHovered() {
			return mouseInside && behavior.isHovered();
		}

		private static Font headerLabelFont() {

			Font font = JBUI.CurrentTheme.ToolWindow.headerFont();
			return font.deriveFont(font.getSize() + JBUI.CurrentTheme.ToolWindow.overrideHeaderFontSizeOffset());
		}

		private static Insets selectorMargin() {
			return new Insets(JBUIScale.scale(VERTICAL_MARGIN), JBUIScale.scale(LEFT_MARGIN),
					JBUIScale.scale(VERTICAL_MARGIN), JBUIScale.scale(RIGHT_MARGIN));
		}

	}

	/**
	 * Option model backing a {@link SelectorAction}.
	 *
	 * @param <T> the option type.
	 * @author Mark Paluch
	 */
	abstract static class SelectorModel<T> extends CollectionComboBoxModel<T> {

		SelectorModel() {
			super(new ArrayList<>());
		}

		abstract String getText(T value);

		abstract void selectionChanged(@Nullable T selection);

		@Nullable
		String getToolTip(T value) {
			return getText(value);
		}

		@Nullable
		Icon getIcon(T value) {
			return null;
		}

		@Nullable
		String getSecondaryText(T value) {
			return null;
		}

		boolean startsNewGroup(T previous, T value) {
			return false;
		}

		void setValues(List<? extends T> values, @Nullable T selection) {
			replaceAll(new ArrayList<>(values));
			super.setSelectedItem(selection);
		}

		@Override
		@SuppressWarnings("unchecked")
		public void setSelectedItem(@Nullable Object item) {
			Object previous = getSelectedItem();
			super.setSelectedItem(item);
			if (previous != item) {
				selectionChanged((T) item);
			}
		}

	}

}
