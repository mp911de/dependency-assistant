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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * JSON PSI walker that classifies NPM dependency entries declared in
 * {@code dependencies} and {@code devDependencies}.
 *
 * <p>The package name is normalized to {@link ArtifactId} as documented on
 * {@link NpmDependency}. Entries that fail the NPM-name allowlist
 * ({@code @?[a-z0-9][a-z0-9._-]*(/[a-z0-9][a-z0-9._-]*)?}) are silently skipped
 * at this layer so no out-of-policy name reaches the registry source or any
 * cache key. Out-of-scope value shapes (or-ranges, {@code latest}, {@code *},
 * {@code file:}, {@code link:}, {@code workspace:}, non-Git URL protocols,
 * {@code npm:<name>} without an inner range) likewise produce no dependency.
 *
 * @author Mark Paluch
 */
class NpmPackageParser {

	static final Pattern NAME_ALLOWLIST = Pattern
			.compile("@?[a-z0-9][a-z0-9._-]*(/[a-z0-9][a-z0-9._-]*)?");

	private static final List<String> DEPENDENCY_KEYS = List.of("dependencies", "devDependencies");

	/**
	 * Parse the {@code dependencies} and {@code devDependencies} entries from the
	 * given JSON file. Files that are not {@link JsonFile JSON files} or whose root
	 * is not a JSON object produce an empty result.
	 * @param file the PSI file to scan; must not be {@literal null}.
	 * @return the discovered NPM dependencies, possibly empty.
	 */
	public List<NpmDependency> parse(PsiFile file) {

		if (!(file instanceof JsonFile jsonFile)) {
			return List.of();
		}

		JsonValue topLevelValue = jsonFile.getTopLevelValue();
		if (!(topLevelValue instanceof JsonObject root)) {
			return List.of();
		}

		List<NpmDependency> result = new ArrayList<>();
		for (String key : DEPENDENCY_KEYS) {

			JsonProperty property = root.findProperty(key);
			if (property == null || !(property.getValue() instanceof JsonObject dependenciesObject)) {
				continue;
			}

			collectFrom(dependenciesObject, result);
		}

		return result;
	}

	private static void collectFrom(JsonObject dependenciesObject, List<NpmDependency> result) {

		for (JsonProperty entry : dependenciesObject.getPropertyList()) {

			NpmDependency dependency = parseEntry(entry);
			if (dependency != null) {
				result.add(dependency);
			}
		}
	}

	private static @Nullable NpmDependency parseEntry(JsonProperty entry) {

		String name = entry.getName();
		if (!NAME_ALLOWLIST.matcher(name).matches()) {
			return null;
		}

		if (!(entry.getValue() instanceof JsonStringLiteral literal)) {
			return null;
		}

		NpmVersionExpression expression = NpmVersionExpression.parse(literal.getValue());
		if (expression == null) {
			return null;
		}

		ArtifactId artifactId = expression.postProcess(toArtifactId(name));
		return new NpmDependency(artifactId, expression);
	}

	/**
	 * Return the canonical {@link ArtifactId} for the given NPM package name.
	 *
	 * <p>Unscoped names produce {@code groupId == artifactId == name}. Scoped names
	 * {@code @scope/name} split into {@code groupId = "@scope"} and
	 * {@code artifactId = "name"}.
	 * @param name an NPM package name that has passed the {@link #NAME_ALLOWLIST
	 * allowlist}.
	 * @return the canonical artifact identity.
	 */
	static ArtifactId toArtifactId(String name) {

		int slash = name.indexOf('/');
		if (slash < 0) {
			return ArtifactId.of(name, name);
		}
		return ArtifactId.of(name.substring(0, slash), name.substring(slash + 1));
	}

}
