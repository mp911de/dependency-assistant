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

/**
 * Phase-scoped completion handle for one {@link ProjectStateIndexer} run.
 *
 * <p>A {@link DependencyAssistant} produces a fresh instance per run, and the
 * indexer invokes {@link #complete(DependencyCollector)} for each phase-one
 * collector after all enumeration and collection has finished. Implementations
 * may use scan-wide knowledge accumulated during collection to enrich each
 * collector in place.
 *
 * <p>The empty instance returned by {@link #empty()} is usable by integrations
 * that do not require any post-scan introspection, such as NPM.
 *
 * @author Mark Paluch
 * @see DependencyAssistant
 * @see ProjectStateIndexer
 */
public interface IntrospectedDependencies {

	IntrospectedDependencies EMPTY = collector -> {
	};

	/**
	 * Mutate the given collector in place using introspection state accumulated
	 * during phase one.
	 * <p>Implementations must use the very instance produced by the indexer during
	 * phase one and must not return a substitute.
	 * @param collector the collector to enrich.
	 */
	void complete(DependencyCollector collector);

	/**
	 * Return a no-op instance for integrations that do not introspect after the
	 * scan.
	 */
	static IntrospectedDependencies empty() {
		return EMPTY;
	}

}
