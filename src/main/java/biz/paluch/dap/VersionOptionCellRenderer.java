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

package biz.paluch.dap;

import java.awt.*;
import javax.swing.*;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.support.ReleaseDateFormatter;
import com.intellij.util.ui.JBUI;
import org.jspecify.annotations.Nullable;

/**
 * List cell renderer that shows an icon (older / newer patch / minor / major)
 * plus version text.
 */
class VersionOptionCellRenderer extends JLabel implements ListCellRenderer<Release> {

	private final ArtifactVersion currentVersion;

	private final ReleaseDateFormatter formatter = ReleaseDateFormatter.create();

	/**
	 * Create a renderer that classifies options relative to the current version.
	 */
	public VersionOptionCellRenderer(ArtifactVersion currentVersion) {
		this.currentVersion = currentVersion;
		setIconTextGap(JBUI.scale(4));
		setBorder(JBUI.Borders.empty());
	}

	@Override
	public Component getListCellRendererComponent(JList<? extends Release> list, @Nullable Release value,
			int index, boolean isSelected, boolean cellHasFocus) {

		if (value == null) {
			setText("");
		} else {
			String text = value.getVersion().getVersion().toString();
			if (value.releaseDate() != null) {
				text += " (" + formatter.format(value.releaseDate()) + ")";
			}
			setText(text);
		}

		setIcon(value != null ? VersionAge.between(currentVersion, value).getIcon() : null);
		return this;
	}

}
