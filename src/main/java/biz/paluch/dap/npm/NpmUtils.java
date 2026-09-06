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

package biz.paluch.dap.npm;

import biz.paluch.dap.artifact.ArtifactId;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * NPM file identification and package coordinates.
 * <p>PSI detection requires a dependency object in {@code package.json}.
 *
 * @author Mark Paluch
 */
class NpmUtils {

	static final PluginId GITHUB = PluginId.getId("org.jetbrains.plugins.github");

	static final boolean GITHUB_AVAILABLE = PluginManagerCore.isPluginInstalled(GITHUB)
			&& !PluginManagerCore.isDisabled(GITHUB);

	static final String PACKAGE_JSON = "package.json";

	private NpmUtils() {
	}

	/**
	 * Render a coordinate as an unscoped name or {@code @scope/name}.
	 */
	static String toString(ArtifactId artifactId) {
		if (artifactId.groupId().equals(artifactId.artifactId())) {
			return artifactId.artifactId();
		}
		return artifactId.groupId() + "/" + artifactId.artifactId();
	}

	/**
	 * Return the canonical artifact identity for an NPM package name.
	 * @param name a name that passed the package-name allowlist.
	 */
	static ArtifactId toArtifactId(String name) {

		int slash = name.indexOf('/');
		if (slash < 0) {
			return ArtifactId.of(name, name);
		}
		return ArtifactId.of(name.substring(0, slash), name.substring(slash + 1));
	}

	static boolean isPackageJson(PsiFile file) {

		if (!PACKAGE_JSON.equals(file.getName())) {
			return false;
		}

		return hasDependencyKey(file);
	}

	/**
	 * Check the file name only. Dependency sections require a separate PSI check.
	 */
	static boolean isPackageJson(VirtualFile file) {
		return PACKAGE_JSON.equals(file.getName());
	}

	private static boolean hasDependencyKey(PsiFile file) {

		if (!(file instanceof JsonFile jsonFile)) {
			return false;
		}

		JsonValue topLevel = jsonFile.getTopLevelValue();
		if (!(topLevel instanceof JsonObject root)) {
			return false;
		}

		return hasObjectProperty(root, "dependencies") || hasObjectProperty(root, "devDependencies");
	}

	private static boolean hasObjectProperty(JsonObject object, String key) {

		JsonProperty property = object.findProperty(key);
		return property != null && property.getValue() instanceof JsonObject && property.isValid();
	}

}
