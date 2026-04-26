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

import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.toml.lang.psi.TomlInlineTable;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;

/**
 * TOML PSI locator for semantic version and property sites.
 *
 * @author Mark Paluch
 */
class TomlLookupSiteLocator implements LookupSiteLocator<TomlLiteral> {

	@Override
	public LookupSite locate(TomlLiteral literal) {

		TomlKeyValue keyValue = PsiTreeUtil.getParentOfType(literal, TomlKeyValue.class);
		if (keyValue == null) {
			return LookupSite.absent();
		}

		if (TomlParser.isInsideTable(literal, TomlParser.VERSIONS::equals)) {
			String propertyName = TomlParser.getTomlKeyName(keyValue.getKey());
			String rawVersion = TomlParser.getText(literal);
			if (rawVersion == null || StringUtils.isEmpty(rawVersion) || StringUtils.isEmpty(propertyName)) {
				return LookupSite.absent();
			}
			return LookupSite.ofProperty(propertyName, rawVersion, keyValue, literal);
		}

		if (TomlParser.isInsideTable(literal, it -> TomlParser.LIBRARIES.equals(it) || TomlParser.PLUGINS.equals(it))) {

			if (keyValue.getValue() != literal && !isInlineVersionLiteral(literal)) {
				return LookupSite.absent();
			}

			TomlKeyValue declarationKeyValue = keyValue;
			if (isInlineVersionLiteral(literal)) {
				TomlInlineTable inlineTable = PsiTreeUtil.getParentOfType(literal, TomlInlineTable.class);
				if (inlineTable == null) {
					return LookupSite.absent();
				}
				declarationKeyValue = PsiTreeUtil.getParentOfType(inlineTable, TomlKeyValue.class);
				if (declarationKeyValue == null) {
					return LookupSite.absent();
				}
			}

			GradlePropertyResolver propertyResolver = GradlePropertyResolver
					.forFile(declarationKeyValue.getContainingFile());
			TomlParser.TomlDependencyDeclaration declaration = TomlParser.parseTomlEntry(declarationKeyValue,
					propertyResolver);
			if (!declaration.isComplete() || declaration.versionLiteral() != literal) {
				return LookupSite.absent();
			}

			return LookupSite.from(declaration.toDependencySite(declarationKeyValue, literal));
		}

		return LookupSite.absent();
	}

	private static boolean isInlineVersionLiteral(TomlLiteral literal) {

		PsiElement parent = literal.getParent();
		return parent != null && parent.getFirstChild() != null
				&& TomlParser.VERSION.equals(parent.getFirstChild().getText().trim());
	}

}
