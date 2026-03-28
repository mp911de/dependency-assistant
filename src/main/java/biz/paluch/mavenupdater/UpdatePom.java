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

import biz.paluch.mavenupdater.artifact.ArtifactCoordinate;
import biz.paluch.mavenupdater.artifact.ArtifactVersion;
import biz.paluch.mavenupdater.artifact.DeclarationSource;
import biz.paluch.mavenupdater.artifact.DependencyUpdateOption;
import biz.paluch.mavenupdater.artifact.VersionSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

/**
 * Applies selected dependency and plugin version updates to a POM file according to the {@link VersionSource} and
 * {@link DeclarationSource}.
 */
class UpdatePom {

	private static final Logger LOG = Logger.getInstance(UpdatePom.class);

	private final Project project;

	public UpdatePom(Project project) {
		this.project = project;
	}

	/**
	 * Apply updates to the POM.
	 */
	public void applyUpdates(VirtualFile pomFile, List<DependencyUpdateOption> suggestions) {

		List<DependencyUpdate> updates = suggestions.stream().filter(s -> s.isApplyUpdate() && s.getUpdateTo() != null)
				.map(DependencyUpdate::of).toList();

		if (updates.isEmpty()) {
			return;
		}

		Document document = FileDocumentManager.getInstance().getDocument(pomFile);
		if (document != null) {
			PsiDocumentManager.getInstance(project).commitDocument(document);
		}

		Runnable updatePom = () -> {

			var psiFile = PsiManager.getInstance(project).findFile(pomFile);
			if (!(psiFile instanceof XmlFile file)) {
				LOG.warn("Cannot update POM: PSI file is not XmlFile for " + pomFile.getPath());
				return;
			}
			XmlTag root = file.getDocument() != null ? file.getDocument().getRootTag() : null;
			if (root == null || !MavenUtils.isMavenPomFile(file)) {
				return;
			}

			for (DependencyUpdate update : updates) {
				apply(root, update);
			}
		};

		ApplicationManager.getApplication().runWriteAction(() -> {
			CommandProcessor.getInstance().executeCommand(project, updatePom, MessageBundle.message("command.update.title"),
					null);
		});
	}

	private void apply(XmlTag projectTag, DependencyUpdate update) {

		String newVersion = update.version().toString();

		for (VersionSource source : update.versionSources()) {

			if (source instanceof VersionSource.VersionPropertySource vps) {
				if (source instanceof VersionSource.ProfilePropertySource profileProperty) {

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

	private void updateDeclaration(XmlTag projectTag, ArtifactCoordinate coordinate, DeclarationSource vds,
			String newVersion) {

		List<XmlTag> tags = findDependencyOrPluginTag(projectTag, coordinate, vds);

		for (XmlTag artifactTag : tags) {
			XmlTag versionTag = artifactTag.findFirstSubTag("version");
			if (versionTag != null) {
				setTagValue(versionTag, newVersion);
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
			setTagValue(prop, newVersion);
		}
	}

	private List<XmlTag> findDependencyOrPluginTag(XmlTag projectTag, ArtifactCoordinate coordinate,
			DeclarationSource source) {
		String groupId = coordinate.groupId();
		String artifactId = coordinate.artifactId();
		List<XmlTag> tags = new ArrayList<>();

		if (source instanceof DeclarationSource.Dependencies) {
			findAndCollect(projectTag.findFirstSubTag("dependencies"), groupId, artifactId, tags::add);
		}

		if (source instanceof DeclarationSource.DependencyManagement) {
			XmlTag dm = projectTag.findFirstSubTag("dependencyManagement");
			XmlTag deps = dm != null ? dm.findFirstSubTag("dependencies") : null;
			findAndCollect(deps, groupId, artifactId, tags::add);
		}

		if (source instanceof DeclarationSource.Plugins) {
			XmlTag build = projectTag.findFirstSubTag("build");
			XmlTag plugins = build != null ? build.findFirstSubTag("plugins") : null;
			findAndCollect(plugins, groupId, artifactId, tags::add);
		}

		if (source instanceof DeclarationSource.PluginManagement) {
			XmlTag build = projectTag.findFirstSubTag("build");
			XmlTag pm = build != null ? build.findFirstSubTag("pluginManagement") : null;
			XmlTag plugins = pm != null ? pm.findFirstSubTag("plugins") : null;
			findAndCollect(plugins, groupId, artifactId, tags::add);
		}

		if (source instanceof DeclarationSource.ProfileDependencies pd) {
			XmlTag profile = findProfile(projectTag, pd.getProfileId());
			XmlTag deps = profile != null ? profile.findFirstSubTag("dependencies") : null;
			findAndCollect(deps, groupId, artifactId, tags::add);
		}

		if (source instanceof DeclarationSource.ProfileDependencyManagement pd) {
			XmlTag profile = findProfile(projectTag, pd.getProfileId());
			XmlTag dm = profile != null ? profile.findFirstSubTag("dependencyManagement") : null;
			XmlTag deps = dm != null ? dm.findFirstSubTag("dependencies") : null;
			findAndCollect(deps, groupId, artifactId, tags::add);
		}

		if (source instanceof DeclarationSource.ProfilePlugins pp) {
			XmlTag profile = findProfile(projectTag, pp.getProfileId());
			XmlTag build = profile != null ? profile.findFirstSubTag("build") : null;
			XmlTag plugins = build != null ? build.findFirstSubTag("plugins") : null;
			findAndCollect(plugins, groupId, artifactId, tags::add);
		}

		if (source instanceof DeclarationSource.ProfilePluginManagement pp) {
			XmlTag profile = findProfile(projectTag, pp.getProfileId());
			XmlTag build = profile != null ? profile.findFirstSubTag("build") : null;
			XmlTag pm = build != null ? build.findFirstSubTag("pluginManagement") : null;
			XmlTag plugins = pm != null ? pm.findFirstSubTag("plugins") : null;
			findAndCollect(plugins, groupId, artifactId, tags::add);
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
			String id = idTag != null ? getTagText(idTag) : null;
			if (profileId.equals(id)) {
				return profile;
			}
		}
		return null;
	}

	private void findAndCollect(@Nullable XmlTag container, String groupId, String artifactId, Consumer<XmlTag> action) {

		if (container == null) {
			return;
		}

		for (XmlTag dep : container.getSubTags()) {
			String g = getSubTagText(dep, "groupId");
			String a = getSubTagText(dep, "artifactId");
			String v = getSubTagText(dep, "version");
			if (artifactId.equals(a) && (groupId == null || groupId.equals(g)) && StringUtils.hasText(v)) {
				action.accept(dep);
			}
		}
	}

	private static @Nullable String getSubTagText(XmlTag parent, String name) {
		XmlTag tag = parent.findFirstSubTag(name);
		return tag != null ? getTagText(tag) : null;
	}

	private static String getTagText(XmlTag tag) {
		String text = tag.getValue().getText();
		return StringUtils.hasText(text) ? text.trim() : "";
	}

	private static void setTagValue(XmlTag tag, String value) {
			tag.getValue().setText(value);
	}

	record DependencyUpdate(ArtifactCoordinate coordinate, ArtifactVersion version,
			Collection<DeclarationSource> declarationSources, Collection<VersionSource> versionSources) {

		public static DependencyUpdate of(DependencyUpdateOption option) {
			return new DependencyUpdate(option.coordinates(), option.getRequiredUpdateTo(),
					option.getCandidate().getDeclarationSources(), option.getCandidate().getVersionSources());
		}

	}
}
