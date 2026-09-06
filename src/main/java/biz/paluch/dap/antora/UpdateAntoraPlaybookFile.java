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

package biz.paluch.dap.antora;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.RefStyle;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.DependencyUpdates;
import biz.paluch.dap.support.FileDependencyUpdater;
import biz.paluch.dap.support.yaml.YamlVersionSite;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * Update the version in an Antora {@code ui.bundle.url}.
 * <p>Preserves the surrounding URL and YAML quote style. Callers provide write
 * action and command management as required by {@link FileDependencyUpdater}.
 *
 * @author Mark Paluch
 */
class UpdateAntoraPlaybookFile implements FileDependencyUpdater {

	private static final String RELEASE_DOWNLOAD_FRAGMENT = "/releases/download/";

	private final YAMLElementGenerator factory;

	UpdateAntoraPlaybookFile(Project project) {
		this.factory = new YAMLElementGenerator(project);
	}

	/**
	 * Apply updates with matching bundle identities and {@link GitVersion} targets.
	 * <p>Malformed URLs and other updates leave the declaration unchanged.
	 */
	@Override
	public void applyUpdates(PsiFile psiFile, DependencyUpdates updates) {

		SyntaxTraverser.psiTraverser(psiFile)
				.filter(YAMLKeyValue.class)
				.filter(AntoraPlaybookParser::isBundleUrlKeyValue)
				.filter(it -> it.isValid() && it.getValue() instanceof YAMLScalar)
				.map(it -> (YAMLScalar) it.getValue())
				.filter(YAMLScalar::isValid)
				.forEach(scalar -> applyUpdates(scalar, updates));
	}

	void applyUpdate(YAMLScalar scalar, DependencyUpdate update) {

		AntoraBundleUrl bundleUrl = AntoraBundleUrl.from(scalar.getTextValue());
		if (bundleUrl == null) {
			return;
		}

		if (!bundleUrl.toArtifactId().equals(update.artifactId())
				|| !(update.to() instanceof GitVersion gitVersion)) {
			return;
		}

		updateVersion(scalar, gitVersion);
	}

	void updateVersion(YAMLScalar scalar, GitVersion newVersion) {
		updateVersion(scalar, scalar.getTextValue(), newVersion);
	}

	/**
	 * Update the version using the original URL. Completion may have edited the
	 * live scalar, so the original preserves the surrounding asset path.
	 * @return the replacement scalar, or {@literal null} if no safe update is
	 * possible.
	 */
	@Nullable
	YAMLScalar updateVersion(YAMLScalar scalar, String value, GitVersion newVersion) {

		AntoraBundleUrl bundleUrl = AntoraBundleUrl.from(value);
		if (bundleUrl == null) {
			return null;
		}

		YamlVersionSite site = YamlVersionSite.locate(scalar, AntoraPlaybookParser::isBundleUrlKeyValue);
		if (site == null) {
			return null;
		}

		String renderedVersion = newVersion.renderRef(RefStyle.VERSION, bundleUrl.version());
		int versionStart = value.indexOf(RELEASE_DOWNLOAD_FRAGMENT) + RELEASE_DOWNLOAD_FRAGMENT.length();
		int versionEnd = value.indexOf('/', versionStart);
		String replacement = value.substring(0, versionStart) + renderedVersion + value.substring(versionEnd);

		YAMLKeyValue replaced = site.replaceRawValue(replacement, factory);
		return replaced.getValue() instanceof YAMLScalar updatedScalar ? updatedScalar : null;
	}

	private void applyUpdates(YAMLScalar scalar, DependencyUpdates updates) {

		AntoraBundleUrl bundleUrl = AntoraBundleUrl.from(scalar.getTextValue());
		if (bundleUrl == null) {
			return;
		}

		ArtifactId artifactId = bundleUrl.toArtifactId();

		updates.updateAll(scalar.getContainingFile(), update -> {
			if (!artifactId.equals(update.artifactId()) || !(update.to() instanceof GitVersion gitVersion)) {
				return;
			}
			updateVersion(scalar, gitVersion);
		});
	}

}
