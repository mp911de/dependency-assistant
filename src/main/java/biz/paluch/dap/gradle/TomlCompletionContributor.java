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

import biz.paluch.dap.assistant.completion.ReleaseCompletionProvider;
import biz.paluch.dap.util.PatternConditions;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.toml.lang.psi.TomlInlineTable;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;
import org.toml.lang.psi.TomlTable;

/**
 * Completion contributor for Gradle version catalogs.
 *
 * @author Mark Paluch
 */
public class TomlCompletionContributor extends CompletionContributor {

	private static final ReleaseCompletionProvider provider = new ReleaseCompletionProvider();

	private static final PatternCondition<PsiFile> IS_VERSION_CATALOG = PatternConditions.conditional(
			"isVersionCatalog", GradleUtils::isVersionCatalog);

	public static final PatternCondition<TomlTable> PLUGIN_LIBRARY_TABLE = PatternConditions
			.conditional("pluginsOrLibraries", it -> {
				String keyName = TomlParser.getTomlTableName(it);
				return TomlParser.LIBRARIES.equals(keyName) || TomlParser.PLUGINS.equals(keyName);
			});

	public static final PatternCondition<TomlTable> VERSIONS_TABLE = PatternConditions
			.conditional("versionsTable", it -> {
				String keyName = TomlParser.getTomlTableName(it);
				return TomlParser.VERSIONS.equals(keyName);
			});

	public static final PatternCondition<TomlKeyValue> VERSION_OR_REF = PatternConditions
			.conditional("versionOrVersionRef", it -> {
				String keyName = TomlParser.getTomlKeyName(it);
				return TomlParser.VERSION_REF.equals(keyName) || TomlParser.VERSION.equals(keyName);
			});

	private static final ElementPattern<PsiElement> VERSION_KEY = PlatformPatterns.psiElement() //
			.inside(PlatformPatterns.psiElement(TomlLiteral.class)
					.withParent(PlatformPatterns.psiElement(TomlKeyValue.class).with(VERSION_OR_REF)
							.withParent(TomlInlineTable.class)
							.withAncestor(5, PlatformPatterns.psiElement(TomlTable.class).with(PLUGIN_LIBRARY_TABLE))))
			.inside(PlatformPatterns.psiFile().with(IS_VERSION_CATALOG));

	private static final ElementPattern<PsiElement> VERSION_PROPERTY = PlatformPatterns.psiElement() //
			.inside(PlatformPatterns.psiElement(TomlLiteral.class)
					.withParent(PlatformPatterns.psiElement(TomlKeyValue.class)
							.withParent(PlatformPatterns.psiElement(TomlTable.class).with(VERSIONS_TABLE))))
			.inside(PlatformPatterns.psiFile().with(IS_VERSION_CATALOG));

	public TomlCompletionContributor() {

		extend(CompletionType.BASIC, VERSION_KEY, provider);
		extend(CompletionType.BASIC, VERSION_PROPERTY, provider);
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		return ReleaseCompletionProvider.isVersionCharacter(typeChar)
				&& (VERSION_KEY.accepts(position) || VERSION_PROPERTY.accepts(position));
	}

	public static boolean isSupportedCompletionSite(PsiElement position) {
		return VERSION_KEY.accepts(position) || VERSION_PROPERTY.accepts(position);
	}

}
