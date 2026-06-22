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
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jspecify.annotations.Nullable;

/**
 * Applies selected dependency and plugin version updates to a POM file
 * according to the {@link VersionSource} and {@link DeclarationSource}.
 */
class UpdatePomFile {

	private static final Logger LOG = Logger.getInstance(UpdatePomFile.class);

	private final PropertyResolver propertyResolver;

	public UpdatePomFile(PropertyResolver propertyResolver) {
		this.propertyResolver = propertyResolver;
	}

	/**
	 * Apply updates to the POM.
	 */
	public void applyUpdates(PsiFile pomFile, List<DependencyUpdate> updates) {

		if (!(pomFile instanceof XmlFile file)) {
			LOG.warn("Cannot update POM: PSI file is not XmlFile for " + pomFile.getName());
			return;
		}
		XmlTag root = file.getDocument() != null ? file.getDocument().getRootTag() : null;
		if (root == null || !MavenUtils.isMavenPomFile(file)) {
			return;
		}

		for (DependencyUpdate update : updates) {
			apply(root, update);
		}
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

	private void apply(XmlTag projectTag, DependencyUpdate update) {

		String newVersion = update.version().toString();

		for (VersionSource source : update.versionSources()) {

			if (source instanceof VersionSource.VersionProperty vps) {
				if (source instanceof VersionSource.Profile profileProperty) {
					XmlTag profile = findProfile(projectTag, profileProperty.getProfileId());
					if (profile != null) {
						updateProperty(profile, vps.getProperty(), newVersion);
					}
				} else {
					updateProperty(projectTag, vps.getProperty(), newVersion);
				}
			}

			if (source instanceof VersionSource.VersionDeclarationSource vds) {
				updateDeclaration(projectTag, update.coordinate(), vds.getDeclarationSource(), newVersion);
			}

			if (source instanceof VersionSource.DeclaredVersion) {
				for (DeclarationSource declarationSource : update.declarationSources()) {
					updateDeclaration(projectTag, update.coordinate(), declarationSource, newVersion);
				}
			}
		}
	}

	private void updateDeclaration(XmlTag projectTag, ArtifactId coordinate, DeclarationSource declarationSource,
			String newVersion) {

		ArtifactIdPredicate predicate = (groupId, artifactId) -> coordinate.artifactId().equals(artifactId)
				&& coordinate.groupId()
						.equals(groupId);

		List<XmlTag> tags = findDependencyOrPluginTag(projectTag, predicate, declarationSource);

		for (XmlTag artifactTag : tags) {
			XmlTag versionTag = artifactTag.findFirstSubTag("version");
			if (versionTag != null) {
				versionTag.getValue().setText(newVersion);
			}
		}
	}

	private void updateProperty(XmlTag parent, String propertyName, String newVersion) {
		XmlTag properties = parent.findFirstSubTag("properties");
		if (properties == null) {
			properties = parent.createChildTag("properties", parent.getNamespace(), "", false);
			parent.addSubTag(properties, false);
		}
		XmlTag prop = properties.findFirstSubTag(propertyName);
		if (prop != null) {
			prop.getValue().setText(newVersion);
		}
	}

	private List<XmlTag> findDependencyOrPluginTag(XmlTag projectTag, ArtifactIdPredicate predicate,
			DeclarationSource source) {

		List<XmlTag> tags = new ArrayList<>();
		XmlTag searchRoot = source instanceof DeclarationSource.Profile inProfile
				? findProfile(projectTag, inProfile.getProfileId())
				: projectTag;

		if (searchRoot == null) {
			return tags;
		}

		XmlTag parent = projectTag.findFirstSubTag("parent");
		XmlTag build = searchRoot.findFirstSubTag("build");
		if (MavenParser.isParentDependencyCandidate(searchRoot, parent)) {
			doWithTag(parent, predicate, tags::add);
		}

		if (source instanceof DeclarationSource.Dependency && !(source instanceof DeclarationSource.Managed)) {
			findAndCollect(searchRoot.findFirstSubTag("dependencies"), predicate, tags::add);
		}

		if (source instanceof DeclarationSource.Dependency && source instanceof DeclarationSource.Managed) {
			XmlTag dm = searchRoot.findFirstSubTag("dependencyManagement");
			findAndCollect(dm != null ? dm.findFirstSubTag("dependencies") : null, predicate, tags::add);
		}

		if (source instanceof DeclarationSource.Plugin && !(source instanceof DeclarationSource.Managed)) {

			findAndCollect(build != null ? build.findFirstSubTag("plugins") : null, predicate, tags::add);
			findAndCollect(build != null ? build.findFirstSubTag("extensions") : null, predicate, tags::add);

			XmlTag reporting = searchRoot.findFirstSubTag("reporting");
			findAndCollect(reporting != null ? reporting.findFirstSubTag("plugins") : null, predicate, tags::add);
		}

		if (source instanceof DeclarationSource.Plugin && source instanceof DeclarationSource.Managed) {
			XmlTag pm = build != null ? build.findFirstSubTag("pluginManagement") : null;
			findAndCollect(pm != null ? pm.findFirstSubTag("plugins") : null, predicate, tags::add);
		}

		return tags;
	}

	private @Nullable XmlTag findProfile(XmlTag projectTag, @Nullable String profileId) {

		XmlTag profiles = projectTag.findFirstSubTag("profiles");
		if (profiles == null || profileId == null) {
			return null;
		}

		for (XmlTag profile : profiles.getSubTags()) {
			if (!"profile".equals(profile.getLocalName())) {
				continue;
			}
			XmlTag idTag = profile.findFirstSubTag("id");
			String id;
			id = idTag != null ? idTag.getValue().getText() : null;
			if (profileId.equals(id)) {
				return profile;
			}
		}

		return null;
	}

	private void findAndCollect(@Nullable XmlTag container, ArtifactIdPredicate predicate,
			Consumer<XmlTag> action) {
		if (container == null) {
			return;
		}
		for (XmlTag dep : container.getSubTags()) {
			doWithTag(dep, predicate, action);
		}
	}

	private void doWithTag(XmlTag tag, ArtifactIdPredicate predicate, Consumer<XmlTag> action) {

		String g = getSubTagText(tag, "groupId");
		String a = getSubTagText(tag, "artifactId");
		String v = getSubTagText(tag, "version");

		if (StringUtils.isEmpty(a) || StringUtils.isEmpty(g) || StringUtils.isEmpty(v)) {
			return;
		}

		String resolvedGroupId = propertyResolver.resolvePlaceholders(g);
		String resolvedArtifactId = propertyResolver.resolvePlaceholders(a);

		if (predicate.test(resolvedGroupId, resolvedArtifactId)) {
			action.accept(tag);
		}
	}

	private static @Nullable String getSubTagText(XmlTag parent, String name) {
		XmlTag tag = parent.findFirstSubTag(name);
		return tag != null ? tag.getValue().getText() : null;
	}

	private static boolean isPropertiesChild(XmlTag tag) {
		XmlTag parent = tag.getParentTag();
		return parent != null && "properties".equals(parent.getName());
	}

	/**
	 * Represents a predicate (boolean-valued function) for a {@code groupId} and
	 * {@code artifactId}.
	 */
	@FunctionalInterface
	interface ArtifactIdPredicate {

		/**
		 * Evaluates this predicate on the given argument.
		 *
		 * @param groupId the input group Id.
		 * @param artifactId the input artifact Id.
		 * @return {@code true} if the input argument matches the predicate, otherwise
		 * {@code false}
		 */
		boolean test(String groupId, String artifactId);

	}

}
