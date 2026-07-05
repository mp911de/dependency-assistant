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

import java.util.Collection;
import java.util.List;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectStateIndexer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.project.MavenImportListener;
import org.jetbrains.idea.maven.project.MavenProject;

/**
 * Declarative project listener re-indexing the Maven assistant's project state
 * after a completed Maven import so members resolved from the effective
 * dependency management surface in highlighting.
 *
 * @author Mark Paluch
 * @see ProjectStateIndexer#refreshAfterImport
 */
class MavenImportRefreshListener implements MavenImportListener {

	private final Project project;

	MavenImportRefreshListener(Project project) {
		this.project = project;
	}

	@Override
	public void importFinished(Collection<MavenProject> importedProjects, List<Module> newModules) {

		ProjectStateIndexer indexer = new ProjectStateIndexer(project, new EmptyProgressIndicator());
		for (DependencyAssistant assistant : DependencyAssistantDispatcher.findAll()) {
			if (assistant instanceof MavenAssistant) {
				indexer.refreshAfterImport(assistant);
			}
		}
	}

}
