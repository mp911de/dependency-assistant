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
import java.util.List;
import java.util.Map;

import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

/**
 * Utilities to access Maven properties.
 *
 * @author Mark Paluch
 */
class MavenProperties {

	private final Project project;
	private final PsiManager psiManager;
	private final MavenProjectsManager manager;

	public MavenProperties(Project project) {
		this(project, PsiManager.getInstance(project), MavenProjectsManager.getInstance(project));
	}

	public MavenProperties(Project project, MavenProjectsManager manager) {
		this.project = project;
		this.psiManager = PsiManager.getInstance(project);
		this.manager = manager;
	}

	public MavenProperties(Project project, PsiManager psiManager, MavenProjectsManager manager) {
		this.project = project;
		this.psiManager = psiManager;
		this.manager = manager;
	}

	public Map<String, String> getAllProperties(MavenProject mavenProject) {

		Map<String, String> properties = new HashMap<>();

		List<MavenProject> hierarchy = getProjects(mavenProject);

		for (int i = hierarchy.size() - 1; i >= 0; i--) {
			MavenProject project = hierarchy.get(i);
			PsiFile pomFile = psiManager.findFile(project.getFile());

			if (pomFile != null) {
				properties.putAll(MavenParser.parseProperties(pomFile));
			}
		}

		return properties;
	}

	public @Nullable XmlTag findProperty(MavenProject mavenProject, String property) {

		List<MavenProject> hierarchy = getProjects(mavenProject);

		for (int i = hierarchy.size() - 1; i >= 0; i--) {
			MavenProject project = hierarchy.get(i);
			PsiFile pomFile = psiManager.findFile(project.getFile());

			if (pomFile instanceof XmlFile xmlFile && xmlFile.getDocument() != null) {
				XmlTag propertyValue = findProperty(xmlFile, property);
				if (propertyValue != null) {
					return propertyValue;
				}
			}
		}

		return null;
	}

	public static @Nullable XmlTag findProperty(XmlFile xmlFile, String property) {

		XmlTag rootTag = xmlFile.getDocument().getRootTag();
		if (rootTag == null) {
			return null;
		}

		XmlTag properties = rootTag.findFirstSubTag("properties");
		XmlTag propertyValue = getProperty(properties, property);
		if (propertyValue != null) {
			return propertyValue;
		}

		XmlTag[] profiles = rootTag.findSubTags("profiles");

		for (XmlTag profile : profiles) {

			properties = profile.findFirstSubTag("properties");
			propertyValue = getProperty(properties, property);
			if (propertyValue != null) {
				return propertyValue;
			}
		}

		return null;
	}

	private static @Nullable XmlTag getProperty(@Nullable XmlTag properties, String property) {

		if (properties == null) {
			return null;
		}
		XmlTag subTag = properties.findFirstSubTag(property);
		if (subTag == null) {
			return null;
		}
		String text = subTag.getValue().getText();
		if (StringUtils.hasText(text)) {
			return subTag;
		}

		return null;
	}

	private @NonNull List<MavenProject> getProjects(MavenProject mavenProject) {
		List<MavenProject> hierarchy = new ArrayList<>();
		MavenProject current = mavenProject;

		while (current != null) {
			hierarchy.add(current);

			MavenId parentId = current.getParentId();
			if (parentId == null) {
				break;
			}

			current = manager.findProject(parentId);
		}
		return hierarchy;
	}
}
