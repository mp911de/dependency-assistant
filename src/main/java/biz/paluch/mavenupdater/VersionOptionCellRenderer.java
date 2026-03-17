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

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import biz.paluch.mavenupdater.dependencies.VersionAge;
import biz.paluch.mavenupdater.dependencies.VersionOption;
import org.jspecify.annotations.Nullable;

/**
 * List cell renderer that shows an icon (older / newer patch / minor / major) plus version text.
 */
public class VersionOptionCellRenderer extends JLabel implements ListCellRenderer<VersionOption> {

	private final @Nullable ArtifactVersion currentVersion;

	public VersionOptionCellRenderer(@Nullable ArtifactVersion currentVersion) {
		this.currentVersion = currentVersion;
	}

	@Override
	public Component getListCellRendererComponent(JList<? extends VersionOption> list, @Nullable VersionOption value,
			int index, boolean isSelected, boolean cellHasFocus) {
		setText(value != null ? toString(value) : "");
		setIcon(
				value != null && currentVersion != null ? VersionAge.fromVersions(currentVersion, value.getVersion()).getIcon()
						: null);
		return this;
	}

	private String toString(VersionOption value) {
		return value.getVersion() + (value.getReleaseDate() != null ? " (" + value.getReleaseDate().toLocalDate() + ")" : "");
	}

}
