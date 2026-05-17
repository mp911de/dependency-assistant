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

package biz.paluch.dap.antora;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import com.intellij.psi.PsiFile;

/**
 * Scans a single Antora playbook file and registers its {@code ui.bundle.url}
 * references with a {@link DependencyCollector}.
 *
 * <p>
 * This collector is intentionally syntax-only. It records the repository
 * identity and declared version as found in the playbook and leaves cache-based
 * version resolution to the project context and lookup services.
 *
 * @author Mark Paluch
 */
class AntoraDependencyCollector {

	private final AntoraPlaybookParser parser = new AntoraPlaybookParser();

	/**
	 * Collect Antora bundle URL references from the given playbook file.
	 * @param file the Antora playbook PSI file to scan.
	 * @return the populated dependency collector.
	 */
	DependencyCollector collect(PsiFile file) {

		DependencyCollector collector = new DependencyCollector();
		doCollect(file, collector);

		return collector;
	}

	/**
	 * Collect Antora bundle URL references from the given playbook file and
	 * register them as declarations on the given collector.
	 * @param file the Antora playbook PSI file to scan.
	 * @param collector the collector to populate with the discovered dependencies.
	 */
	void doCollect(PsiFile file, DependencyCollector collector) {

		List<AntoraBundleUrl> refs = parser.parse(file);
		for (AntoraBundleUrl ref : refs) {
			ArtifactId artifactId = ref.toArtifactId();
			VersionSource versionSource = ref.toVersionSource();
			collector.registerDeclaration(artifactId, DeclarationSource.dependency(), versionSource);
		}
	}

}
