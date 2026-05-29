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

/**
 * Phase-scoped completion handle for one {@link ProjectStateUpdater} run.
 *
 * <p>
 * A {@link DependencySource} produces a fresh instance per run, and the updater
 * invokes {@link #complete(DependencyCollector)} for each phase-one collector
 * after all enumeration and collection has finished. Implementations may use
 * scan-wide knowledge accumulated during collection to enrich each collector in
 * place. {@link #updateCache(Cache)} runs once, after all collectors have been
 * completed and all Project State stores have run.
 *
 * <p>
 * The empty instance returned by {@link #empty()} is usable by integrations
 * that do not require any post-scan introspection, such as NPM.
 *
 * @author Mark Paluch
 * @see DependencySource
 * @see ProjectStateUpdater
 */
public interface IntrospectedDependencies {

	IntrospectedDependencies EMPTY = collector -> {
	};

	/**
	 * Mutate the given collector in place using introspection state accumulated
	 * during phase one.
	 * <p>
	 * Implementations must use the very instance produced by the updater during
	 * phase one and must not return a substitute.
	 * @param collector the collector to enrich; must not be {@literal null}.
	 */
	void complete(DependencyCollector collector);

	/**
	 * Update the persistent cache with any introspection-derived metadata.
	 * <p>
	 * Invoked only once per updater run, after all collectors have completed and
	 * after Project State stores have been applied.
	 * @param cache the cache to mutate; must not be {@literal null}.
	 */
	default void updateCache(Cache cache) {
	}

	/**
	 * Return a no-op instance for integrations that do not introspect after the
	 * scan.
	 */
	static IntrospectedDependencies empty() {
		return EMPTY;
	}

}
