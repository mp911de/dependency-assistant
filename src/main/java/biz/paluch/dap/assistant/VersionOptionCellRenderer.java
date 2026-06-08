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

import javax.swing.JList;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.support.ReleaseDateFormatter;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import org.jspecify.annotations.Nullable;

/**
 * List cell renderer that shows an icon (older / newer patch / minor / major)
 * plus version text, graying out versions that do not satisfy the dependency
 * rule.
 */
class VersionOptionCellRenderer extends ColoredListCellRenderer<Release> {

	private final UpdateCandidate candidate;

	private final ArtifactVersion currentVersion;

	private final ReleaseDateFormatter formatter = ReleaseDateFormatter.create();

	/**
	 * Create a renderer that classifies options relative to the current version.
	 */
	public VersionOptionCellRenderer(UpdateCandidate candidate, ArtifactVersion currentVersion) {
		this.candidate = candidate;
		this.currentVersion = currentVersion;
		setIconTextGap(JBUI.scale(4));
		setBorder(JBUI.Borders.empty());
	}

	@Override
	protected void customizeCellRenderer(JList<? extends Release> list, @Nullable Release value, int index,
			boolean selected, boolean hasFocus) {

		if (value == null) {
			return;
		}

		String text = value.getVersion().getVersion().toString();
		if (value.releaseDate() != null) {
			text += " (" + formatter.format(value.releaseDate()) + ")";
		}

		boolean valid = candidate.rule().test(value.getVersion());
		append(text, valid ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);

		if (!valid) {
			setIcon(DependencyAssistantIcons.DEPENDENCY_RULE_WARN);
		}
		else {
			setIcon(VersionAge.between(currentVersion, value.getVersion()).getIcon());
		}
	}

}
