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

package biz.paluch.dap.gradle;

import java.util.Map;
import java.util.Set;

import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValuesManager;
import org.jspecify.annotations.Nullable;

/**
 * Kotlin DSL-specific property resolver.
 *
 * @author Mark Paluch
 */
class KotlinDslPropertyResolver implements PropertyResolver {

	private final Map<String, ? extends Property> extra;

	private final Map<String, ? extends Property> local;

	private KotlinDslPropertyResolver(Map<String, ? extends Property> extra, Map<String, ? extends Property> local) {
		this.extra = extra;
		this.local = local;
	}

	/**
	 * Create a property resolver for the given Kotlin DSL build file. The parsed
	 * {@code extra} and {@code val} properties are cached per PSI modification.
	 * @param file the Kotlin build script.
	 * @return a resolver backed by the file's local property declarations.
	 */
	public static KotlinDslPropertyResolver from(PsiFile file) {

		return CachedValuesManager.getProjectPsiDependentCache(file, it -> {

			Map<String, ? extends Property> extra = KotlinDslExtraParser.parseExtraProperties(file);
			Map<String, ? extends Property> local = KotlinDslExtraParser.parseValProperties(file);

			return new KotlinDslPropertyResolver(extra, local);
		});
	}

	public Set<String> getExtraPropertyNames() {
		return Set.copyOf(extra.keySet());
	}

	@Override
	public boolean containsProperty(String key) {
		return extra.containsKey(key) || local.containsKey(key);
	}

	@Override
	public @Nullable String getProperty(String key) {
		Property value = getPropertyValue(key);
		return value != null ? value.getValue() : null;
	}

	@Override
	public @Nullable Property getPropertyValue(String key) {
		Property value = extra.get(key);
		return value != null ? value : local.get(key);
	}

}
