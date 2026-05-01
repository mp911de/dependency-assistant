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
 * Scans a single GitHub Actions workflow file and registers discovered
 * {@code uses:} references as dependency usages in a
 * {@link DependencyCollector}.
 *
 * <p>Each discovered reference is resolved against the shared cache using
 * {@link GitVersionResolver}. Unresolvable refs are silently skipped so that
 * incomplete cache state does not fail the entire scan.
 *
 * @author Mark Paluch
 */
class GitHubDependencyCollector {

	private final GitHubWorkflowParser parser = new GitHubWorkflowParser();

	/**
	 * Collect all {@code uses:} references from the given workflow file and
	 * register them as dependency usages.
	 * @param file the YAML workflow PSI file to scan.
	 * @return the populated dependency collector.
	 */
	DependencyCollector collect(PsiFile file) {

		DependencyCollector collector = new DependencyCollector();
		doCollect(file, collector);

		return collector;
	}

	/**
	 * Collect all {@code uses:} references from the given workflow file and
	 * register them as dependency usages.
	 * @param file the YAML workflow PSI file to scan.
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
