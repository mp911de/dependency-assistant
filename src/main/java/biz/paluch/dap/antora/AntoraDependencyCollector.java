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

package biz.paluch.dap.antora;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import com.intellij.psi.PsiFile;

/**
 * Collect declared Antora bundle refs without resolving them against releases.
 *
 * @author Mark Paluch
 */
class AntoraDependencyCollector {

	private final AntoraPlaybookParser parser = new AntoraPlaybookParser();

	private final PackageSystem packageSystem;

	AntoraDependencyCollector(PackageSystem packageSystem) {
		this.packageSystem = packageSystem;
	}

	DependencyCollector collect(PsiFile file) {

		DependencyCollector collector = new DependencyCollector(packageSystem);
		doCollect(file, collector);

		return collector;
	}

	void doCollect(PsiFile file, DependencyCollector collector) {

		List<AntoraBundleUrl> refs = parser.parse(file);
		for (AntoraBundleUrl ref : refs) {
			ArtifactId artifactId = ref.toArtifactId();
			VersionSource versionSource = ref.toVersionSource();
			collector.registerDeclaration(artifactId, DeclarationSource.dependency(), versionSource);
		}
	}

}
