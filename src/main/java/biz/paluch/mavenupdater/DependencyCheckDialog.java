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
package biz.paluch.mavenupdater;

import biz.paluch.mavenupdater.dependencies.ArtifactCoordinates;
import biz.paluch.mavenupdater.dependencies.ArtifactVersion;
import biz.paluch.mavenupdater.dependencies.DependencySource;
import biz.paluch.mavenupdater.dependencies.DependencyUpgrade;
import biz.paluch.mavenupdater.dependencies.DependencyUpgrades;
import icons.MavenIcons;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

import org.jspecify.annotations.Nullable;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

/**
 * Dialog showing Maven dependency versions and update suggestions.
 */
public class DependencyCheckDialog extends DialogWrapper {

	private final Project project;
	private final @Nullable VirtualFile pomFile;
	private final DependencyUpgrades result;

	public DependencyCheckDialog(Project project, @Nullable VirtualFile pomFile, DependencyUpgrades upgrades) {
		super(project, false, IdeModalityType.MODELESS);
		this.project = project;
		this.pomFile = pomFile;
		this.result = upgrades;
		setTitle("Maven Dependency Versions for " + project.getName() + (pomFile != null ? ": " + pomFile.getName() : ""));
		init();
	}

	@Override
	protected JComponent createCenterPanel() {
		String[] columnNames = { "Dependency", "Current", "Suggested", "Update" };
		Object[][] data = result.items().stream()
				.map(
						info -> new Object[] { info.coordinates(), info.currentVersion(), info.getUpgradeTo(), info.isDoUpgrade() })
				.toArray(n -> new Object[n][]);

		DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return column == 2 || column == 3;
			}

