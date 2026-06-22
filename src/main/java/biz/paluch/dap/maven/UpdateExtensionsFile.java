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

package biz.paluch.dap.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.VersionSource;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jspecify.annotations.Nullable;

/**
 * Applies selected dependency and plugin version updates to an extensions file
 * according to the {@link VersionSource} and {@link DeclarationSource}.
 */
class UpdateExtensionsFile {

	private static final Logger LOG = Logger.getInstance(UpdateExtensionsFile.class);

	/**
	 * Apply updates to the extensions file.
	 */
	public void applyUpdates(PsiFile extensionsFile, List<DependencyUpdate> updates) {

		if (!(extensionsFile instanceof XmlFile file)) {
			LOG.warn("Cannot update Extensions file: PSI file is not XmlFile for " + extensionsFile.getName());
			return;
		}
		XmlTag root = file.getDocument() != null ? file.getDocument().getRootTag() : null;
		if (root == null || !MavenUtils.isMavenExtensionsFile(file)) {
			return;
		}

		for (DependencyUpdate update : updates) {
			apply(root, update);
		}
	}

	/**
	 * Apply a single update at the given version literal. The literal must be the
	 * {@code <version>} XML tag.
	 * @param versionLiteral the version PSI element.
	 * @param update the update to apply.
	 */
	public void applyUpdate(PsiElement versionLiteral, DependencyUpdate update) {

		XmlTag versionTag = versionLiteral instanceof XmlTag tag ? tag
				: PsiTreeUtil.getParentOfType(versionLiteral, XmlTag.class);

		if (versionTag == null || !"version".equals(versionTag.getName())) {
			return;
		}

		String value = update.version().toString();
		versionTag.getValue().setText(value);
	}

	private void apply(XmlTag root, DependencyUpdate update) {

		String newVersion = update.version().toString();
		updateDeclaration(root, update.coordinate(), newVersion);
	}

	private void updateDeclaration(XmlTag projectTag, ArtifactId coordinate, String newVersion) {

		List<XmlTag> tags = findExtension(projectTag, coordinate);

		for (XmlTag artifactTag : tags) {
			XmlTag versionTag = artifactTag.findFirstSubTag("version");
			if (versionTag != null) {
				versionTag.getValue().setText(newVersion);
			}
		}
	}

	private List<XmlTag> findExtension(XmlTag extensions, ArtifactId coordinate) {

		String groupId = coordinate.groupId();
		String artifactId = coordinate.artifactId();
		List<XmlTag> tags = new ArrayList<>();

		findAndCollect(extensions, groupId, artifactId, tags::add);
		return tags;
	}

	private void findAndCollect(@Nullable XmlTag container, String groupId, String artifactId,
			Consumer<XmlTag> action) {

		if (container == null) {
			return;
		}

		for (XmlTag dep : container.getSubTags()) {
			String g = getSubTagText(dep, "groupId");
			String a = getSubTagText(dep, "artifactId");
			if (artifactId.equals(a) && groupId.equals(g)) {
				action.accept(dep);
			}
		}
	}

	private static @Nullable String getSubTagText(XmlTag parent, String name) {
		XmlTag tag = parent.findFirstSubTag(name);
		return tag != null ? tag.getValue().getText() : null;
	}

}
