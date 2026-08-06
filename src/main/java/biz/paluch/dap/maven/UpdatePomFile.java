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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeResult;
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
class UpdatePomFile {

	private static final Logger LOG = Logger.getInstance(UpdatePomFile.class);

	private final MavenPomProperties propertyResolver;

	UpdatePomFile(MavenPomProperties propertyResolver) {
		this.propertyResolver = propertyResolver;
	}

	/**
	 * Apply updates to the POM.
	 */
	public UpgradeResult applyUpdates(PsiFile pomFile, List<DependencyUpdate> updates) {

		if (!(pomFile instanceof XmlFile file)) {
			LOG.warn("Cannot update POM: PSI file is not XmlFile for " + pomFile.getName());
			return UpgradeResult.none();
		}
		XmlTag root = file.getDocument() != null ? file.getDocument().getRootTag() : null;
		if (root == null || !MavenUtils.isMavenPomFile(file)) {
			return UpgradeResult.none();
		}

		String before = file.getText();
		MavenParser parser = new MavenParser(propertyResolver);
		List<ArtifactDeclaration> declarations = parser.parsePomFile(file);
		Map<ArtifactId, List<ArtifactDeclaration>> index = new HashMap<>();
		for (ArtifactDeclaration d : declarations) {
			index.computeIfAbsent(d.getArtifactId(), k -> new ArrayList<>()).add(d);
		}

		for (DependencyUpdate update : updates) {
			List<ArtifactDeclaration> artifactDeclarations = index.get(update.artifactId());
			if (artifactDeclarations == null) {
				continue;
			}
			for (ArtifactDeclaration declaration : artifactDeclarations) {
				apply(declaration, update);
			}
		}

		return before.equals(file.getText()) ? UpgradeResult.none() : UpgradeResult.changed();
	}

	/**
	 * Apply a single update at the given version literal. The literal must be the
	 * {@code <version>} XML tag value or a {@code <properties>}-child tag value of
	 * the same POM file.
	 * @param versionLiteral the version PSI element.
	 * @param update the update to apply.
	 */
	public void applyUpdate(PsiElement versionLiteral, DependencyUpdate update) {

		XmlTag versionTag = versionLiteral instanceof XmlTag tag ? tag
				: PsiTreeUtil.getParentOfType(versionLiteral, XmlTag.class);

		if (versionTag == null || !"version".equals(versionTag.getName()) && !isPropertiesChild(versionTag)) {
			return;
		}

		String value = update.version().toString();
		versionTag.getValue().setText(value);
	}

	private void apply(ArtifactDeclaration declaration, DependencyUpdate update) {

		Set<PsiElement> updated = new HashSet<>();
		for (VersionSource source : update.versionSources()) {
			PsiElement literal = declaration.getVersionLiteral();
			if (literal != null && matches(declaration, update, source) && updated.add(literal)) {
				applyUpdate(literal, update);
			}
		}
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

		if (source instanceof VersionSource.VersionDeclarationSource declaredBy) {
			return declarationSourceMatches(declaredBy.getDeclarationSource(), declaration.getDeclarationSource());
		}

		return source instanceof VersionSource.DeclaredVersion
				&& update.declarationSources().stream()
						.anyMatch(expected -> declarationSourceMatches(expected, declaration.getDeclarationSource()));
	}

	private static boolean declarationSourceMatches(DeclarationSource expected, DeclarationSource actual) {

		if (expected.equals(actual)) {
			return true;
		}
		if (expected instanceof DeclarationSource.Managed && !(actual instanceof DeclarationSource.Managed)
				|| expected instanceof DeclarationSource.Plugin && !(actual instanceof DeclarationSource.Plugin)
				|| expected instanceof DeclarationSource.Dependency
						&& !(actual instanceof DeclarationSource.Dependency)) {
			return false;
		}
		if (expected instanceof DeclarationSource.Profile expectedProfile) {
			return actual instanceof DeclarationSource.Profile actualProfile
					&& expectedProfile.getProfileId().equals(actualProfile.getProfileId());
		}
		return expected instanceof DeclarationSource.Managed && actual instanceof DeclarationSource.Managed;
	}

	private static boolean isPropertiesChild(XmlTag tag) {
		XmlTag parent = tag.getParentTag();
		return parent != null && "properties".equals(parent.getName());
	}

}
