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

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.ui.JBUI;
import org.jspecify.annotations.Nullable;

/**
 * Table cell editor for the Suggested column: combobox of version options (with
 * release dates and version-age icon).
 *
 * @author Mark Paluch
 */
class SuggestedVersionComboBoxEditor extends AbstractCellEditor implements TableCellEditor {

	private static final int CELL_HORIZONTAL_PADDING = 6;

	private final ComboBox<Release> combo = new ComboBox<>();

	private final DependencyUpgradeReview review;

	private final UpdateCandidate candidate;

	private final List<Release> options = new ArrayList<>();

	/**
	 * Create an editor for the suggested version column.
	 */
	public SuggestedVersionComboBoxEditor(DependencyUpgradeReview review, UpdateCandidate candidate) {
		this.review = review;
		this.candidate = candidate;
		this.combo.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
		this.combo.setModel(new CollectionComboBoxModel<>(options));
		this.combo.setRenderer(new VersionOptionCellRenderer(candidate, candidate.currentVersion()));
		this.combo.addActionListener(e -> {

			Release selected = (Release) combo.getSelectedItem();
			if (selected != null && !selected.version().matches(review.getUpdateTo(candidate))) {
				review.selectTarget(candidate, selected.version());
				fireEditingStopped();
			}
		});
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, @Nullable Object value, boolean isSelected, int row,
			int column) {

		combo.setFont(table.getFont());
		combo.setBorder(JBUI.Borders.empty(0, CELL_HORIZONTAL_PADDING, 0, CELL_HORIZONTAL_PADDING));

		refreshOptions();
		return combo;
	}

	private void refreshOptions() {

		ArtifactVersion updateTo = review.getUpdateTo(candidate);
		ArtifactVersion currentValue = updateTo == null
				? (combo.getSelectedItem() instanceof Release release ? release.version() : candidate.currentVersion())
				: updateTo;

		Releases releases = review.visibleReleases(candidate);

		if (!this.options.equals(releases)) {
			this.options.clear();
			this.options.addAll(releases.toList());
		}

		Release selected = null;
		for (Release release : releases) {

			if (release.version().matches(currentValue)) {
				selected = release;
				break;
			}
		}

		if (selected != null) {
			combo.getModel().setSelectedItem(selected);
		}
	}

	@Override
	public @Nullable Object getCellEditorValue() {
		Object item = combo.getSelectedItem();
		if (item instanceof Release release) {
			return release.version();
		}
		return null;
	}

}
