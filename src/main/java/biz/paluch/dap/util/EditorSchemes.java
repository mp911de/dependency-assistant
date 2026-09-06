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

package biz.paluch.dap.util;

import java.awt.Font;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jspecify.annotations.Nullable;

/**
 * Editor colors and fonts that remain usable before the IDE is initialized.
 * Each lookup reads the current scheme and falls back if it is unavailable.
 *
 * @author Mark Paluch
 */
public abstract class EditorSchemes {

	private static @Nullable EditorColorsScheme globalScheme() {

		Application application = ApplicationManager.getApplication();
		if (application == null) {
			return null;
		}
		EditorColorsManager manager = EditorColorsManager.getInstance();
		return manager != null ? manager.getGlobalScheme() : null;
	}

	/**
	 * Return the current scheme's attributes, or the fallback if the scheme or
	 * attributes are unavailable.
	 */
	public static TextAttributes attributes(TextAttributesKey key, TextAttributes fallback) {

		EditorColorsScheme scheme = globalScheme();
		if (scheme == null) {
			return fallback;
		}
		TextAttributes attributes = scheme.getAttributes(key);
		return attributes != null ? attributes : fallback;
	}

	/**
	 * Use the editor font family, falling back to the platform monospaced font.
	 */
	public static Font editorFont(int style, int size) {

		EditorColorsScheme scheme = globalScheme();
		return new Font(scheme != null ? scheme.getEditorFontName() : Font.MONOSPACED, style, size);
	}

}
