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

package biz.paluch.dap.maven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.DependencyUpdates;
import biz.paluch.dap.support.FileDependencyUpdater;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

/**
 * Applies selected dependency and plugin version updates to a POM file
 * according to the {@link VersionSource} and {@link DeclarationSource}.
 *
 * @author Mark Paluch
 */
class UpdatePomFile implements FileDependencyUpdater {

	private static final Logger LOG = Logger.getInstance(UpdatePomFile.class);

	private final MavenPomProperties propertyResolver;

	UpdatePomFile(MavenPomProperties propertyResolver) {
		this.propertyResolver = propertyResolver;
	}

	@Override
	public void applyUpdates(PsiFile file, DependencyUpdates updates) {

		if (!(file instanceof XmlFile pomFile)) {
			LOG.warn("Cannot update POM: PSI file is not XmlFile for " + file.getName());
			return;
		}
		XmlTag root = pomFile.getDocument() != null ? pomFile.getDocument().getRootTag() : null;
		if (root == null || !MavenUtils.isMavenPomFile(file)) {
			return;
		}

		record TagAndDeclaration(MavenPomSupport.PomTag tag, ArtifactDeclaration declaration) {

		}

		Map<ArtifactId, List<TagAndDeclaration>> index = new HashMap<>();
		MavenParser.doWithArtifacts(propertyResolver, pomFile, (tag, declaration) -> {
			index.computeIfAbsent(declaration.getArtifactId(), k -> new ArrayList<>())
					.add(new TagAndDeclaration(tag, declaration));
		});

		updates.updateAll(file, update -> {

			for (VersionSource source : update.versionSources()) {
				if (source instanceof VersionSource.VersionProperty versionProperty) {
					updateProperty(pomFile, update, versionProperty);
				} else {


					List<TagAndDeclaration> declarations = index.get(update.artifactId());
					if (declarations == null || declarations.isEmpty()) {
						return;
					}

					for (TagAndDeclaration declaration : declarations) {
						updateDeclaration(declaration.tag().getTag(), declaration.declaration(), update, source);
					}
				}
			}
		});
	}


	private void updateProperty(XmlFile file, DependencyUpdate update, VersionSource.VersionProperty versionProperty) {

		MavenPomSupport.doWithRoot(file, rootTag -> {

			if (versionProperty instanceof VersionSource.Profile profileProperty) {

				MavenPomSupport.doWithProfiles(MavenPomSupport.PomTag.of(rootTag), profile -> {

					String id = profile.getText("id");
					if (!profileProperty.getProfileId().equals(id)) {
						return;
					}

					XmlTag propertiesTag = profile.getTag().findFirstSubTag("properties");
					if (propertiesTag != null) {
						XmlTag propertyTag = propertiesTag.findFirstSubTag(versionProperty.getProperty());
						if (propertyTag != null) {
							applyUpdate(propertyTag, update);
						}
					}
				});
			} else {

				XmlTag propertiesTag = rootTag.findFirstSubTag("properties");
				if (propertiesTag != null) {
					XmlTag propertyTag = propertiesTag.findFirstSubTag(versionProperty.getProperty());
					if (propertyTag != null) {
						applyUpdate(propertyTag, update);
					}
				}
			}
		});
	}

	private void updateDeclaration(XmlTag owner, ArtifactDeclaration declaration, DependencyUpdate update,
			VersionSource source) {
		if (matches(declaration, update, source)) {
			XmlTag version = owner.findFirstSubTag("version");
			if (version != null) {
				applyUpdate(version, update);
			}
		}
	}

	/**
	 * Apply a single update at the given version literal. The literal must be the
	 * {@code <version>} XML tag value or a {@code <properties>}-child tag value of
	 * the same POM file. Other PSI elements are ignored.
	 *
	 * @param versionLiteral the version PSI element.
	 * @param update the update to apply.
	 */
	public void applyUpdate(PsiElement versionLiteral, DependencyUpdate update) {

		XmlTag versionTag = versionLiteral instanceof XmlTag tag ? tag
				: PsiTreeUtil.getParentOfType(versionLiteral, XmlTag.class);

		if (versionTag == null || !"version".equals(versionTag.getName()) && !isPropertiesChild(versionTag)) {
			return;
		}

		String value = update.to().toString();
		versionTag.getValue().setText(value);
	}

	private boolean matches(ArtifactDeclaration declaration, DependencyUpdate update, VersionSource source) {

		if (!update.artifactId().equals(declaration.getArtifactId())) {
			return false;
		}

		if (source instanceof VersionSource.VersionProperty property
				&& declaration.getVersionSource() instanceof VersionSource.VersionProperty candidate) {
			return property.getProperty().equals(candidate.getProperty())
					&& (!(property instanceof VersionSource.Profile expected)
							|| candidate instanceof VersionSource.Profile actual
									&& expected.getProfileId().equals(actual.getProfileId()));
		}

		// Declaration sources are identities (section, BOM import, profile id). A
		// root, profile, plain managed, or BOM entry is only rewritten by an update
		// that names that very source.
		if (source instanceof VersionSource.VersionDeclarationSource declaredBy) {
			return declaredBy.getDeclarationSource().equals(declaration.getDeclarationSource());
		}

		return source instanceof VersionSource.DeclaredVersion
				&& update.declarationSources().contains(declaration.getDeclarationSource());
	}

	private static boolean isPropertiesChild(XmlTag tag) {
		XmlTag parent = tag.getParentTag();
		return parent != null && "properties".equals(parent.getName());
	}

}
