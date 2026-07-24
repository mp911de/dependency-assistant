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
import biz.paluch.dap.artifact.ArtifactId;

/**
 * @author Mark Paluch
 */
class DefaultIconDependencyPresentation implements IconDependencyPresentation {

	private final Icon tableIcon;

	private final String ecosystem;

	private final DependencyPresentation presentation;

	public DefaultIconDependencyPresentation(Icon tableIcon, String ecosystem,
			DependencyPresentation presentation) {
		this.tableIcon = tableIcon;
		this.ecosystem = ecosystem;
		this.presentation = presentation;
	}

	@Override
	public ArtifactId getArtifactId() {
		return presentation.getArtifactId();
	}

	@Override
	public Icon getTableIcon() {
		return tableIcon;
	}

	@Override
	public String getEcosystem() {
		return ecosystem;
	}

	@Override
	public String getArtifactIdDisplayName() {
		return presentation.getArtifactIdDisplayName();
	}

	@Override
	public String getDisplayName() {
		return presentation.getDisplayName();
	}

	@Override
	public boolean hasDependencyName() {
		return presentation.hasDependencyName();
	}

	@Override
	public String getDependencyName() {
		return presentation.getDependencyName();
	}

	@Override
	public boolean hasProjectName() {
		return presentation.hasProjectName();
	}

	@Override
	public String getProjectName() {
		return presentation.getProjectName();
	}

	@Override
	public String toString() {
		return presentation.toString();
	}

}
