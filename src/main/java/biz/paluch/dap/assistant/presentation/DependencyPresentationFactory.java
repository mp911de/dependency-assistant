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
import biz.paluch.dap.metadata.ProjectMetadataService;
import biz.paluch.dap.metadata.ProjectName;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.state.ApplicationSettings;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;

/**
 * Creates detached {@link DependencyPresentation} snapshots from package
 * identity, dependency rules, remembered name hints, and cached project
 * metadata.
 *
 * <p>A non-blank name supplied by the resolved {@link DependencyRule} takes
 * precedence over a name remembered in {@link ApplicationSettings}. The cached
 * project name is carried separately as {@link ProjectName} and does not
 * participate in {@link DependencyPresentation#getDisplayName()}.
 *
 * <p>The factory retains its metadata and settings collaborators. Created
 * presentations retain only immutable presentation values and, for
 * {@link IconDependencyPresentation}, the selected table icon.
 *
 * @author Mark Paluch
 */
public class DependencyPresentationFactory {

	private final ProjectMetadataService metadataService;

	private final ApplicationSettings settings;

	/**
	 * Create a factory backed by the given project's metadata service and the
	 * application-level settings service.
	 *
	 * @param project the project whose cached metadata should be used.
	 */
	public DependencyPresentationFactory(Project project) {
		this.metadataService = ProjectMetadataService.getInstance(project);
		this.settings = ApplicationSettings.getInstance();
	}

	/**
	 * Create a factory backed by the given collaborators.
	 *
	 * @param metadataService the source of cached project names.
	 * @param settings the source of remembered dependency names.
	 */
	public DependencyPresentationFactory(ProjectMetadataService metadataService, ApplicationSettings settings) {
		this.metadataService = metadataService;
		this.settings = settings;
	}

	/**
	 * Create a detached presentation for the given package.
	 *
	 * <p>The result captures the rule-provided or remembered dependency name and
	 * the current cached project-name policy.
	 *
	 * @param pkg the package identity to present.
	 * @param rule the resolved rule supplying the preferred dependency name.
	 * @return a detached presentation of the package and its resolved names.
	 */
	public DependencyPresentation create(PackageIdentity pkg, DependencyRule rule) {

		ProjectName projectName = metadataService.getProjectName(pkg.getArtifactId());
		String dependencyName = rule.getDependencyName();

		if (StringUtils.isEmpty(dependencyName)) {
			dependencyName = settings.findNameHint(pkg);
		}

		return DependencyPresentation.of(pkg, dependencyName, projectName);
	}

	/**
	 * Create a detached presentation for the given dependency with the table icon
	 * selected by the interface assistant.
	 *
	 * @param dependency the dependency whose identity and declaration kind should
	 * be presented.
	 * @param rule the resolved rule supplying the preferred dependency name.
	 * @param assistant the interface assistant that selects the table icon.
	 * @return a detached presentation of the dependency, resolved names, and table
	 * icon.
	 */
	public IconDependencyPresentation create(Dependency dependency, DependencyRule rule, InterfaceAssistant assistant) {

		DependencyPresentation presentation = create(dependency.getPackageIdentity(), rule);
		Icon tableIcon = assistant.getTableIcon(dependency);
		return new DefaultIconDependencyPresentation(tableIcon, presentation);
	}

}
