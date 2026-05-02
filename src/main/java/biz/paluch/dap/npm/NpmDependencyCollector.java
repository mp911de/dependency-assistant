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
import java.util.Set;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import com.intellij.psi.PsiFile;

/**
 * Scans a single {@code package.json} file and registers each accepted
 * {@code dependencies}/{@code devDependencies} entry with a
 * {@link DependencyCollector}.
 *
 * <p>The collector also returns the set of distinct GitHub hosts seen across
 * Git-backed entries so the project context can build one
 * {@link biz.paluch.dap.github.GitHubReleaseSource GitHub release source} per
 * host. Routing per host (rather than per dependency) keeps GitHub auth state
 * and rate limits aligned across declarations.
 *
 * @author Mark Paluch
 */
class NpmDependencyCollector {

	private final NpmPackageParser parser = new NpmPackageParser();

	/**
	 * Result of a {@code package.json} scan.
	 *
	 * @param collector the populated dependency collector.
	 * @param gitHosts the set of distinct GitHub hosts referenced by accepted Git
	 * URLs; possibly empty.
	 */
	record CollectionResult(DependencyCollector collector, Set<String> gitHosts) {

	}

	/**
	 * Collect NPM dependencies from the given {@code package.json} file.
	 * @param file the JSON PSI file to scan; must not be {@literal null}.
	 * @return the collection result.
	 */
	DependencyCollector collect(PsiFile file) {

		DependencyCollector collector = new DependencyCollector();
		List<NpmDependency> dependencies = parser.parse(file);
		for (NpmDependency dependency : dependencies) {

			VersionSource versionSource = toVersionSource(dependency);
			collector.registerDeclaration(dependency.artifactId(), DeclarationSource.dependency(), versionSource);
		}

		return collector;
	}

	private static VersionSource toVersionSource(NpmDependency dependency) {

		String raw = rawText(dependency.version());
		return raw.isEmpty() ? VersionSource.none() : VersionSource.declared(raw);
	}

	private static String rawText(NpmVersionExpression expression) {

		return switch (expression) {
		case NpmVersionExpression.Exact exact -> exact.modifier() + exact.version();
		case NpmVersionExpression.RangeUpper range -> range.prefix() + range.upper();
		case NpmVersionExpression.Prefix prefix -> prefix.text();
		case NpmVersionExpression.Alias alias -> "npm:%s@%s".formatted(alias.packageName(), rawText(alias.inner()));
		case NpmVersionExpression.Git git -> git.ref().committish();
		};
	}

}
