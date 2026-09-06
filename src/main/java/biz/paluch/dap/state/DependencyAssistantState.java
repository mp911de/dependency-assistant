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

package biz.paluch.dap.state;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

/**
 * Persistent project state. Runtime dependency collectors belong to
 * {@link StateService} and are not serialized.
 *
 * @author Mark Paluch
 */
public class DependencyAssistantState {

	private @Tag Cache cache = new Cache();

	@Attribute
	private volatile boolean usedOnce = false;

	public Cache getCache() {
		return this.cache;
	}

	public void setCache(Cache cache) {
		this.cache = cache;
	}

	public boolean isUsedOnce() {
		return usedOnce;
	}

	public void setUsedOnce(boolean usedOnce) {
		this.usedOnce = usedOnce;
		this.cache.incrementModification();
	}

	/**
	 * Prepare the cache for use after reloading it.
	 */
	public void postLoad() {
		this.cache.reindex();
	}

}
