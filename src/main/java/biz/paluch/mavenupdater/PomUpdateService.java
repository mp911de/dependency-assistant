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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import biz.paluch.mavenupdater.dependencies.DependencySource;
import biz.paluch.mavenupdater.dependencies.DependencyUpgrade;
import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

/**
 * Applies selected dependency and plugin version upgrades to a POM file. Updates property if version is a property
 * reference; otherwise updates the version element in dependencyManagement/pluginManagement or in place.
 */
public class PomUpdateService {

	private final Project project;

	public PomUpdateService(Project project) {
		this.project = project;
	}

	/**
	 * Apply upgrades to the POM: for each suggestion with doUpgrade and upgradeTo set, update the version (via property,
	 * management section, or direct).
	 */
	public void applyUpgrades(VirtualFile pomFile, List<DependencyUpgrade> suggestions) {
		List<DependencyUpgrade> upgrades = suggestions.stream().filter(s -> s.isDoUpgrade() && s.getUpgradeTo() != null)
				.toList();
		if (upgrades.isEmpty()) {
			return;
		}
		var document = FileDocumentManager.getInstance().getDocument(pomFile);
		if (document != null) {
			PsiDocumentManager.getInstance(project).commitDocument(document);
		}

		ApplicationManager.getApplication().runWriteAction(() -> {

			CommandProcessor.getInstance().executeCommand(project, () -> {

				XmlFile file = (XmlFile) PsiManager.getInstance(project).findFile(pomFile);
				XmlTag root = file != null && file.getDocument() != null ? file.getDocument().getRootTag() : null;
				if (file == null || !MavenUtils.isMavenPomFile(project, file)) {
					return;
				}

				for (DependencyUpgrade upgrade : upgrades) {
					apply(root, upgrade);
				}
			}, MessageBundle.message("command.update.title"), null);
		});
	}

	private void apply(XmlTag projectTag, DependencyUpgrade s) {

		String newVersion = s.getUpgradeTo().toString();
		List<XmlTag> tags = findDependencyOrPluginTag(projectTag, s);

		for (XmlTag artifactTag : tags) {

			XmlTag versionTag = artifactTag.findFirstSubTag("version");
			String currentValue = versionTag != null ? getTagText(versionTag) : null;

			if (currentValue != null && currentValue.startsWith("${") && currentValue.endsWith("}")) {
				String propertyName = currentValue.substring(2, currentValue.length() - 1);
				updateProperty(projectTag, propertyName, newVersion);
				return;
			}
			if (versionTag != null) {
				setTagValue(versionTag, newVersion);
			} else {
				addVersionTag(artifactTag, newVersion);
			}
		}

	}

	private void updateProperty(XmlTag projectTag, String propertyName, String newVersion) {
		XmlTag properties = projectTag.findFirstSubTag("properties");
		if (properties == null) {
			properties = projectTag.createChildTag("properties", projectTag.getNamespace(), "", false);
			projectTag.addSubTag(properties, false);
		}
		XmlTag prop = properties.findFirstSubTag(propertyName);
		if (prop != null) {
			setTagValue(prop, newVersion);
		} else {
			XmlTag newProp = properties.createChildTag(propertyName, properties.getNamespace(), newVersion, false);
			properties.addSubTag(newProp, false);
		}
	}

	private List<XmlTag> findDependencyOrPluginTag(XmlTag projectTag, DependencyUpgrade s) {
		DependencySource src = s.source();
		String groupId = s.coordinates().groupId();
		String artifactId = s.coordinates().artifactId();
		List<XmlTag> tags = new ArrayList<>();

		if (src instanceof DependencySource.Dependencies) {
			findAndCollect(projectTag.findFirstSubTag("dependencies"), groupId, artifactId, tags::add);
		}
		if (src instanceof DependencySource.DependencyManagement) {
			XmlTag dm = projectTag.findFirstSubTag("dependencyManagement");
			XmlTag deps = dm != null ? dm.findFirstSubTag("dependencies") : null;
			findAndCollect(deps, groupId, artifactId, tags::add);
		}
		if (src instanceof DependencySource.Plugins) {
			XmlTag build = projectTag.findFirstSubTag("build");
			XmlTag plugins = build != null ? build.findFirstSubTag("plugins") : null;
			findAndCollect(plugins, groupId, artifactId, tags::add);
		}
		if (src instanceof DependencySource.PluginManagement) {
			XmlTag build = projectTag.findFirstSubTag("build");
			XmlTag pm = build != null ? build.findFirstSubTag("pluginManagement") : null;
			XmlTag plugins = pm != null ? pm.findFirstSubTag("plugins") : null;
			findAndCollect(plugins, groupId, artifactId, tags::add);
		}
		if (src instanceof DependencySource.ProfileDependencies pd) {
			XmlTag profile = findProfile(projectTag, pd.getProfileId());
			XmlTag deps = profile != null ? profile.findFirstSubTag("dependencies") : null;
			findAndCollect(deps, groupId, artifactId, tags::add);
		}
		if (src instanceof DependencySource.ProfileDependencyManagement pd) {
			XmlTag profile = findProfile(projectTag, pd.getProfileId());
			XmlTag dm = profile != null ? profile.findFirstSubTag("dependencyManagement") : null;
			XmlTag deps = dm != null ? dm.findFirstSubTag("dependencies") : null;
			findAndCollect(deps, groupId, artifactId, tags::add);
		}
		if (src instanceof DependencySource.ProfilePlugins pp) {
			XmlTag profile = findProfile(projectTag, pp.getProfileId());
			XmlTag build = profile != null ? profile.findFirstSubTag("build") : null;
			XmlTag plugins = build != null ? build.findFirstSubTag("plugins") : null;
			findAndCollect(plugins, groupId, artifactId, tags::add);
		}
		if (src instanceof DependencySource.ProfilePluginManagement pp) {
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

	private static String getSubTagText(XmlTag parent, String name) {
		XmlTag tag = parent.findFirstSubTag(name);
		return tag != null ? getTagText(tag) : null;
	}

	private static String getTagText(XmlTag tag) {
		if (tag.getValue() == null) {
			return "";
		}
		String text = tag.getValue().getText();
		return text != null ? text.trim() : "";
	}

	private static void setTagValue(XmlTag tag, String value) {
		if (tag.getValue() != null) {
			tag.getValue().setText(value);
		}
	}

	private static void addVersionTag(XmlTag parent, String version) {
		XmlTag versionTag = parent.createChildTag("version", parent.getNamespace(), version, false);
		parent.addSubTag(versionTag, false);
	}
}
