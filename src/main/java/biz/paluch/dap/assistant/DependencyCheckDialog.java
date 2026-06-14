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

package biz.paluch.dap.assistant;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.ReleaseDateFormatter;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.EditorSchemes;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jspecify.annotations.Nullable;

/**
 * Dialog showing Maven dependency versions and update suggestions.
 *
 * @author Mark Paluch
 */
public class DependencyCheckDialog extends DialogWrapper {

	private static final int DEPENDENCY_COLUMN_INDEX = 0;

	private static final int UPGRADE_TARGETS_COLUMN_INDEX = 2;

	/**
	 * Strategies offered as one-click targets in the upgrades column. LATEST and
	 * RELEASE are not shown.
	 */
	private static final Set<UpgradeStrategy> UPGRADE_TARGET_STRATEGIES = EnumSet
			.complementOf(EnumSet.of(UpgradeStrategy.LATEST, UpgradeStrategy.RELEASE));

	private static final int SUGGESTED_VERSION_CELL_PADDING = 6;

	private final Project project;

	private final UpgradeReview review;

	private final Collection<VirtualFile> files;

	private final DependencyCheckComponents components;

	private volatile boolean applyingUpdates;

	private volatile @Nullable ProgressIndicator updateProgress;

	public DependencyCheckDialog(Project project, DependencyUpgradeCandidates result, String title) {
		super(project, false, IdeModalityType.MODELESS);
		this.project = project;
		this.files = result.files();
		this.review = new UpgradeReview(result.candidates(), result.errors());
		this.components = new DependencyCheckComponents(this.review, getDisposable(), this::doCancelAction,
				this::navigateToSites);
		setTitle(title);
		init();
	}

	/**
	 * Run a Dependency Site Find for the double-clicked row and present the result
	 * for navigation.
	 */
	private void navigateToSites(UpgradeCandidate candidate, RelativePoint where) {
		new DependencySitesPopup(project, getDisposable(), this::doCancelAction, files).navigate(candidate, where);
	}

	/**
	 * Components panel for the dependency check dialog, containing the table and
	 * related controls.
	 */
	static class DependencyCheckComponents extends JPanel {

		private final static int STATE_STRIP_HEIGHT = getStrategyStripHeight();

		private final UpgradeReview review;

		private final ListTableModel<UpgradeCandidate> tableModel;

		private final DependencyUpdateTable table;

		private final ActionToolbar toolbar;

		private final ComboBox<UpgradeReview.UpgradeStrategies> strategyComboBox;

		private final JCheckBox filterVersionsCheckBox;

		DependencyCheckComponents(UpgradeReview review, Disposable parent, Runnable escapeHandler,
				BiConsumer<UpgradeCandidate, RelativePoint> onNavigate) {
			super(new BorderLayout());
			this.review = review;

			this.tableModel = new ListTableModel<>(new DependencyCoordinateColumn(review),
					new CurrentVersionColumn(), new UpgradeTargetsColumn(review), new UpdateToColumn(review),
					new DoUpdateColumn(review));
			this.table = new DependencyUpdateTable(tableModel, escapeHandler, onNavigate);
			this.strategyComboBox = new ComboBox<>(
					UpgradeReview.UpgradeStrategies.values());
			this.filterVersionsCheckBox = new JCheckBox(MessageBundle.message("dialog.filter.version.suggestions"),
					this.review.isHideUpToDate());

			ActionGroup toolbarGroup = createToolbarGroup();
			this.toolbar = ActionManager.getInstance().createActionToolbar("DependencyVersions", toolbarGroup,
					true);

			initialize(parent);
		}

