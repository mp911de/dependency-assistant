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

import biz.paluch.mavenupdater.artifact.ArtifactVersion;
import biz.paluch.mavenupdater.artifact.DependencyUpdateOption;
import biz.paluch.mavenupdater.artifact.VersionOption;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

import org.jspecify.annotations.Nullable;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.ui.JBUI;

/**
 * Table cell editor for the Suggested column: combobox of version options (with release dates and version-age icon).
 */
class SuggestedVersionComboBoxEditor extends AbstractCellEditor implements TableCellEditor {

	private final ComboBox<VersionOption> combo = new ComboBox<>();

	private final DependencyUpdateModel model;
	private final List<VersionOption> options = new ArrayList<>();

	public SuggestedVersionComboBoxEditor(DependencyUpdateModel model, DependencyUpdateOption option) {
		this.model = model;
		this.combo.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
		this.combo.setModel(new CollectionComboBoxModel<>(options));
		this.combo.setRenderer(new VersionOptionCellRenderer(option.currentVersion()));
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, @Nullable Object value, boolean isSelected, int row,
			int column) {

		combo.setFont(table.getFont());
		combo.setBorder(JBUI.Borders.empty());

		DependencyUpdateOption info = ModelUtil.getOption(table, row);
		refreshOptions(info);
		return combo;
	}

	private void refreshOptions(DependencyUpdateOption info) {

		ArtifactVersion currentValue = info.getUpdateTo() == null
				? (combo.getSelectedItem() instanceof VersionOption vo ? vo.version() : info.currentVersion())
				: info.getUpdateTo();
		List<VersionOption> options = model.isFilterVersionSuggestions() ? info.filtered() : info.versionOptions();

		if (!this.options.equals(options)) {
			this.options.clear();
			this.options.addAll(options);
		}

		VersionOption selected = null;
		for (VersionOption opt : options) {
			if (currentValue != null && opt.version().equals(currentValue)) {
				selected = opt;
			}
		}

		if (selected != null) {
			combo.getModel().setSelectedItem(selected);
		}
	}

	@Override
	public @Nullable Object getCellEditorValue() {
		Object item = combo.getSelectedItem();
		if (item instanceof VersionOption vo) {
			return vo.version();
		}
		return item != null ? item.toString() : MessageBundle.message("dialog.version.empty");
	}

	public ComboBox<VersionOption> getCombo() {
		return combo;
	}

}
