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

import biz.paluch.mavenupdater.dependencies.ArtifactVersion;

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import biz.paluch.mavenupdater.dependencies.DependencyUpgrade;
import biz.paluch.mavenupdater.dependencies.DependencyUpgrades;
import biz.paluch.mavenupdater.dependencies.VersionAge;
import org.jspecify.annotations.Nullable;

/**
 * Table cell renderer for Suggested column: shows version-age icon + text when combobox is collapsed.
 */
public class SuggestedVersionTableCellRenderer extends DefaultTableCellRenderer {

	private final DependencyUpgrades result;

	private final JTable table;

	public SuggestedVersionTableCellRenderer(DependencyUpgrades result, JTable table) {
		this.result = result;
		this.table = table;
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		int modelRow = this.table.convertRowIndexToModel(row);
		DependencyUpgrade info = modelRow >= 0 && modelRow < result.items().size() ? result.items().get(modelRow)
				: null;

		setText(value != null ? value.toString() : "");
		if (info.currentVersion() != null && value instanceof ArtifactVersion version) {
			Icon icon = VersionAge.fromVersions(info.currentVersion(), version).getIcon();
			setIcon(icon);
		}
		return this;
	}

}
