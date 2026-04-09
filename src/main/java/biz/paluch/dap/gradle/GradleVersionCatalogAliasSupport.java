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

import biz.paluch.dap.artifact.ArtifactId;

import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import org.toml.lang.psi.TomlFile;
import org.toml.lang.psi.TomlInlineTable;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;
import org.toml.lang.psi.TomlTable;

import com.intellij.psi.util.PsiTreeUtil;

/**
 * TOML PSI bridge for Gradle {@code gradle/libs.versions.toml}: finding entries, {@code [versions]} literals, and
 * inline {@code version} keys. Mapping {@code libs.…} accessor segments to catalog tables lives in
 * {@link TomlParser#catalogTableKeyFromLibsSegments}. Kotlin and Groovy {@code libs.…} navigation is in
 * {@link KotlinDslUtils} and {@link GroovyDslUtils}.
 *
 * @author Mark Paluch
 */
final class GradleVersionCatalogAliasSupport {

	private GradleVersionCatalogAliasSupport() {}

	static @Nullable TomlKeyValue findCatalogEntryKeyValue(TomlFile tomlFile, String tableName, String entryKey) {

		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(tomlFile, TomlTable.class)) {
			String tn = TomlParser.getTomlTableName(table);
			if (!tableName.equals(tn)) {
				continue;
			}
			for (TomlKeyValue kv : PsiTreeUtil.getChildrenOfTypeAsList(table, TomlKeyValue.class)) {
				if (entryKey.equals(TomlParser.getTomlKeyName(kv.getKey()))) {
					return kv;
				}
			}
		}
		return null;
	}

	static @Nullable TomlLiteral findVersionsTableLiteral(TomlFile tomlFile, String versionKey) {

		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(tomlFile, TomlTable.class)) {
			String tn = TomlParser.getTomlTableName(table);
			if (!"versions".equals(tn)) {
				continue;
			}
			for (TomlKeyValue vkv : PsiTreeUtil.getChildrenOfTypeAsList(table, TomlKeyValue.class)) {
				if (versionKey.equals(TomlParser.getTomlKeyName(vkv.getKey())) && vkv.getValue() instanceof TomlLiteral lit) {
					return lit;
				}
			}
		}
		return null;
	}

	static @Nullable TomlLiteral findInlineVersionLiteral(TomlInlineTable inline) {

		for (TomlKeyValue inner : PsiTreeUtil.getChildrenOfTypeAsList(inline, TomlKeyValue.class)) {
			if (!"version".equals(TomlParser.getTomlKeyName(inner.getKey()))) {
				continue;
			}
			if (inner.getValue() instanceof TomlLiteral lit) {
				return lit;
			}
		}
		return null;
	}

	static Function<String, ArtifactId> idFunctionForTable(String tableName) {
		return "plugins".equals(tableName) ? m -> ArtifactId.of(m, m) : GradleParserSupport::parseArtifactId;
	}

}
