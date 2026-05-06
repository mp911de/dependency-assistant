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

import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * Utilities to access Maven properties.
 *
 * @author Mark Paluch
 */
class MavenProperties {

	private final PsiManager psiManager;
	private final MavenProjectsManager manager;

	/**
	 * Create a new {@code MavenProperties}.
	 * @param project the IntelliJ project.
	 */
	public MavenProperties(Project project) {
		this(PsiManager.getInstance(project), MavenProjectsManager.getInstance(project));
	}

	/**
	 * Create a new {@code MavenProperties}.
	 * @param project the IntelliJ project.
	 * @param manager the Maven projects manager to inspect.
	 */
	public MavenProperties(Project project, MavenProjectsManager manager) {
		this.psiManager = PsiManager.getInstance(project);
		this.manager = manager;
	}

	/**
	 * Create a new {@code MavenProperties}.
	 * @param project the IntelliJ project.
	 * @param psiManager the PSI manager to use.
	 * @param manager the Maven projects manager to inspect.
	 */
	public MavenProperties(PsiManager psiManager, MavenProjectsManager manager) {
		this.psiManager = psiManager;
		this.manager = manager;
	}

	/**
	 * Return all properties visible to the given Maven project.
	 * @param mavenProject the Maven project to inspect.
	 */
	public Map<String, PropertyValue> parseAllProperties(MavenProject mavenProject) {

		Map<String, PropertyValue> properties = new HashMap<>();
		List<MavenProject> hierarchy = getProjects(mavenProject);

		for (int i = hierarchy.size() - 1; i >= 0; i--) {
			MavenProject project = hierarchy.get(i);
			PsiFile pomFile = psiManager.findFile(project.getFile());

			if (pomFile instanceof XmlFile xmlFile) {
				properties.putAll(MavenParser.parseProperties(xmlFile));
			}
		}

		return properties;
	}

	/**
	 * Return all properties visible to the given Maven project.
	 * @param mavenProject the Maven project to inspect.
	 */
	public Map<String, String> getAllProperties(MavenProject mavenProject) {

		Map<String, String> properties = new HashMap<>();
		parseAllProperties(mavenProject).forEach((k, v) -> properties.put(k, v.getValue()));
		return properties;
	}

	private List<MavenProject> getProjects(MavenProject mavenProject) {
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

	/**
	 * Create a composite property resolver for the given Maven project.
	 */
	public PropertyResolver getPropertyResolver(MavenProject mavenProject, XmlFile pom) {
		return new MavenProjectMetadataPropertyResolver(pom)
				.withFallback(PropertyResolver.fromMap(parseAllProperties(mavenProject)));
	}

}
