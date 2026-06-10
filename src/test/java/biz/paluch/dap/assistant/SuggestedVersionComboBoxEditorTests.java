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
import java.awt.Insets;
import java.util.List;

import javax.swing.JTable;
import javax.swing.border.Border;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.rule.DependencyRule;
import com.intellij.util.ui.JBUI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SuggestedVersionComboBoxEditor}.
 *
 * @author Mark Paluch
 */
class SuggestedVersionComboBoxEditorTests {

	@Test
	void editorKeepsHorizontalCellPaddingWithoutUpgradeTargets() {

		UpdateCandidate candidate = candidateWithoutUpgradeTargets();
		DependencyUpgradeReview review = new DependencyUpgradeReview(List.of(candidate), List.of());
		SuggestedVersionComboBoxEditor editor = new SuggestedVersionComboBoxEditor(review, candidate);

		Component component = editor.getTableCellEditorComponent(new JTable(1, 1), null, false, 0, 0);

		assertThat(candidate.option().hasUpgradeTargets()).isFalse();
		assertThat(component).isInstanceOfSatisfying(javax.swing.JComponent.class, it -> {
			Border border = it.getBorder();
			Insets insets = border.getBorderInsets(it);
			assertThat(insets.left).isGreaterThanOrEqualTo(JBUI.scale(6));
			assertThat(insets.right).isGreaterThanOrEqualTo(JBUI.scale(6));
		});
	}

	private static UpdateCandidate candidateWithoutUpgradeTargets() {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		Dependency dependency = new Dependency(ArtifactId.of("com.example", "demo"), current);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		DependencyUpdateCandidate option = new DependencyUpdateCandidate(dependency, List.of(Release.of(current)));
		return new UpdateCandidate(option, new TestInterfaceAssistant(), DeclaredVersions.none(),
				DependencyRule.absent());
	}

}
