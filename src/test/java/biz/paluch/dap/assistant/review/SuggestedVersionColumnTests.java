/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.assistant.review;

import java.awt.Component;
import java.awt.Insets;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.border.Border;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import com.intellij.icons.AllIcons;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.fixtures.TestCandidates.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the suggested-version column of {@link DependencyUpdateTable}:
 * {@link DependencyUpdateTable.SuggestedVersionComboBoxEditor} and
 * {@link DependencyUpdateTable.VersionOptionCellRenderer}.
 *
 * @author Mark Paluch
 */
class SuggestedVersionColumnTests {

	@Test
	void editorKeepsHorizontalCellPaddingWithoutUpgradeTargets() {

		TableRow candidate = new TableRow(candidate("com.example:demo:1.0.0"));
		assertThat(candidate.getUpgrade().hasUpgradeTargets()).isFalse();

		UpgradeReview review = new UpgradeReview(List.of(candidate), List.of());
		review.setHideUpToDate(false);

		DependencyUpdateTable.UpdateToColumn column = new DependencyUpdateTable.UpdateToColumn(review);
		ListTableModel<TableRow> model = new ListTableModel<>(column);
		model.setItems(review.getCandidates());

		Component component = column.getEditor(candidate).getTableCellEditorComponent(new JTable(model), null, false,
				0, 0);

		assertThat(component).isInstanceOfSatisfying(JComponent.class, it -> {
			Border border = it.getBorder();
			Insets insets = border.getBorderInsets(it);
			assertThat(insets.left).isGreaterThanOrEqualTo(JBUI.scale(6));
			assertThat(insets.right).isGreaterThanOrEqualTo(JBUI.scale(6));
		});
	}

	@Test
	void absentSelectedReleaseUsesWarningIntroductionIcon() {

		TableRow core = new TableRow(candidate("com.example:core:1.0.0",
				it -> it.releases("1.1.0").versionProperty("shared.version")));
		TableRow addon = new TableRow(candidate("com.example:addon:1.0.0",
				it -> it.versionProperty("shared.version")));
		UpgradeReview review = new UpgradeReview(core, addon);
		review.setVersion(core, ArtifactVersion.of("1.1.0"));
		Release selected = review.getSelectedRelease(addon);
		DependencyUpdateTable.VersionOptionCellRenderer renderer = new DependencyUpdateTable.VersionOptionCellRenderer();
		renderer.setCandidate(addon);

		renderer.getListCellRendererComponent(new JList<>(), selected, -1, false, false);

		assertThat(renderer.getIcon()).isEqualTo(AllIcons.Nodes.WarningIntroduction);
	}

}
