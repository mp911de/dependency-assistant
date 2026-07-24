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

package biz.paluch.dap.assistant;

import javax.swing.Icon;

import biz.paluch.dap.DependencyPresentation;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDisplayName;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * @author Mark Paluch
 */
public final class DependencyPresentationFactory {

	private final StateService stateService;

	public DependencyPresentationFactory(StateService stateService) {
		this.stateService = stateService;
	}

	public DependencyPresentation create(ArtifactId artifactId, DependencyRule rule, InterfaceAssistant assistant) {

		String projectName = null;
		CachedArtifact artifact = stateService.getCache()
				.findCachedArtifact(artifactId);
		if (artifact != null) {
			projectName = artifact.getProjectName();
		}
		return create(artifactId, projectName, rule, assistant);
	}

	public static DependencyPresentation create(ArtifactId artifactId, @Nullable String projectName,
			DependencyRule rule,
			InterfaceAssistant assistant) {

		String dependencyName = rule.getDependencyName();

		if (StringUtils.isEmpty(dependencyName)) {
			dependencyName = ProjectDisplayName.getAcceptedProjectName(artifactId,
					projectName);
		}

		return DependencyPresentation.of(artifactId,
				assistant.getDisplayName(artifactId), dependencyName, projectName);
	}

	public IconDependencyPresentation create(Dependency dependency, DependencyRule rule, InterfaceAssistant assistant) {

		DependencyPresentation presentation = create(dependency.getArtifactId(), rule, assistant);
		Icon tableIcon = assistant.getTableIcon(dependency);
		return new DefaultIconDependencyPresentation(tableIcon, assistant.getClass().getName(), presentation);
	}

}
