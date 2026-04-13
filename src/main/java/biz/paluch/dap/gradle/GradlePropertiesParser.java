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
import java.util.function.Consumer;

import biz.paluch.dap.support.PsiPropertyValueElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Parser for {@code gradle.properties}.
 *
 * @author Mark Paluch
 */
class GradlePropertiesParser {

	/**
	 * Loads all properties from a {@code gradle.properties} PSI file into a map.
	 */
	public static Map<String, PsiPropertyValueElement> parseGradleProperties(PsiFile file) {

		if (!(file instanceof PropertiesFile propsFile)) {
			return Map.of();
		}

		Map<String, PsiPropertyValueElement> result = new LinkedHashMap<>();
		doParseProperties(propsFile, it -> result.put(it.propertyKey(), it));
		return result;
	}

	/**
	 * Loads all properties from a {@code gradle.properties} PSI file into a map.
	 */
	public static Map<String, String> getGradleProperties(PsiFile file) {

		if (!(file instanceof PropertiesFile propsFile)) {
			return Map.of();
		}

		Map<String, String> result = new LinkedHashMap<>();
		doParseProperties(propsFile, it -> result.put(it.propertyKey(), it.propertyValue()));
		return result;
	}

	private static void doParseProperties(PropertiesFile propsFile, Consumer<PsiPropertyValueElement> action) {

		for (IProperty prop : propsFile.getProperties()) {
			String key = prop.getKey();
			String value = prop.getValue();
			if (key != null && value != null) {
				PsiElement propPsi = prop.getPsiElement();
				PsiElement valuePsi = propPsi.getLastChild();
				action.accept(new PsiPropertyValueElement(valuePsi, key, value));
			}
		}
	}

}
