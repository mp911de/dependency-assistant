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

package biz.paluch.dap.github;

import java.util.List;
import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Scans a single supported GitHub Actions YAML file and registers
 * repository-backed {@code uses:} references with a
 * {@link DependencyCollector}.
 *
 * <p>This collector is intentionally syntax-only. It records the repository
 * identity and declared ref as found in the workflow and leaves cache-based
 * version resolution to the project context and lookup services. This keeps
 * workflow scanning independent of release metadata availability.
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

	/**
	 * Collect repository-backed {@code uses:} references from the given GitHub
	 * Actions file.
	 * @param file the YAML PSI file to scan.
	 * @return the populated dependency collector.
	 */
	DependencyCollector collect(PsiFile file) {

		DependencyCollector collector = new DependencyCollector();
		doCollect(file, collector);

		return collector;
	}

	/**
	 * Collect repository-backed {@code uses:} references from the given GitHub
	 * Actions file and register them as declarations.
	 * @param file the YAML PSI file to scan.
	 * @param collector the collector to populate with the discovered dependencies.
	 */
	public void doCollect(PsiFile file, DependencyCollector collector) {

		List<UsesRepositoryAction> refs = parser.parse(file);
		for (UsesRepositoryAction ref : refs) {
			ArtifactId artifactId = ref.toArtifactId();
			VersionSource versionSource = ref.toVersionSource();

			collector.registerDeclaration(artifactId, DeclarationSource.dependency(),
					versionSource);

			if (StringUtils.isEmpty(ref.version())) {
				continue;
			}

			Optional<GitVersion> version = versionResolver.resolve(artifactId, ref.version());
			if (version.isPresent()) {
				version.ifPresent(gitVersion -> collector.registerUsage(artifactId, gitVersion,
						DeclarationSource.dependency(), versionSource));
			} else {

				Optional<ArtifactVersion> artifactVersion = ArtifactVersion.from(ref.version());
				if (artifactVersion.isPresent()) {
					artifactVersion
							.ifPresent(it -> collector.registerUsage(artifactId, it, DeclarationSource.dependency(),
									versionSource));
				} else {
					collector.registerUsage(artifactId, new GitRef(ref.version()), DeclarationSource.dependency(),
							versionSource);
				}
			}
		}
	}

}
