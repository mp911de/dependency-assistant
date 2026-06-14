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

import javax.swing.Icon;
import javax.swing.JTable;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionAge;
import com.intellij.util.ui.SortableColumnModel;
import org.jspecify.annotations.Nullable;

/**
 * Utility methods for working with table models.
 *
 * @author Mark Paluch
 */
class ModelUtil {

	/**
	 * Return the {@link UpgradeCandidate} row backing the given view row.
	 * @param viewRow row index in view coordinates (e.g. from renderer/editor),
	 * respects row sorter.
	 */
	static UpgradeCandidate getRow(JTable table, int viewRow) {
		int modelRow = table.convertRowIndexToModel(viewRow);
		return (UpgradeCandidate) ((SortableColumnModel) table.getModel()).getRowValue(modelRow);
	}

	/**
	 * Obtain the icon for the version option (dialog or completion.
	 * @param rule the evaluated rule.
	 * @param currentVersion current artifact version.
	 * @param candidate candidate version.
	 * @return the icon to use.
	 */
	public static Icon getIcon(EvaluatedDependencyRule rule, @Nullable ArtifactVersion currentVersion,
			ArtifactVersion candidate) {

		if (!rule.test(candidate)) {
			return DependencyAssistantIcons.DEPENDENCY_RULE_WARN;
		}

		if (currentVersion == null) {
			if (rule.isPresent() && rule.isLocked()) {
				return VersionAge.RULE_COMPLIANT;
			}

			if (candidate.isPreview()) {
				return VersionAge.PREVIEW.getIcon();
			}
			return VersionAge.OLDER.getIcon();
		}

		VersionAge age = VersionAge.between(currentVersion, candidate.getVersion());
		if (rule.isPresent() && age == VersionAge.OLDER && rule.isLocked()) {
			return VersionAge.RULE_COMPLIANT;
		}

		if (candidate.isPreview()) {
			return VersionAge.PREVIEW.getIcon();
		}

		VersionAge versionAge = VersionAge.between(currentVersion, candidate);
		return versionAge.getIcon();
	}
}