			@Override
			public Class<?> getColumnClass(int columnIndex) {
				if (columnIndex == 0) {
					return ArtifactCoordinates.class;
				}

				if (columnIndex == 2) {
					return ArtifactVersion.class;
				}

				return columnIndex == 3 ? Boolean.class : String.class;
			}
		};

		for (Object[] row : data) {
			model.addRow(row);
		}

		JTable table = new JBTable(model);
		table.setAutoCreateRowSorter(true);
		table.setShowGrid(true);
		table.setRowHeight(table.getRowHeight() + 4);
		table.setIntercellSpacing(new Dimension(JBUI.scale(2), JBUI.scale(2)));
		table.setPreferredScrollableViewportSize(new Dimension(JBUI.scale(750), JBUI.scale(400)));
		table.getTableHeader().setReorderingAllowed(false);
		// Sync table edits back to VersionUpdateSuggestion: version -> upgradeTo + doUpgrade, checkbox -> doUpgrade
		model.addTableModelListener(new TableModelListener() {
			@Override
			public void tableChanged(TableModelEvent e) {
				if (e.getType() != TableModelEvent.UPDATE || e.getColumn() < 0) {
					return;
				}
				int row = e.getFirstRow();
				if (row < 0 || row >= result.items().size()) {
					return;
				}
				DependencyUpgrade suggestion = result.items().get(row);
				int col = e.getColumn();
				if (col == 2) {
					Object value = model.getValueAt(row, 2);
					if (value instanceof ArtifactVersion av && !av.equals(suggestion.getUpgradeTo())) {
						suggestion.setUpgradeTo(av);
						suggestion.setDoUpgrade(true);
						model.setValueAt(true, row, 3);
					}
				} else if (col == 4) {
					Object value = model.getValueAt(row, 3);
					suggestion.setDoUpgrade(Boolean.TRUE.equals(value));
				}
			}
		});

		TableColumnModel columns = table.getColumnModel();

		columns.getColumn(0).setPreferredWidth(JBUI.scale(280));
		columns.getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
					int row, int column) {
				Component tableCellRendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
						row, column);

				DependencyUpgrade info = result.getInfo(row);
				if (info == null) {
					return tableCellRendererComponent;
				}

				String tooltip = info.coordinates().toString();
				if (info.source() instanceof DependencySource.Plugin) {
					tooltip = "Plugin: " + tooltip;
					setIcon(MavenIcons.MavenPlugin);
				} else if (info.source() instanceof DependencySource.Dependency) {
					setIcon(MavenIcons.MavenProject);
				}

				if (info.source() instanceof DependencySource.Profile profile) {
					tooltip += ". Profile: " + profile.getProfileId();
				}

				setToolTipText(tooltip);
				return tableCellRendererComponent;
			}

			@Override
			protected void setValue(Object value) {
				if (value instanceof ArtifactCoordinates ac) {
					setText(ac.artifactId());
				}
			}
		});
		columns.getColumn(1).setPreferredWidth(JBUI.scale(140));
		columns.getColumn(2).setPreferredWidth(JBUI.scale(220));
		columns.getColumn(1).setCellEditor(null);

		columns.getColumn(2).setCellRenderer(new SuggestedVersionTableCellRenderer(result, table));
		SuggestedVersionComboBoxEditor cellEditor = new SuggestedVersionComboBoxEditor(result, table);
		cellEditor.getCombo().addItemListener(it -> {
			if (it.getStateChange() == ItemEvent.SELECTED) {
				table.editingStopped(new ChangeEvent(it.getItem()));
			}
		});
		columns.getColumn(2).setCellEditor(cellEditor);

		int updateColumnIndex = 3;
		JCheckBox booleanCheckBox = new JCheckBox();
		booleanCheckBox.setHorizontalAlignment(SwingConstants.CENTER);
		columns.getColumn(updateColumnIndex).setCellRenderer(new TableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable t, @Nullable Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				booleanCheckBox.setSelected(Boolean.TRUE.equals(value));
				booleanCheckBox.setBackground(isSelected ? t.getSelectionBackground() : t.getBackground());
				return booleanCheckBox;
			}
		});
		JCheckBox editorCheckBox = new JCheckBox();
		editorCheckBox.setHorizontalAlignment(SwingConstants.CENTER);
		columns.getColumn(updateColumnIndex).setCellEditor(new javax.swing.DefaultCellEditor(editorCheckBox));

		AnAction selectAllAction = new AnAction("Select All", "Select all dependencies for update",
				AllIcons.Actions.Selectall) {
			@Override
			public void actionPerformed(AnActionEvent e) {
				for (int r = 0; r < model.getRowCount(); r++) {
					model.setValueAt(true, r, updateColumnIndex);
				}
			}
		};
		AnAction deselectAllAction = new AnAction("Unselect All", "Unselect all dependencies",
				AllIcons.Actions.Unselectall) {
			@Override
			public void actionPerformed(AnActionEvent e) {
				for (int r = 0; r < model.getRowCount(); r++) {
					model.setValueAt(false, r, updateColumnIndex);
				}
			}
		};
		DefaultActionGroup toolbarGroup = new DefaultActionGroup();
		toolbarGroup.add(selectAllAction);
		toolbarGroup.add(deselectAllAction);
		var toolbar = ActionManager.getInstance().createActionToolbar("MavenDependencyVersions", toolbarGroup, true);
		toolbar.setTargetComponent(table);

		JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		toolbarPanel.add(toolbar.getComponent());

		JBScrollPane scrollPane = new JBScrollPane(table);
		scrollPane.setPreferredSize(new Dimension(JBUI.scale(775), JBUI.scale(420)));

		JPanel panel = new JPanel(new BorderLayout());
		panel.add(toolbarPanel, BorderLayout.NORTH);
		panel.add(scrollPane, BorderLayout.CENTER);
		if (!result.errors().isEmpty()) {
			String errorText = "Warnings:\n" + result.errors().stream().map(s -> "• " + s).collect(Collectors.joining("\n"));
			JTextArea errorArea = new JTextArea(errorText);
			errorArea.setEditable(false);
			errorArea.setRows(3);
			errorArea.setLineWrap(true);
			panel.add(new JScrollPane(errorArea), BorderLayout.SOUTH);
		}
		return panel;
	}

	@Override
	public @Nullable JComponent getPreferredFocusedComponent() {
		return null;
	}

	@Override
	protected void doOKAction() {
		if (pomFile != null) {
			new PomUpdateService(project).applyUpgrades(pomFile, result.items());
		}
		super.doOKAction();
	}

	private List<ArtifactVersion> filterVersionSuggestions(Collection<ArtifactVersion> versions,
			ArtifactVersion current) {

		ArtifactVersion previousMinor = null;
		ArtifactVersion previousMajor = null;
		ArtifactVersion filterMilestone = null;
		List<ArtifactVersion> newer = new ArrayList<>();
		List<ArtifactVersion> older = new ArrayList<>();

		for (ArtifactVersion version : versions.stream().sorted().toList()) {

			if (version.isPreview()) {

				if (filterMilestone == null) {
					filterMilestone = version;
				} else if (version.isNewer(filterMilestone)) {
					filterMilestone = version;
				}
				continue;
			} else {
				if (filterMilestone != null) {
					doAdd(current, filterMilestone, newer, older);
					filterMilestone = null;
				}
			}

			if (previousMajor == null
					|| version.isNewer(previousMajor) && version.getVersion().getMajor() < current.getVersion().getMajor()) {
				previousMajor = version;
			}

			if (previousMinor == null || version.isNewer(previousMinor) && version.hasSameMajorMinor(current)) {
				previousMinor = version;
			}

			doAdd(current, version, newer, older);
		}

		if (filterMilestone != null) {
			doAdd(current, filterMilestone, newer, older);
		}

		Set<ArtifactVersion> result = new TreeSet<>(Comparator.reverseOrder());

		result.addAll(older.reversed().stream().limit(10).toList());
		if (previousMajor != null) {
			result.add(previousMajor);
		}

		if (previousMinor != null) {
			result.add(previousMinor);
		}

		result.add(current);
		result.addAll(newer);

		return List.copyOf(result);
	}

	private static void doAdd(ArtifactVersion current, ArtifactVersion version, List<ArtifactVersion> newer,
			List<ArtifactVersion> older) {
		if (version.isNewer(current)) {
			newer.add(version);
		} else if (version.isBugFixVersion() || version.isReleaseVersion()) {
			older.add(version);
		}
	}

}
