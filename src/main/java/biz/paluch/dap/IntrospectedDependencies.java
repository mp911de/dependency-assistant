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

package biz.paluch.dap;

import biz.paluch.dap.artifact.DependencyCollector;

/**
 * Completion handle for one dependency collection run.
 *
 * <p>A {@link DependencyAssistant} supplies one handle per run. Mutable
 * implementations use a fresh handle, while the empty implementation is shared.
 * The indexer invokes {@link #complete(DependencyCollector)} for each phase-one
 * collector after all enumeration and collection has finished. A file-scoped
 * collection completes its collector immediately after collection.
 * Implementations may use knowledge accumulated during the run to enrich each
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
	 * Enrich the given collector in place using introspection state accumulated
	 * during collection.
	 * <p>The host invokes this method only after phase-one collection for the run
	 * has completed.
	 *
	 * @param collector the collector to enrich.
	 */
	void complete(DependencyCollector collector);

	/**
	 * Return a no-op instance for integrations that do not introspect after the
	 * scan.
	 * @return the shared no-op completion handle.
	 */
	static IntrospectedDependencies empty() {
		return EMPTY;
	}

}
