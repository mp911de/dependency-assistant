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

import biz.paluch.dap.assistant.ReleasesCompletionProvider;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.toml.lang.psi.TomlInlineTable;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlTable;
import org.toml.lang.psi.TomlValue;

/**
 * Completion contributor for Gradle version catalogs.
 *
 * @author Mark Paluch
 */
public class TomlCompletionContributor extends CompletionContributor {

	private static final ReleasesCompletionProvider provider = new ReleasesCompletionProvider();

	public TomlCompletionContributor() {

		PatternCondition<PsiFile> isVersionCatalog = new PatternCondition<>("isVersionCatalog") {

			@Override
			public boolean accepts(PsiFile psiFile, ProcessingContext processingContext) {
				return GradleUtils.isVersionCatalog(psiFile);
			}

		};

		PatternCondition<TomlKeyValue> isVersionKey = new PatternCondition<>("isVersionKey") {

			@Override
			public boolean accepts(TomlKeyValue keyValue, ProcessingContext processingContext) {
				String tomlKeyName = TomlParser.getTomlKeyName(keyValue.getKey());
				return TomlParser.VERSION.equals(tomlKeyName) || TomlParser.VERSION_REF.equals(tomlKeyName);
			}

		};

		PatternCondition<TomlTable> isPluginOrDependencyTable = new PatternCondition<>("isPluginOrDependencyTable") {

			@Override
			public boolean accepts(TomlTable table, ProcessingContext processingContext) {
				String tableName = TomlParser.getTomlTableName(table);
				return TomlParser.PLUGINS.equals(tableName) || TomlParser.LIBRARIES.equals(tableName);
			}

		};

		PatternCondition<TomlTable> isVersionTable = new PatternCondition<>("isVersionTable") {

			@Override
			public boolean accepts(TomlTable table, ProcessingContext processingContext) {
				String tableName = TomlParser.getTomlTableName(table);
				return TomlParser.VERSIONS.equals(tableName);
			}

		};

		PsiElementPattern.Capture<TomlKeyValue> kv = PlatformPatterns.psiElement(TomlKeyValue.class);

		PsiElementPattern.Capture<TomlTable> dependencyTable = PlatformPatterns.psiElement(TomlTable.class)
				.with(isPluginOrDependencyTable);

		PsiElementPattern.Capture<TomlTable> versionTable = PlatformPatterns.psiElement(TomlTable.class)
				.with(isVersionTable);

		extend(CompletionType.BASIC, PlatformPatterns.psiElement() //
				.inside(PlatformPatterns.psiElement(TomlValue.class)
						.withParent(kv.with(isVersionKey).withParent(TomlInlineTable.class).withAncestor(5,
								dependencyTable)))
				.inside(PlatformPatterns.psiFile().with(isVersionCatalog)),
				provider);

		extend(CompletionType.BASIC, PlatformPatterns.psiElement() //
				.inside(PlatformPatterns.psiElement(TomlValue.class)
						.withParent(kv.withParent(versionTable)))
				.inside(PlatformPatterns.psiFile().with(isVersionCatalog)),
				provider);
	}

}
