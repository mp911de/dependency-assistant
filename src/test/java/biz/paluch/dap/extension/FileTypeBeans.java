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

package biz.paluch.dap.extension;

import java.util.LinkedHashMap;
import java.util.Map;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.YAMLParserDefinition;
import org.jspecify.annotations.Nullable;

/**
 * Registry of file extension to {@link FileType} mappings for file types that
 * may not be registered in light test fixtures even when the corresponding
 * bundled plugin is on the classpath.
 *
 * <p>Light fixtures load bundled plugins from the IDE installation but do not
 * always process every plugin module's {@code plugin.xml} file type
 * registrations. This registry provides the known mappings so that
 * {@link ProjectFileExtension} can register missing associations before project
 * files are created, ensuring files are parsed with the correct language.
 *
 * @author Mark Paluch
 * @see ProjectFileExtension
 */
class FileTypeBeans {

	private static final Map<String, LanguageFileType> EXTENSION_TO_FILE_TYPE = new LinkedHashMap<>();

	private static final Map<String, ParserDefinition> EXTENSION_TO_PARSER_DEFINITION = new LinkedHashMap<>();

	static {
		register(YAMLFileType.YML, new YAMLParserDefinition(), "yaml", "yml");
	}

	private static void register(LanguageFileType fileType, ParserDefinition parserDefinition, String... extensions) {
		for (String ext : extensions) {
			EXTENSION_TO_FILE_TYPE.put(ext.toLowerCase(), fileType);
			EXTENSION_TO_PARSER_DEFINITION.put(ext.toLowerCase(), parserDefinition);
		}
	}

	/**
	 * Find the {@link FileType} for the given file extension.
	 * @param extension the file extension (without dot), must not be
	 * {@literal null}.
	 * @return the file type, or {@literal null} if none is registered for this
	 * extension.
	 */
	static @Nullable FileType findFileTypeForExtension(String extension) {
		return EXTENSION_TO_FILE_TYPE.get(extension.toLowerCase());
	}

	/**
	 * Ensure a {@link ParserDefinition} is registered for the language of the file
	 * type associated with the given extension.
	 *
	 * <p>Light test fixtures may not register parser definitions of plugin content
	 * modules, causing files to be parsed as plain text. This method explicitly
	 * registers the parser definition for the language so the file is parsed with
	 * the correct PSI tree.
	 *
	 * @param extension the file extension (without dot), must not be
	 * {@literal null}.
	 */
	static void ensureParserDefinition(String extension) {

		LanguageFileType fileType = EXTENSION_TO_FILE_TYPE.get(extension.toLowerCase());
		ParserDefinition parserDefinition = EXTENSION_TO_PARSER_DEFINITION.get(extension.toLowerCase());
		if (fileType == null || parserDefinition == null) {
			return;
		}

		if (LanguageParserDefinitions.INSTANCE.forLanguage(fileType.getLanguage()) != null) {
			return;
		}

		ApplicationManager.getApplication().runWriteAction(() -> LanguageParserDefinitions.INSTANCE
				.addExplicitExtension(fileType.getLanguage(), parserDefinition));
	}

}
