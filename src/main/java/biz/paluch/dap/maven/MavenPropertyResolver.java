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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.support.PropertyResolver;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;

/**
 * Maven-specific {@link PropertyResolver} that resolves properties from Maven
 * files within the project hierarchy.
 *
 * @author Mark Paluch
 */
class MavenPropertyResolver extends MavenProperties {

	private final MavenProjectContext context;

	private static final MavenPropertyResolver EMPTY = new MavenPropertyResolver(null, null, PropertyResolver.empty());

	private MavenPropertyResolver(MavenProjectContext context, PsiFile root,
			PropertyResolver compositePropertyResolver) {
		super(root, compositePropertyResolver);
		this.context = context;
	}

	/**
	 * Create a new {@code MavenPropertyResolver} for the given
	 * {@link MavenProjectContext}.
	 */
	public static MavenPropertyResolver create(MavenProjectContext context, PsiFile pomFile) {

		if (context.isAbsent() || !(pomFile instanceof XmlFile xmlFile)) {
			return EMPTY;
		}

		PropertyResolver propertyResolver = new MavenProjectMetadataPropertyResolver(xmlFile);

		List<MavenProject> hierarchy = getProjects(context);

		for (int i = hierarchy.size() - 1; i >= 0; i--) {
			MavenProject project = hierarchy.get(i);
			PsiFile parentPom = context.findFile(project.getFile());

			if (MavenUtils.isMavenPomFile(parentPom)) {
				propertyResolver = propertyResolver.withFallback(MavenProperties.from(parentPom));
			}
		}

		return new MavenPropertyResolver(context, pomFile, propertyResolver);
	}

	private static List<MavenProject> getProjects(MavenProjectContext context) {

		List<MavenProject> hierarchy = new ArrayList<>();
		Set<MavenId> visited = new HashSet<>();
		MavenProject current = context.getMavenProject();

		while (current != null) {
			MavenId currentId = current.getMavenId();
			if (!visited.add(currentId)) {
				break;
			}
			hierarchy.add(current);

			MavenId parentId = current.getParentId();
			if (parentId == null) {
				break;
			}

			current = context.getProjectsManager().findProject(parentId);
		}
		return hierarchy;
	}

}
