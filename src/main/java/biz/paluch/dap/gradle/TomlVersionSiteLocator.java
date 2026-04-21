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

import java.util.Map;
import java.util.function.Predicate;

import biz.paluch.dap.support.PsiPropertyValueElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;
import org.toml.lang.psi.TomlTable;

/**
 * TOML PSI locator for semantic version and property sites.
 *
 * @author Mark Paluch
 */
class TomlVersionSiteLocator {

	@Nullable
	GradleLookupSite locate(TomlLiteral literal) {

		TomlKeyValue keyValue = PsiTreeUtil.getParentOfType(literal, TomlKeyValue.class);
		if (keyValue == null) {
			return null;
		}

		if (isInsideTable(literal, TomlParser.VERSIONS::equals)) {
			String propertyName = TomlParser.getTomlKeyName(keyValue.getKey());
			String rawVersion = TomlParser.getText(literal);
			if (rawVersion == null) {
				return null;
			}
			return GradleLookupSite.property(propertyName, rawVersion, keyValue, literal, null);
		}

		if (!isInsideTable(literal, it -> TomlParser.LIBRARIES.equals(it) || TomlParser.PLUGINS.equals(it))) {
			return null;
		}

		if (keyValue.getValue() != literal && !isInlineVersionLiteral(literal)) {
			return null;
		}

		Map<String, PsiPropertyValueElement> properties = TomlParser.parseTomlVersions(keyValue.getContainingFile());
		TomlParser.TomlDependencyDeclaration declaration = TomlParser.parseTomlEntry(keyValue, properties);
		if (!declaration.isComplete() || declaration.versionLiteral() != literal) {
			return null;
		}

		return new GradleLookupSite.GradleVersionSite(declaration.toDependency(), keyValue, literal);
	}

	private static boolean isInlineVersionLiteral(TomlLiteral literal) {

		PsiElement parent = literal.getParent();
		return parent != null && parent.getFirstChild() != null
				&& TomlParser.VERSION.equals(parent.getFirstChild().getText().trim());
	}

	private static boolean isInsideTable(PsiElement element, Predicate<String> predicate) {

		TomlTable table = PsiTreeUtil.getParentOfType(element, TomlTable.class);
		if (table == null) {
			return false;
		}

		String tableName = TomlParser.getTomlTableName(table);
		return tableName != null && predicate.test(tableName);
	}

}
