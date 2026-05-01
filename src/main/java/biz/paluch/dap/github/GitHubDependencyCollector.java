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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
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
		}
	}

}
