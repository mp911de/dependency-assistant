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

package biz.paluch.dap.assistant.presentation;

import javax.swing.Icon;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.metadata.ProjectMetadata;
import biz.paluch.dap.metadata.ProjectMetadataService;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.state.ApplicationSettings;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;

/**
 * Creates {@link DependencyPresentation}s, resolving the dependency name with
 * the precedence rule name.
 *
 * @author Mark Paluch
 */
public class DependencyPresentationFactory {

	private final ProjectMetadataService metadataService;

	private final ApplicationSettings settings;

	public DependencyPresentationFactory(Project project) {
		this.metadataService = ProjectMetadataService.getInstance(project);
		this.settings = ApplicationSettings.getInstance();
	}

	public DependencyPresentationFactory(ProjectMetadataService metadataService, ApplicationSettings settings) {
		this.metadataService = metadataService;
		this.settings = settings;
	}

	/**
	 * Create a {@link DependencyPresentation} for the given package.
	 * @param pkg the package to create a presentation for.
	 * @param rule a rule governing the dependency.
	 * @param assistant the interface assistant associated with the project.
	 * @return a new presentation.
	 */
	public DependencyPresentation create(PackageIdentity pkg, DependencyRule rule, InterfaceAssistant assistant) {

		ProjectMetadata metadata = metadataService.getMetadata(pkg);

		String dependencyName = rule.getDependencyName();
		String projectName = metadata.getProjectName();

		if (StringUtils.isEmpty(dependencyName)) {
			dependencyName = settings.findNameHint(pkg);
		}

		if (StringUtils.isEmpty(dependencyName)) {
			projectName = ProjectDisplayName.getAcceptedProjectName(pkg.getArtifactId(),
					metadata.getProjectName());
		}

		String displayName = assistant.getDisplayName(pkg.getArtifactId());
		String artifactId = assistant.getArtifactId(pkg.getArtifactId());
		return DependencyPresentation.of(pkg, displayName,
				artifactId, dependencyName, projectName);
	}

	/**
	 * Create a {@link IconDependencyPresentation} for the given dependency.
	 * @param dependency the dependency to create a presentation for.
	 * @param rule a rule governing the dependency.
	 * @param assistant the interface assistant associated with the project.
	 * @return a new icon dependency presentation.
	 */
	public IconDependencyPresentation create(Dependency dependency, DependencyRule rule, InterfaceAssistant assistant) {

		DependencyPresentation presentation = create(dependency.getPackageIdentity(), rule, assistant);
		Icon tableIcon = assistant.getTableIcon(dependency);
		return new DefaultIconDependencyPresentation(tableIcon, presentation);
	}

}