		private void initialize(Disposable parent) {

			tableModel.setItems(this.review.getCandidates());
			table.setRowSelectionAllowed(false);
			table.setCellSelectionEnabled(false);

			review.addListener(change -> {

				if (change.reload()) {
					stopEditing();
					tableModel.setItems(review.getCandidates());
					return;
				}

				UpgradeCandidate candidate = change.candidate();
				if (candidate != null) {
					int modelRow = tableModel.indexOf(candidate);
					if (modelRow >= 0) {
						tableModel.fireTableRowsUpdated(modelRow, modelRow);
					}
					return;
				}

				int rowCount = tableModel.getRowCount();
				if (rowCount > 0) {
					tableModel.fireTableRowsUpdated(0, rowCount - 1);
				}
			}, parent);

			table.setAutoCreateRowSorter(true);
			table.setShowGrid(true);
			table.setRowHeight(Math.max(table.getRowHeight(), STATE_STRIP_HEIGHT) + 4);
			table.setIntercellSpacing(new Dimension(JBUI.scale(2), JBUI.scale(2)));
			table.getTableHeader().setReorderingAllowed(false);

			table.setPreferredScrollableViewportSize(new Dimension(JBUI.scale(820), JBUI.scale(420)));

			TableColumnModel columns = table.getColumnModel();

			columns.getColumn(0).setPreferredWidth(JBUI.scale(280));
			columns.getColumn(1).setPreferredWidth(JBUI.scale(90));
			columns.getColumn(2).setPreferredWidth(JBUI.scale(90));
			columns.getColumn(3).setPreferredWidth(JBUI.scale(180));
			TableUtil.setupCheckboxColumn(columns.getColumn(4), columns.getColumnMargin() + JBUI.scale(32));

			filterVersionsCheckBox.setToolTipText(MessageBundle.message("dialog.filter.version.tooltip"));

			toolbar.setTargetComponent(table);
			installSpeedSearch(table);

			filterVersionsCheckBox.addItemListener(e -> review.setHideUpToDate(filterVersionsCheckBox.isSelected()));

			strategyComboBox.setSelectedItem(this.review.getUpgradeStrategy());
			strategyComboBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
				label.setText(MessageBundle.message(value.getMessageKey()));
				label.setIcon(value.getIcon());
				label.setIconTextGap(JBUI.scale(4));
			}));
			strategyComboBox.addItemListener(e -> {
				if (e.getStateChange() != ItemEvent.SELECTED) {
					return;
				}
				UpgradeReview.UpgradeStrategies strategy = (UpgradeReview.UpgradeStrategies) e
						.getItem();
				review.applyStrategyToAll(strategy);
			});

			JPanel strategyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(5), 0));
			JLabel strategyLabel = new JLabel(MessageBundle.message("dialog.upgradeStrategy.label"));
			strategyComboBox.setToolTipText(MessageBundle.message("dialog.upgradeStrategy.tooltip"));
			strategyPanel.add(strategyLabel);
			strategyPanel.add(strategyComboBox);

			JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
			rightPanel.add(strategyPanel);
			rightPanel.add(toolbar.getComponent());

			JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(12), 0));
			leftPanel.add(filterVersionsCheckBox);

			JPanel toolbarPanel = new JPanel(new BorderLayout());
			toolbarPanel.add(leftPanel, BorderLayout.WEST);
			toolbarPanel.add(rightPanel, BorderLayout.EAST);

			JBScrollPane scrollPane = new JBScrollPane(table);
			scrollPane.setPreferredSize(new Dimension(JBUI.scale(775), JBUI.scale(420)));

			add(toolbarPanel, BorderLayout.NORTH);
			add(scrollPane, BorderLayout.CENTER);
			if (!this.review.getErrors().isEmpty()) {
				String errorText = "<html><b>"
						+ com.intellij.openapi.util.text.StringUtil
								.escapeXmlEntities(MessageBundle.message("dialog.warnings.prefix"))
						+ "</b><br>"
						+ this.review.getErrors().stream()
								.map(s -> MessageBundle.message("dialog.warnings.bullet") + s)
								.map(com.intellij.openapi.util.text.StringUtil::escapeXmlEntities)
								.collect(Collectors.joining("<br>"))
						+ "</html>";
				InlineBanner warningBanner = new InlineBanner(errorText, EditorNotificationPanel.Status.Warning);
				warningBanner.showCloseButton(false);
				add(warningBanner, BorderLayout.SOUTH);
			}
		}

		private static void installSpeedSearch(TableView<UpgradeCandidate> table) {

			TableViewSpeedSearch<UpgradeCandidate> speedSearch = new TableViewSpeedSearch<>(table, null) {

				@Override
				protected @Nullable String getItemText(UpgradeCandidate item) {
					return item.getArtifactId() + " " + item.getDependencyName() + " " + item.getRowLabel();
				}

			};
			speedSearch.setupListeners();
		}

		private DefaultActionGroup createToolbarGroup() {

			AnAction selectAllAction = new AnAction(MessageBundle.message("dialog.action.selectAll"),
					MessageBundle.message("dialog.action.selectAll.description"), AllIcons.Actions.Selectall) {

				@Override
				public void actionPerformed(AnActionEvent e) {
					review.selectAll(true);
				}

			};

			AnAction deselectAllAction = new AnAction(MessageBundle.message("dialog.action.unselectAll"),
					MessageBundle.message("dialog.action.unselectAll.description"), AllIcons.Actions.Unselectall) {

				@Override
				public void actionPerformed(AnActionEvent e) {
					review.selectAll(false);
				}

			};

			DefaultActionGroup toolbarGroup = new DefaultActionGroup();
			toolbarGroup.add(selectAllAction);
			toolbarGroup.add(deselectAllAction);
			return toolbarGroup;
		}

		void stopEditing() {
			TableUtil.stopEditing(table);
		}

		void setControlsEnabled(boolean enabled) {
			UIUtil.setEnabled(this.table, enabled, true);
			UIUtil.setEnabled(this.filterVersionsCheckBox, enabled, true);
			UIUtil.setEnabled(this.strategyComboBox, enabled, true);
			UIUtil.setEnabled(this.toolbar.getComponent(), enabled, true);
		}

		/**
		 * Row-height floor matching the upgrades column button strip so rows keep the
		 * same height whether or not any row offers strategy buttons.
		 */
		private static int getStrategyStripHeight() {

			int height = 0;
			for (UpgradeStrategy strategy : UPGRADE_TARGET_STRATEGIES) {
				height = Math.max(height, strategyIconSize(VersionAge.fromTarget(strategy).getIcon()).height);
			}
			return height;
		}

	}

	@Override
	protected JComponent createCenterPanel() {
		return this.components;
	}

	@Override
	public @Nullable JComponent getPreferredFocusedComponent() {
		return null;
	}

	@Override
	protected void doOKAction() {
		if (applyingUpdates) {
			return;
		}

		this.components.stopEditing();

		List<DependencyUpdate> updates = review.getSelectedUpdates();
		if (updates.isEmpty()) {
			super.doOKAction();
			return;
		}

		setBusy(true);
		new ApplyUpdatesTask(project, updates).queue();
	}

	@Override
	public void doCancelAction() {
		if (applyingUpdates) {
			ProgressIndicator indicator = this.updateProgress;
			if (indicator != null) {
				indicator.cancel();
			}
			return;
		}
		restartHighlighting();
		super.doCancelAction();
	}

	private void setBusy(boolean busy) {

		this.applyingUpdates = busy;
		setOKActionEnabled(!busy);
		this.components.setControlsEnabled(!busy);
	}

	private void applyUpdates(List<DependencyUpdate> updates, List<DependencyAssistant> assistants,
			ProgressIndicator indicator) {

		Set<AppliedDependencyUpdate> applied = new TreeSet<>();
		new BuildActionDelegate(project, (file, fileUpdates) -> {

			indicator.checkCanceled();
			indicator.setText2(file.getName());
			for (DependencyAssistant dependencyAssistant : assistants) {
				indicator.checkCanceled();
				if (!dependencyAssistant.supports(file)) {
					continue;
				}

				ProjectDependencyContext context = dependencyAssistant.createContext(file);
				if (context.isAvailable()) {
					indicator.checkCanceled();
					context.applyUpdates(file, fileUpdates);

					for (DependencyUpdate fileUpdate : fileUpdates) {
						applied.add(new AppliedDependencyUpdate(fileUpdate.coordinate(), fileUpdate.from(),
								fileUpdate.version()));
					}
				}
			}
		}).updateBuildFiles(files, updates);

		Notifications.updatesApplied(project, applied);
	}

	class ApplyUpdatesTask extends Task.Backgroundable {

		private final List<DependencyUpdate> updates;

		private final List<DependencyAssistant> assistants;

		private ApplyUpdatesTask(Project project, List<DependencyUpdate> updates) {
			super(project, MessageBundle.message("intention.UpgradingDependencies.text"), true);
			this.updates = updates;
			this.assistants = DependencyAssistantDispatcher.findAll(project);
		}

		@Override
		public void run(ProgressIndicator indicator) {
			updateProgress = indicator;
			indicator.setIndeterminate(true);
			indicator.setText(MessageBundle.message("intention.UpgradingDependencies.text"));
			applyUpdates(updates, assistants, indicator);
		}

		@Override
		public void onSuccess() {

			restartHighlighting();
			close(OK_EXIT_CODE);
		}

		@Override
		public void onThrowable(Throwable error) {
			Notifications.error(project,
					MessageBundle.message("UpdateBuildFile.notification.error.title"),
					Notifications.errorMessage(error));
		}

		@Override
		public void onFinished() {

			updateProgress = null;
			if (isShowing()) {
				setBusy(false);
			} else {
				applyingUpdates = false;
			}
		}

	}

	private void restartHighlighting() {

		ReadAction.run(() -> {
			DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(project);
			BetterPsiManager psiManager = BetterPsiManager.getInstance(project);
			psiManager.stream(files).forEach(analyzer::restart);
		});
	}

	static class DependencyUpdateTable extends TableView<UpgradeCandidate> {

		private final Runnable escapeHandler;

		private final BiConsumer<UpgradeCandidate, RelativePoint> onNavigate;

		DependencyUpdateTable(ListTableModel<UpgradeCandidate> model, Runnable escapeHandler,
				BiConsumer<UpgradeCandidate, RelativePoint> onNavigate) {
			super(model);
			this.escapeHandler = escapeHandler;
			this.onNavigate = onNavigate;
			setToolTipText("");

			getActionMap().put("cancel", new AbstractAction() {

				@Override
				public void actionPerformed(ActionEvent e) {
				}

				@Override
				public boolean isEnabled() {
					return false;
				}

			});

			addMouseMotionListener(new MouseMotionAdapter() {

				@Override
				public void mouseMoved(MouseEvent e) {
					editUpgradeTargetsOnHover(e);
				}

			});

			addMouseListener(new MouseAdapter() {

				@Override
				public void mouseClicked(MouseEvent e) {
					navigateOnDoubleClick(e);
				}

			});
		}

		private void navigateOnDoubleClick(MouseEvent e) {

			if (e.getClickCount() != 2) {
				return;
			}

			Point p = e.getPoint();
			int row = rowAtPoint(p);
			int col = columnAtPoint(p);
			if (row < 0 || convertColumnIndexToModel(col) != DEPENDENCY_COLUMN_INDEX) {
				return;
			}

			onNavigate.accept(ModelUtil.getRow(this, row), new RelativePoint(this, p));
		}

		@Override
		public void editingCanceled(ChangeEvent e) {
			editingCanceled(e, currentEvent());
		}

		void editingCanceled(@Nullable ChangeEvent e, @Nullable AWTEvent trigger) {

			boolean hoverEditing = getEditingColumn() == convertColumnIndexToView(UPGRADE_TARGETS_COLUMN_INDEX);
			super.editingCanceled(e);

			// IdeEventQueue cancels any active table editor on ESC and consumes the
			// event before key bindings run. The hover editor holds no edit state to
			// abort, so that ESC means "close the dialog".
			if (hoverEditing && isEscapeKeyPress(trigger)) {
				escapeHandler.run();
			}
		}

		private static @Nullable AWTEvent currentEvent() {
			return Toolkit.getDefaultToolkit().getSystemEventQueue() instanceof IdeEventQueue ideEventQueue
					? ideEventQueue.getTrueCurrentEvent()
					: null;
		}

		private static boolean isEscapeKeyPress(@Nullable AWTEvent event) {
			return event instanceof KeyEvent keyEvent && keyEvent.getID() == KeyEvent.KEY_PRESSED
					&& keyEvent.getKeyCode() == KeyEvent.VK_ESCAPE;
		}

		private void editUpgradeTargetsOnHover(MouseEvent e) {

			Point p = e.getPoint();
			int row = rowAtPoint(p);
			int col = columnAtPoint(p);
			if (row < 0 || col != convertColumnIndexToView(UPGRADE_TARGETS_COLUMN_INDEX)) {
				return;
			}

			if (isEditing() && getEditingRow() == row && getEditingColumn() == col) {
				return;
			}

			if (isCellEditable(row, col)) {
				editCellAt(row, col);
			}
		}

	}

	static class DependencyCoordinateColumn extends ColumnInfo<UpgradeCandidate, ArtifactId> {

		static final TextAttributesKey WEAK_WARNING_KEY = HighlightInfoType.WEAK_WARNING.getAttributesKey();

		static final TextAttributes WEAK_WARNING_ATTRIBUTES = EditorSchemes.attributes(WEAK_WARNING_KEY,
				new TextAttributes());

		/**
		 * Weak-warning wave underline for rows coupled through a Shared Version
		 * Property: informative, never blocking.
		 */
		static final SimpleTextAttributes SHARED_PROPERTY_ATTRIBUTES = new SimpleTextAttributes(
				SimpleTextAttributes.STYLE_PLAIN | SimpleTextAttributes.STYLE_WAVED, null,
				WEAK_WARNING_ATTRIBUTES.getEffectColor());

		private final UpgradeReview review;

		private final Map<UpgradeCandidate, String> tooltips = Collections.synchronizedMap(new HashMap<>());

		private final ColoredTableCellRenderer renderer = new ColoredTableCellRenderer() {

			@Override
			protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus,
					int row, int column) {

				UpgradeCandidate candidate = ModelUtil.getRow(table, row);
				List<UpgradeCandidate> peers = review.getSharedPropertyPeers(candidate);
				boolean waved = !peers.isEmpty() || candidate.getDeclaredVersions().hasDeclarationDrift();
				append(candidate.getRowLabel(),
						waved ? SHARED_PROPERTY_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);

				if (candidate instanceof UpgradeGroup group) {
					append("  (%d)".formatted(group.getMembers().size()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
				} else if (review.isAmbiguous(candidate)) {
					append("  (%s)".formatted(candidate.getArtifactId().groupId()),
							SimpleTextAttributes.GRAYED_ATTRIBUTES);
				}

				setIcon(peers.isEmpty() ? candidate.getTableIcon() : DependencyAssistantIcons.SHARED_PROPERTY);

				String tooltip = tooltips.computeIfAbsent(candidate, c -> toolTip(c, peers));
				setToolTipText(tooltip);
			}

		};

		DependencyCoordinateColumn(UpgradeReview review) {
			super(MessageBundle.message("dialog.column.dependency"));
			this.review = review;
		}

		private static String toolTip(UpgradeCandidate candidate, List<UpgradeCandidate> peers) {
			StringBuilder tooltip = new StringBuilder();
			tooltip.append(candidate instanceof UpgradeGroup group ? memberToolTip(group)
					: coordinateToolTip(candidate));

			if (!peers.isEmpty()) {
				if (!tooltip.isEmpty()) {
					tooltip.append("<br>");
				}
				tooltip.append(sharedPropertyToolTip(candidate, peers));
			}

			DeclaredVersions declaredVersions = candidate.getDeclaredVersions();
			if (declaredVersions.hasDeclarationDrift()) {
				if (!tooltip.isEmpty()) {
					tooltip.append("<br>");
				}
				tooltip.append(declaredVersions.getDeclarationDriftToolTipText());
			}

			return "<html>" + tooltip + "</html>";
		}

		private static String coordinateToolTip(UpgradeCandidate candidate) {

			DependencyUpdateCandidate option = candidate.getUpdateCandidate();

			String artifactId = option.getArtifactId().toString();
			String tooltip = artifactId;
			if (option.hasPropertyVersion()) {
				VersionSource.VersionProperty versionProperty = option.getPropertyVersion();
				tooltip = MessageBundle.message("dialog.tooltip.property", "<code>" + versionProperty + "</code>");
				if (versionProperty instanceof VersionSource.Profile pps) {
					tooltip += MessageBundle.message("dialog.tooltip.profile",
							"<code>" + pps.getProfileId() + "</code>");
				}
			}

			if (option.getDeclarationSource() instanceof DeclarationSource.Plugin) {
				tooltip += MessageBundle.message("dialog.tooltip.plugin", artifactId);
			}

			if (option.getDeclarationSource() instanceof DeclarationSource.Profile profile) {
				tooltip += MessageBundle.message("dialog.tooltip.profile",
						"<code>" + profile.getProfileId() + "</code>");
			}

			return tooltip;
		}

		private static String memberToolTip(UpgradeGroup group) {

			StringBuilder tooltip = new StringBuilder();
			tooltip.append("<b>")
					.append(MessageBundle.message("dialog.tooltip.group.header", group.getDependencyName()))
					.append("</b><ul>");

			for (UpgradeCandidate member : group.getMembers()) {

				DependencyUpdateCandidate option = member.getUpdateCandidate();
				String versionSource = option.hasPropertyVersion() ? "<code>" + option.getPropertyVersion() + "</code>"
						: MessageBundle.message("dialog.tooltip.group.member.inline");
				tooltip.append("<li>")
						.append(MessageBundle.message("dialog.tooltip.group.member", member.getArtifactId(),
								versionSource))
						.append("</li>");
			}

			return tooltip.append("</ul>").toString();
		}

		private static String sharedPropertyToolTip(UpgradeCandidate candidate, List<UpgradeCandidate> peers) {

			Set<String> names = candidate.getVersionPropertyNames();
			Set<String> shared = new LinkedHashSet<>();
			for (UpgradeCandidate peer : peers) {
				Set<String> peerShared = new LinkedHashSet<>(peer.getVersionPropertyNames());
				peerShared.retainAll(names);
				shared.addAll(peerShared);
			}

			StringBuilder tooltip = new StringBuilder("<br>");
			tooltip.append(MessageBundle.message("dialog.tooltip.sharedProperty",
					"<code>" + String.join(", ", shared) + "</code>"));

			tooltip.append("<ul>");
			for (UpgradeCandidate peer : peers) {
				tooltip.append("<li><code>").append(peer.getRowLabel()).append("</code></li>");
			}

			return tooltip.append("</ul>").toString();
		}

		@Override
		public ArtifactId valueOf(UpgradeCandidate item) {
			return item.getArtifactId();
		}

		@Override
		public TableCellRenderer getRenderer(UpgradeCandidate item) {
			return renderer;
		}

		@Override
		public Class<?> getColumnClass() {
			return ArtifactId.class;
		}

	}

	static class CurrentVersionColumn extends ColumnInfo<UpgradeCandidate, ArtifactVersion> {

		private final DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {

			private @Nullable Font cachedFont;

			private int cachedFontSize = -1;

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				super.getTableCellRendererComponent(
						table, value, isSelected, hasFocus, row, column);

				UpgradeCandidate candidate = ModelUtil.getRow(table, row);
				DeclaredVersions declaredVersions = candidate.getDeclaredVersions();
				EvaluatedDependencyRule rule = candidate.getRuleResult();

				if (declaredVersions.hasVersionDrift()) {
					setIcon(DependencyAssistantIcons.DEPENDENCY_RULE_WARN);
				} else if (rule.isPresent()) {
					setIcon(rule.getIcon());
				} else {
					setIcon(null);
				}
				setFont(getCachedEditorFont(getFont().getSize()));

				if (declaredVersions.hasVersionDrift() || rule.isPresent()) {
					setToolTipText(conflictTooltip(candidate, rule));
				} else {
					setToolTipText(null);
				}

				return this;
			}

			private Font getCachedEditorFont(int uiFontSize) {

				if (cachedFont == null || cachedFontSize != uiFontSize) {
					cachedFontSize = uiFontSize;
					cachedFont = EditorSchemes.editorFont(Font.PLAIN, uiFontSize);
				}

				return cachedFont;
			}

		};

		CurrentVersionColumn() {
			super(MessageBundle.message("dialog.column.current"));
		}

		@Override
		public @Nullable ArtifactVersion valueOf(UpgradeCandidate item) {
			return item.getCurrentVersion();
		}

		@Override
		public TableCellRenderer getRenderer(UpgradeCandidate item) {
			return renderer;
		}

		@Override
		public Class<?> getColumnClass() {
			return ArtifactVersion.class;
		}

		private static @Nullable String conflictTooltip(
				UpgradeCandidate candidate,
				EvaluatedDependencyRule rule) {
			StringBuilder tooltip = new StringBuilder();
			DeclaredVersions declaredVersions = candidate.getDeclaredVersions();

			if (declaredVersions.hasVersionDrift()) {
				tooltip.append(declaredVersions.getVersionDriftToolTipText(candidate.getCurrentVersion()));
			}
			if (rule.isPresent()) {
				tooltip.append(rule.getToolTipText());
			}

			if (StringUtils.isEmpty(tooltip.toString())) {
				return null;
			}

			return "<html>%s</html>".formatted(tooltip);
		}

	}

	static class UpgradeTargetsColumn extends ColumnInfo<UpgradeCandidate, Object> {

		private final UpgradeTargetsRenderer renderer;

		private final UpgradeTargetsToolbarEditor editor;

		UpgradeTargetsColumn(UpgradeReview review) {
			super(MessageBundle.message("dialog.column.upgrades"));
			this.renderer = new UpgradeTargetsRenderer(review);
			this.editor = new UpgradeTargetsToolbarEditor(review);
		}

		@Override
		public @Nullable Object valueOf(UpgradeCandidate item) {
			return null;
		}

		@Override
		public boolean isCellEditable(UpgradeCandidate item) {
			return true;
		}

		@Override
		public TableCellEditor getEditor(UpgradeCandidate item) {
			return editor;
		}

		@Override
		public TableCellRenderer getRenderer(UpgradeCandidate item) {
			return renderer;
		}

		@Override
		public Class<?> getColumnClass() {
			return Object.class;
		}

	}

	private static JPanel createStrategyStrip(Collection<? extends JComponent> components) {

		JPanel panel = new JPanel(new GridBagLayout());
		panel.setOpaque(true);
		panel.setBorder(JBUI.Borders.empty());

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = 0;
		constraints.anchor = GridBagConstraints.CENTER;
		constraints.insets = JBUI.insetsRight(JBUI.scale(2));

		int gridx = 0;
		for (JComponent component : components) {
			constraints.gridx = gridx++;
			panel.add(component, constraints);
		}

		GridBagConstraints filler = new GridBagConstraints();
		filler.gridx = gridx;
		filler.gridy = 0;
		filler.weightx = 1.0;
		filler.fill = GridBagConstraints.HORIZONTAL;
		panel.add(Box.createHorizontalGlue(), filler);
		return panel;
	}

	private static Dimension strategyIconSize(Icon icon) {

		int padding = JBUI.scale(8);
		return new Dimension(icon.getIconWidth() + padding, icon.getIconHeight() + padding);
	}

	static class UpgradeTargetsToolbarEditor extends AbstractCellEditor implements TableCellEditor {

		private final UpgradeReview review;

		private final Map<UpgradeStrategy, ActionButton> buttons = new EnumMap<>(UpgradeStrategy.class);

		private final JPanel buttonPanel;

		private @Nullable UpgradeCandidate candidate;

		UpgradeTargetsToolbarEditor(UpgradeReview review) {

			this.review = review;
			for (UpgradeStrategy strategy : UPGRADE_TARGET_STRATEGIES) {
				buttons.put(strategy, createButton(strategy, () -> applyStrategy(strategy)));
			}
			this.buttonPanel = createStrategyStrip(buttons.values());
		}

		private void applyStrategy(UpgradeStrategy strategy) {

			if (candidate != null) {
				review.applyStrategyTarget(candidate, strategy);
			}
		}

		private static ActionButton createButton(UpgradeStrategy strategy, Runnable action) {

			Icon icon = VersionAge.fromTarget(strategy).getIcon();
			String shortLabel = MessageBundle.message("dialog.upgradeTarget." + strategy.name());

			AnAction buttonAction = new AnAction(shortLabel, null, icon) {

				@Override
				public void actionPerformed(AnActionEvent e) {
					action.run();
				}

			};

			Dimension size = strategyIconSize(icon);
			ActionButton button = new ActionButton(buttonAction, null, "DependencyAssistant.UpgradeTarget", size);
			button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			button.setPreferredSize(size);
			button.setMinimumSize(size);
			button.setMaximumSize(size);
			button.setLook(ActionButtonLook.SYSTEM_LOOK);
			return button;
		}

		@Override
		public JComponent getTableCellEditorComponent(JTable table, @Nullable Object value, boolean isSelected, int row,
				int column) {

			UpgradeCandidate candidate = ModelUtil.getRow(table, row);
			this.candidate = candidate;

			for (Map.Entry<UpgradeStrategy, ActionButton> entry : buttons.entrySet()) {

				UpgradeStrategy strategy = entry.getKey();
				ActionButton button = entry.getValue();
				Release target = review.resolveTarget(candidate, strategy);

				if (target == null) {
					button.setVisible(false);
					continue;
				}

				String shortLabel = MessageBundle.message("dialog.upgradeTarget." + strategy.name()) + ": "
						+ candidate.getInterfaceAssistant().getDocumentationText(target.getVersion());
				button.getPresentation().setText(shortLabel);
				button.setToolTipText(
						MessageBundle.message("dialog.upgradeTarget.tooltip", shortLabel, target.version()));
				button.getAccessibleContext().setAccessibleName(shortLabel);
				button.setVisible(true);
			}
			return buttonPanel;
		}

		@Override
		public @Nullable Object getCellEditorValue() {
			return null;
		}

	}

	/**
	 * Paint-only stamp mirroring {@link UpgradeTargetsToolbarEditor}'s button
	 * strip. The live buttons exist only in the editor; hover-to-edit makes them
	 * interactive.
	 */
	static class UpgradeTargetsRenderer implements TableCellRenderer {

		private final UpgradeReview review;

		private final Map<UpgradeStrategy, JLabel> icons = new EnumMap<>(UpgradeStrategy.class);

		private final JPanel panel;

		UpgradeTargetsRenderer(UpgradeReview review) {

			this.review = review;
			for (UpgradeStrategy strategy : UPGRADE_TARGET_STRATEGIES) {

				Icon icon = VersionAge.fromTarget(strategy).getIcon();
				JLabel label = new JLabel(icon);
				label.setHorizontalAlignment(SwingConstants.CENTER);
				label.setPreferredSize(strategyIconSize(icon));
				icons.put(strategy, label);
			}
			this.panel = createStrategyStrip(icons.values());
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected,
				boolean hasFocus, int row, int column) {

			UpgradeCandidate candidate = ModelUtil.getRow(table, row);
			icons.forEach((strategy, label) -> label.setVisible(review.resolveTarget(candidate, strategy) != null));
			panel.setEnabled(table.isEnabled());
			return panel;
		}

	}

	static class UpdateToColumn extends ColumnInfo<UpgradeCandidate, ArtifactVersion> {

		private final UpgradeReview review;

		private final SuggestedVersionRenderer renderer;

		private final SuggestedVersionComboBoxEditor editor;

		UpdateToColumn(UpgradeReview review) {
			super(MessageBundle.message("dialog.column.upgradeTo"));
			this.review = review;
			this.renderer = new SuggestedVersionRenderer(review);
			this.editor = new SuggestedVersionComboBoxEditor(review);
		}

		@Override
		public @Nullable ArtifactVersion valueOf(UpgradeCandidate item) {
			return review.getUpdateTo(item);
		}

		@Override
		public TableCellRenderer getRenderer(UpgradeCandidate item) {
			return renderer;
		}

		@Override
		public TableCellEditor getEditor(UpgradeCandidate item) {
			return editor;
		}

		@Override
		public void setValue(UpgradeCandidate item, ArtifactVersion value) {
			if (value != null && !value.matches(review.getUpdateTo(item))) {
				review.selectTarget(item, value);
			}
		}

		@Override
		public boolean isCellEditable(UpgradeCandidate item) {
			return true;
		}

		@Override
		public Class<?> getColumnClass() {
			return ArtifactVersion.class;
		}

	}

	/**
	 * Shared editor for the suggested-version column: a combo box of the visible
	 * release options, re-targeted to the edited row when editing starts. Picking
	 * an option stops editing; {@link UpdateToColumn#setValue} pushes the pick into
	 * the review.
	 */
	static class SuggestedVersionComboBoxEditor extends AbstractCellEditor implements TableCellEditor {

		private final UpgradeReview review;

		private final ComboBox<Release> combo = new ComboBox<>();

		private final VersionOptionCellRenderer optionRenderer = new VersionOptionCellRenderer();

		/** Suppresses selection events while the combo is re-targeted to a row. */
		private boolean refreshing;

		SuggestedVersionComboBoxEditor(UpgradeReview review) {

			this.review = review;
			combo.registerTableCellEditor(this);
			combo.setRenderer(optionRenderer);
			combo.setBorder(JBUI.Borders.empty(0, SUGGESTED_VERSION_CELL_PADDING));
			combo.setMinimumAndPreferredWidth(JBUI.scale(170));
			combo.addActionListener(e -> {
				if (!refreshing) {
					stopCellEditing();
				}
			});
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, @Nullable Object value, boolean isSelected, int row,
				int column) {

			UpgradeCandidate candidate = ModelUtil.getRow(table, row);
			optionRenderer.setCandidate(candidate);
			combo.setFont(table.getFont());

			refreshing = true;
			try {
				combo.setModel(new CollectionComboBoxModel<>(review.getReleases(candidate).toList(),
						review.getSelectedRelease(candidate)));
			} finally {
				refreshing = false;
			}
			return combo;
		}

		@Override
		public @Nullable Object getCellEditorValue() {
			return combo.getSelectedItem() instanceof Release release ? release.version() : null;
		}

	}

	/**
	 * Paint-only stamp for the suggested-version column: a combo box showing the
	 * candidate's selected release.
	 */
	static class SuggestedVersionRenderer implements TableCellRenderer {

		private final UpgradeReview review;

		private final ComboBox<Release> combo = new ComboBox<>();

		private final VersionOptionCellRenderer optionRenderer = new VersionOptionCellRenderer();

		private final CollectionComboBoxModel<Release> model = new CollectionComboBoxModel<>(new ArrayList<>());

		SuggestedVersionRenderer(UpgradeReview review) {

			this.review = review;
			combo.setModel(model);
			combo.setRenderer(optionRenderer);
			combo.setBorder(JBUI.Borders.empty(0, SUGGESTED_VERSION_CELL_PADDING));
			combo.setMinimumAndPreferredWidth(JBUI.scale(170));
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected,
				boolean hasFocus, int row, int column) {

			UpgradeCandidate candidate = ModelUtil.getRow(table, row);
			optionRenderer.setCandidate(candidate);
			combo.setFont(table.getFont());

			Release selected = review.getSelectedRelease(candidate);
			model.replaceAll(selected == null ? List.of() : List.of(selected));
			model.setSelectedItem(selected);
			combo.setEnabled(table.isEnabled());
			return combo;
		}

	}

	/**
	 * List cell renderer that shows an icon (older / newer patch / minor / major)
	 * plus version text, graying out versions that do not satisfy the dependency
	 * rule. Options are classified relative to the candidate set via
	 * {@link #setCandidate(UpgradeCandidate)}.
	 */
	static class VersionOptionCellRenderer extends ColoredListCellRenderer<Release> {

		private final ReleaseDateFormatter formatter = ReleaseDateFormatter.create();

		private @Nullable UpgradeCandidate candidate;

		VersionOptionCellRenderer() {
			setIconTextGap(JBUI.scale(4));
			setBorder(JBUI.Borders.empty());
		}

		void setCandidate(UpgradeCandidate candidate) {
			this.candidate = candidate;
		}

		@Override
		protected void customizeCellRenderer(JList<? extends Release> list, @Nullable Release value, int index,
				boolean selected, boolean hasFocus) {

			if (value == null || candidate == null) {
				return;
			}

			String text = Versioned.of(value.getVersion()).getVersion().toString();
			if (value.releaseDate() != null) {
				text += " (" + formatter.format(value.releaseDate()) + ")";
			}

			boolean valid = candidate.getRule().test(value.getVersion());
			append(text, valid ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
			setIcon(valid ? VersionAge.between(candidate.getCurrentVersion(), value.getVersion()).getIcon()
					: DependencyAssistantIcons.DEPENDENCY_RULE_WARN);
		}

	}

	static class DoUpdateColumn extends ColumnInfo<UpgradeCandidate, Boolean> {

		private final UpgradeReview review;

		DoUpdateColumn(UpgradeReview review) {
			super(MessageBundle.message("dialog.column.upgrade"));
			this.review = review;
		}

		@Override
		public TableCellRenderer getRenderer(UpgradeCandidate item) {
			return new BooleanTableCellRenderer();
		}

		@Override
		public TableCellEditor getEditor(UpgradeCandidate item) {
			return new BooleanTableCellEditor();
		}

		@Override
		public Boolean valueOf(UpgradeCandidate item) {
			return review.isApplyUpdate(item);
		}

		@Override
		public void setValue(UpgradeCandidate item, Boolean value) {
			review.setSelected(item, value);
		}

		@Override
		public boolean isCellEditable(UpgradeCandidate item) {
			return true;
		}

		@Override
		public Class<?> getColumnClass() {
			return Boolean.class;
		}

	}

}
