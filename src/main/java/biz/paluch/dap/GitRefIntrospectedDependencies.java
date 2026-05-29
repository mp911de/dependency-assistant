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

package biz.paluch.dap;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.GitVersionResolver;

/**
 * Cache-backed {@link IntrospectedDependencies} that promotes git-ref
 * declarations to usages during completion.
 *
 * <p>
 * Shared by the Antora and GitHub Actions integrations: during completion the
 * persistent {@link Cache} is consulted so declarations whose ref matches a
 * previously cached release are exposed as resolved dependency usages.
 * Declarations whose usages are already registered during phase-one collection
 * are not disturbed.
 *
 * @author Mark Paluch
 */
public class GitRefIntrospectedDependencies implements IntrospectedDependencies {

	private final Cache cache;

	/**
	 * Create a completion handle backed by the given persistent cache.
	 * @param cache the cache to consult during completion; must not be
	 * {@literal null}.
	 */
	public GitRefIntrospectedDependencies(Cache cache) {
		this.cache = cache;
	}

	@Override
	public void complete(DependencyCollector collector) {
		collector.promoteResolvedDeclarations(declaration -> GitVersionResolver.resolveDependency(declaration,
				cache.getReleases(declaration.getArtifactId())));
	}

}
