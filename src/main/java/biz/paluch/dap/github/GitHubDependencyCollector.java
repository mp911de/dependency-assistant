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

package biz.paluch.dap.github;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Collects repository-backed {@code uses:} declarations from a workflow.
 * <p>Refs are resolved from cached metadata or their raw version text without
 * network access. Declarations remain available when resolution fails.
 *
 * @author Mark Paluch
 */
class GitHubDependencyCollector {

	private final GitHubWorkflowParser parser = new GitHubWorkflowParser();

	private final StateService service;

	private final GitVersionResolver versionResolver;

	public GitHubDependencyCollector(Project project) {
		this.service = StateService.getInstance(project);
		this.versionResolver = new GitVersionResolver(service.getCache());
	}

	DependencyCollector collect(PackageSystem packageSystem, PsiFile file) {

		DependencyCollector collector = new DependencyCollector(packageSystem);
		doCollect(file, collector);

		return collector;
	}

	public void doCollect(PsiFile file, DependencyCollector collector) {

		List<UsesRepositoryAction> refs = parser.parse(file);
		for (UsesRepositoryAction ref : refs) {
			ArtifactId artifactId = ref.getArtifactId();
			VersionSource versionSource = ref.toVersionSource();

			collector.registerDeclaration(artifactId, DeclarationSource.dependency(),
					versionSource);

			if (StringUtils.isEmpty(ref.version())) {
				continue;
			}

			Versioned resolved = versionResolver.resolveLenient(artifactId, ref.version());
			if (resolved.isVersioned()) {
				collector.registerUsage(artifactId, resolved.getVersion(), DeclarationSource.dependency(),
						versionSource);
			}
		}
	}

}
