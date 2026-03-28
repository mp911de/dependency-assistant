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

import biz.paluch.mavenupdater.artifact.DependencyUpdateOption;

import javax.swing.JTable;

import com.intellij.util.ui.SortableColumnModel;

/**
 * Utility methods for working with table models.
 *
 * @author Mark Paluch
 */
class ModelUtil {

	/**
	 * @param viewRow row index in view coordinates (e.g. from renderer/editor), respects row sorter
	 */
	static DependencyUpdateOption getOption(JTable table, int viewRow) {
		int modelRow = table.convertRowIndexToModel(viewRow);
		return (DependencyUpdateOption) ((SortableColumnModel) table.getModel()).getRowValue(modelRow);
	}
}
