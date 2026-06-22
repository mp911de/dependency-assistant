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

package biz.paluch.dap.gradle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValuesManager;
import org.jspecify.annotations.Nullable;

/**
 * Groovy DSL-specific property resolver.
 *
 * <p>Resolves {@code ext} properties and script-level variables declared in a
 * Groovy build script. Script-level variables take precedence over {@code ext}
 * properties on a name collision, matching Gradle script scoping.
 *
 * @author Mark Paluch
 */
class GroovyDslPropertyResolver implements PropertyResolver {

	private final Map<String, PropertyValue> properties;

	private GroovyDslPropertyResolver(Map<String, PropertyValue> properties) {
		this.properties = properties;
	}

	/**
	 * Create a property resolver for the given Groovy DSL build file. The parsed
	 * {@code ext} properties and script-level variables are cached per PSI
	 * modification.
	 * @param file the Groovy build script.
	 * @return a resolver backed by the file's local property declarations.
	 */
	static GroovyDslPropertyResolver from(PsiFile file) {

		return CachedValuesManager.getProjectPsiDependentCache(file, it -> {

			Map<String, PropertyValue> properties = new LinkedHashMap<>(GroovyDslExtParser.parseExtProperties(file));
			properties.putAll(GroovyDslExtParser.parseLocalVariables(file));

			return new GroovyDslPropertyResolver(properties);
		});
	}

	Set<String> getDeclaredPropertyNames() {
		return Set.copyOf(properties.keySet());
	}

	@Override
	public boolean containsProperty(String key) {
		return properties.containsKey(key);
	}

	@Override
	public @Nullable String getProperty(String key) {
		PropertyValue value = properties.get(key);
		return value != null ? value.getValue() : null;
	}

	@Override
	public @Nullable PropertyValue getPropertyValue(String key) {
		return properties.get(key);
	}

}
