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

package biz.paluch.dap.util;

import java.awt.Font;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;

/**
 * Safe access to the global {@link EditorColorsScheme} and the values derived
 * from it, such as {@link TextAttributes} for a {@link TextAttributesKey} and
 * the editor font.
 *
 * <p>Every accessor tolerates an unavailable platform: when the
 * {@link Application} or {@link EditorColorsManager} is not initialized (for
 * example in plain unit tests, or during early startup), the scheme is treated
 * as absent and the caller-supplied fallback is returned instead of throwing.
 * This keeps static initializers and renderers that read scheme colors from
 * depending on a fully booted IDE.
 *
 * @author Mark Paluch
 */
public abstract class EditorSchemes {

	private static final EditorColorsScheme SCHEME = globalScheme();

	/**
	 * Return the global editor color scheme.
	 *
	 * @return the global scheme, or a fresh {@link DefaultColorsScheme} when the
	 * {@link Application} or {@link EditorColorsManager} is not initialized; never
	 * {@literal null}.
	 */
	private static EditorColorsScheme globalScheme() {
		Application application = ApplicationManager.getApplication();
		if (application == null) {
			return new DefaultColorsScheme();
		}
		EditorColorsManager manager = EditorColorsManager.getInstance();
		return manager != null ? manager.getGlobalScheme() : new DefaultColorsScheme();
	}

	/**
	 * Return the {@link TextAttributes} the global scheme assigns to the given key.
	 *
	 * @param key the attributes key to resolve.
	 * @param fallback the value to return when no scheme attributes are available.
	 * @return the scheme attributes for the key, or {@code fallback} when the
	 * scheme is unavailable or defines no attributes for the key.
	 */
	public static TextAttributes attributes(TextAttributesKey key, TextAttributes fallback) {
		EditorColorsScheme scheme = globalScheme();
		TextAttributes attributes = scheme.getAttributes(key);
		return attributes != null ? attributes : fallback;
	}

	/**
	 * Create a {@link Font} in the global scheme's editor font, falling back to the
	 * platform monospaced font when no scheme is available.
	 *
	 * @param style the AWT font style, for example {@link Font#PLAIN}.
	 * @param size the font size in points.
	 * @return a font using the editor font family.
	 */
	public static Font editorFont(int style, int size) {
		return new Font(SCHEME.getEditorFontName(), style, size);
	}

}
