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

package biz.paluch.dap.state;

import com.intellij.util.xmlb.annotations.Tag;

/**
 * Persistent root state for the Dependency Assistant plugin.
 * <p>The type currently persists a single {@link Cache} instance and
 * intentionally keeps the model minimal so that service-level runtime state
 * remains outside the serialized contract.
 *
 * @author Mark Paluch
 */
public class DependencyAssistantState {

	private @Tag Cache cache = new Cache();

	// TODO: used state to avoid nagging.

	/**
	 * Return the persisted cache.
	 *
	 * @return the cache to be serialized with this state.
	 */
	public Cache getCache() {
		return this.cache;
	}

	/**
	 * Set the persisted cache.
	 *
	 * @param cache the cache to serialize with this state.
	 */
	public void setCache(Cache cache) {
		this.cache = cache;
	}

}
