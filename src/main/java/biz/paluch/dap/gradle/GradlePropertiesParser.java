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

import biz.paluch.dap.support.PropertyValue;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Parser for {@code gradle.properties}.
 *
 * @author Mark Paluch
 */
class GradlePropertiesParser {

	/**
	 * Return whether {@code element} is inside the editable value of a
	 * {@code gradle.properties} property.
	 */
	static boolean isPropertyValueElement(PsiElement element) {
		return getPropertyValueElement(element) != null;
	}

	/**
	 * Return the enclosing property value PSI element, if any.
	 */
	static @Nullable PropertyValueImpl getPropertyValueElement(PsiElement element) {

		if (element instanceof PropertyValueImpl propertyValue) {
			return propertyValue;
		}

		return PsiTreeUtil.getParentOfType(element, PropertyValueImpl.class, false);
	}

	/**
	 * Return the property owning {@code element} when the element belongs to a
	 * property value.
	 */
	static @Nullable Property getProperty(PsiElement element) {

		PropertyValueImpl propertyValue = getPropertyValueElement(element);
		if (propertyValue == null) {
			return null;
		}

		return PsiTreeUtil.getParentOfType(propertyValue, Property.class);
	}

	/**
	 * Loads all properties from a {@code gradle.properties} PSI file into a map.
	 */
	public static Map<String, biz.paluch.dap.support.Property> parseGradleProperties(PsiFile file) {

		if (!(file instanceof PropertiesFile propsFile)) {
			return Map.of();
		}

		Map<String, biz.paluch.dap.support.Property> result = new LinkedHashMap<>();
		doParseProperties(propsFile, it -> result.put(it.getKey(), it));
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
		doParseProperties(propsFile, it -> result.put(it.getKey(), it.getValue()));
		return result;
	}

	private static void doParseProperties(PropertiesFile propsFile, Consumer<PropertyValue> action) {

		for (IProperty prop : propsFile.getProperties()) {
			String key = prop.getUnescapedKey();
			String value = prop.getUnescapedValue();
			if (key != null && value != null) {
				PsiElement propPsi = prop.getPsiElement();
				PsiElement valuePsi = propPsi.getLastChild();
				action.accept(new PropertyValue(key, value, valuePsi));
			}
		}
	}

}
