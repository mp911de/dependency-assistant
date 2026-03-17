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

import java.awt.Component;

import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

import biz.paluch.mavenupdater.dependencies.DependencyUpgrade;
import biz.paluch.mavenupdater.dependencies.DependencyUpgrades;
import biz.paluch.mavenupdater.dependencies.VersionOption;
import org.jspecify.annotations.Nullable;

import com.intellij.openapi.ui.ComboBox;

/**
 * Table cell editor for the Suggested column: combobox of version options (with release dates and version-age icon).
 */
public class SuggestedVersionComboBoxEditor extends AbstractCellEditor implements TableCellEditor {

	private final ComboBox<VersionOption> combo = new ComboBox<>();

	private final DependencyUpgrades result;

	private final JTable table;

	public SuggestedVersionComboBoxEditor(DependencyUpgrades result, JTable table) {
		this.result = result;
		this.table = table;
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, @Nullable Object value, boolean isSelected, int row,
			int column) {

		int modelRow = this.table.convertRowIndexToModel(row);
		DependencyUpgrade info = modelRow >= 0 && modelRow < result.items().size() ? result.items().get(modelRow)
				: null;

		combo.removeAllItems();
		VersionOption selected = null;
		for (VersionOption opt : info.versionOptions()) {
			combo.addItem(opt);

			if (opt.getVersion().equals(value)) {
				selected = opt;
			}
		}
		combo.setRenderer(new VersionOptionCellRenderer(info.currentVersion()));

		if (selected != null) {
			combo.setSelectedItem(selected);
		}
		return combo;
	}

	@Override
	public @Nullable Object getCellEditorValue() {
		Object item = combo.getSelectedItem();
		if (item instanceof VersionOption vo) {
			return vo.getVersion();
		}
		return item != null ? item.toString() : "—";
	}


	public ComboBox<VersionOption> getCombo() {
		return combo;
	}
}
