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

import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Utilities for identifying NPM {@code package.json} files.
 *
 * <p>Detection requires the file to be a JSON file named {@code package.json}
 * whose root object contains a {@code dependencies} or {@code devDependencies}
 * property whose value is a JSON object. Files without those keys, with
 * malformed JSON, or whose value is non-object are silently skipped so that
 * unrelated JSON content (settings, schemas) never produces highlights.
 *
 * @author Mark Paluch
 */
class NpmUtils {

	static final String PACKAGE_JSON = "package.json";

	private NpmUtils() {
	}

	/**
	 * Return whether the given file is a {@code package.json} that the integration
	 * should manage.
	 * @param file the PSI file to test; must not be {@literal null}.
	 * @return {@literal true} when the file qualifies.
	 */
	static boolean isPackageJson(PsiFile file) {

		if (file.getUserData(NpmProjectContext.KEY) != null) {
			return true;
		}

		VirtualFile virtualFile = file.getVirtualFile();
		if (virtualFile == null || !PACKAGE_JSON.equals(virtualFile.getName())) {
			return false;
		}

		return hasDependencyKey(file);
	}

	/**
	 * Return whether the given virtual file is named {@code package.json}.
	 * <p>This is a name check only; the IDE caller still needs to inspect the PSI
	 * to verify the document carries dependency keys.
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
