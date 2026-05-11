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

package biz.paluch.dap.antora;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.RefStyle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLScalar;

/**
 * PSI updater for Antora playbook {@code ui.bundle.url} declarations.
 *
 * <p>Applies dependency updates by rewriting only the version segment between
 * {@code /releases/download/} and the next path separator. Host, owner,
 * repository, asset path, YAML quoting style, and unrelated content are left
 * untouched.
 *
 * @author Mark Paluch
 */
class UpdateAntoraPlaybookFile {

	private static final String RELEASE_DOWNLOAD_FRAGMENT = "/releases/download/";

	private final YAMLElementGenerator factory;

	/**
	 * Create a new {@code UpdateAntoraPlaybookFile}.
	 * @param project the IntelliJ project that owns the write action.
	 */
	UpdateAntoraPlaybookFile(Project project) {
		this.factory = new YAMLElementGenerator(project);
	}

	/**
	 * Apply matching dependency updates to the given Antora playbook file.
	 * <p>Only the {@code ui.bundle.url} value's version segment is changed.
	 * Declarations without a matching update are left as-is.
	 * @param psiFile the Antora playbook PSI file.
	 * @param updates the dependency updates to apply.
	 */
	void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {

		SyntaxTraverser.psiTraverser(psiFile)
				.filter(YAMLKeyValue.class)
				.filter(AntoraPlaybookParser::isBundleUrlKeyValue)
				.filter(it -> it.isValid() && it.getValue() instanceof YAMLScalar)
				.map(it -> (YAMLScalar) it.getValue())
				.filter(YAMLScalar::isValid)
				.forEach(scalar -> applyUpdates(updates, scalar));
	}

	/**
	 * Apply a single update at the given Antora playbook scalar.
	 * @param scalar the {@code ui.bundle.url} scalar containing the URL.
	 * @param update the update to apply.
	 */
	void applyUpdate(YAMLScalar scalar, DependencyUpdate update) {

		AntoraBundleUrl bundleUrl = AntoraBundleUrl.from(scalar.getTextValue());
		if (bundleUrl == null) {
			return;
		}

		if (!bundleUrl.toArtifactId().equals(update.coordinate())
				|| !(update.version() instanceof GitVersion gitVersion)) {
			return;
		}

		updateVersion(scalar, gitVersion);
	}

	/**
	 * Update an Antora playbook {@code ui.bundle.url} scalar with the version
	 * rendered from the given Git release.
	 * @param scalar the scalar containing a parseable bundle URL.
	 * @param newVersion the resolved release version to render.
	 */
	void updateVersion(YAMLScalar scalar, GitVersion newVersion) {

		String value = scalar.getTextValue();
		AntoraBundleUrl bundleUrl = AntoraBundleUrl.from(value);
		if (bundleUrl == null) {
			return;
		}

		if (!(scalar.getParent() instanceof YAMLKeyValue keyValue)) {
			return;
		}

		String renderedVersion = newVersion.renderRef(RefStyle.VERSION, bundleUrl.version());
		int versionStart = value.indexOf(RELEASE_DOWNLOAD_FRAGMENT) + RELEASE_DOWNLOAD_FRAGMENT.length();
		int versionEnd = value.indexOf('/', versionStart);
		String replacement = value.substring(0, versionStart) + renderedVersion + value.substring(versionEnd);

		YAMLKeyValue ykv;
		if (scalar instanceof YAMLQuotedText quoted) {
			String quote = quoted.isSingleQuote() ? "'" : "\"";
			ykv = factory.createYamlKeyValue(keyValue.getKeyText(), quote + replacement + quote);
		} else {
			ykv = factory.createYamlKeyValue(keyValue.getKeyText(), replacement);
		}

		keyValue.replace(ykv);
	}

	private void applyUpdates(List<DependencyUpdate> updates, YAMLScalar scalar) {

		AntoraBundleUrl bundleUrl = AntoraBundleUrl.from(scalar.getTextValue());
		if (bundleUrl == null) {
			return;
		}

		ArtifactId artifactId = bundleUrl.toArtifactId();

		for (DependencyUpdate update : updates) {

			if (!artifactId.equals(update.coordinate()) || !(update.version() instanceof GitVersion gitVersion)) {
				continue;
			}

			updateVersion(scalar, gitVersion);
			break;
		}
	}

}
