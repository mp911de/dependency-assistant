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

import java.time.Duration;
import java.time.LocalDateTime;

import javax.swing.Icon;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.artifact.VersionAware;
import biz.paluch.dap.support.ReleaseDateFormatter;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.icons.AllIcons;
import org.jspecify.annotations.Nullable;

/**
 * Renderer for lookup elements that provide a {@link ArtifactRelease} object.
 * @author Mark Paluch
 */
public class ArtifactReleaseRenderer extends LookupElementRenderer<LookupElement> {

	private final ReleaseDateFormatter formatter = ReleaseDateFormatter.create();

	private final InterfaceAssistant assistant;

	private final @Nullable ArtifactVersion currentVersion;

	public ArtifactReleaseRenderer(InterfaceAssistant assistant, @Nullable ArtifactVersion currentVersion) {
		this.assistant = assistant;
		this.currentVersion = currentVersion;
	}

	public String formatReleaseDate(ArtifactRelease release) {
		LocalDateTime releaseDate = release.getReleaseDate();
		return releaseDate == null ? "" : formatter.format(releaseDate);
	}

	@Override
	public void renderElement(LookupElement element, LookupElementPresentation presentation) {

		if (!(element.getObject() instanceof ArtifactRelease release)) {
			return;
		}

		ArtifactVersion version = VersionAware.getVersion(release);
		presentation.setItemText(release.getVersion().toString());

		String typeText = "";
		LocalDateTime releaseDate = release.getReleaseDate();

		if (releaseDate != null) {
			Duration age = Duration.between(releaseDate, LocalDateTime.now());
			if (age.toDays() < 5) {
				presentation.setItemTextUnderlined(true);
			}
			typeText = formatReleaseDate(release);
		}

		if (release.getVersion() instanceof GitVersion gitVersion && StringUtils.hasText(gitVersion.getSha())) {
			typeText = gitVersion.getShortSha() + " " + typeText;
			presentation.setTypeText(typeText.trim(), AllIcons.Vcs.CommitNode);
		} else {
			presentation.setTypeText(typeText.trim());
		}

		if (currentVersion != null) {
			if (release.isOlder(currentVersion)) {
				presentation.setItemTextItalic(true);
			}
		}

		presentation.setIcon(getIcon(release.artifactId(), version));
	}

	private Icon getIcon(ArtifactId artifactId, ArtifactVersion version) {
		if (version.isPreview()) {
			return VersionAge.PREVIEW.getIcon();
		}
		if (currentVersion != null) {
			VersionAge versionAge = VersionAge.between(currentVersion, version);
			return versionAge.getIcon();
		}
		return assistant.getTableIcon(new Dependency(artifactId, currentVersion));
	}

}
