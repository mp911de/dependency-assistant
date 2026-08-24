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

package biz.paluch.dap.support.yaml;

import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLScalar;

/**
 * Rendering style for a YAML scalar.
 *
 * <p>{@link #of(YAMLScalar)} detects the source scalar's style, and
 * {@link #wrap(String)} renders replacement content in that same style. Quoted
 * styles escape their delimiter syntax. {@link #NONE} leaves the replacement
 * unchanged and does not select a quoted style when the content requires one.
 *
 * @author Mark Paluch
 */
public enum QuoteStyle {

	/**
	 * Plain scalar style. Replacement content is returned unchanged.
	 */
	NONE {
		@Override
		public String wrap(String rawValue) {
			return rawValue;
		}
	},

	/**
	 * Single-quoted scalar style. Single quotes in the content are doubled.
	 */
	SINGLE {
		@Override
		public String wrap(String rawValue) {
			return "'" + rawValue.replace("'", "''") + "'";
		}
	},

	/**
	 * Double-quoted scalar style. Backslashes and double quotes in the content are
	 * escaped.
	 */
	DOUBLE {
		@Override
		public String wrap(String rawValue) {
			return "\"" + rawValue.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		}
	};

	/**
	 * Determine the rendering style of the given YAML scalar.
	 * @param scalar the scalar to inspect.
	 * @return the corresponding quoted style, or {@link #NONE} for a plain scalar.
	 */
	public static QuoteStyle of(YAMLScalar scalar) {

		if (!(scalar instanceof YAMLQuotedText quoted)) {
			return NONE;
		}
		return quoted.isSingleQuote() ? SINGLE : DOUBLE;
	}

	/**
	 * Render scalar content using this style.
	 * @param rawValue the unquoted scalar content to render.
	 * @return the rendered YAML scalar text.
	 */
	public abstract String wrap(String rawValue);

}
