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

package biz.paluch.dap.npm;

import java.util.List;
import java.util.Optional;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.GitVersionResolver;
import com.intellij.psi.PsiFile;

/**
 * Scans a single {@code package.json} file and registers each accepted
 * {@code dependencies}/{@code devDependencies} entry with a
 * {@link DependencyCollector}.
 *
 * @author Mark Paluch
 */
class NpmDependencyCollector {

	private final NpmPackageParser parser = new NpmPackageParser();

	private final GitVersionResolver gitVersionResolver;

	public NpmDependencyCollector(Cache cache) {
		this.gitVersionResolver = new GitVersionResolver(cache);
	}

	/**
	 * Collect NPM dependencies from the given {@code package.json} file.
	 * @param file the JSON PSI file to scan; must not be {@literal null}.
	 * @return the populated dependency collector.
	 */
	DependencyCollector collect(PsiFile file) {

		DependencyCollector collector = new DependencyCollector();
		doCollect(file, collector);
		return collector;
	}

	/**
	 * Collect NPM dependencies from the given {@code package.json} file.
	 */
	void doCollect(PsiFile file, DependencyCollector collector) {

		List<NpmDependency> dependencies = parser.parse(file);
		for (NpmDependency dependency : dependencies) {

			VersionSource versionSource = dependency.versionSource();
			collector.registerDeclaration(dependency.artifactId(), DeclarationSource.dependency(), versionSource);

			if (!versionSource.isDefined() || versionSource.isPrefix()) {
				continue;
			}

			if (dependency.version() instanceof NpmVersionExpression.Git git) {
				Optional<GitVersion> version = gitVersionResolver.resolve(dependency.artifactId(), git.text());
				if (version.isPresent()) {
					version.ifPresent(it -> {
						collector.registerUsage(dependency.artifactId(), it, DeclarationSource.dependency(),
								versionSource);
					});
				} else {
					collector.registerUsage(dependency.artifactId(), new GitRef(git.text()),
							DeclarationSource.dependency(),
							versionSource);
				}
			} else {
				dependency.artifactVersion().ifPresent(version -> {
					collector.registerUsage(dependency.artifactId(), version, DeclarationSource.dependency(),
							versionSource);
				});
			}
		}
	}


}
