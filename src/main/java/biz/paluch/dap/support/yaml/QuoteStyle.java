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
 * YAML scalar quoting for replacements.
 * <p>Quoted styles escape their delimiters. {@link #NONE} preserves plain text
 * and does not add quotes when new content would require them.
 *
 * @author Mark Paluch
 */
public enum QuoteStyle {

	NONE {
		@Override
		public String wrap(String rawValue) {
			return rawValue;
		}
	},

	SINGLE {
		@Override
		public String wrap(String rawValue) {
			return "'" + rawValue.replace("'", "''") + "'";
		}
	},

	DOUBLE {
		@Override
		public String wrap(String rawValue) {
			return "\"" + rawValue.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		}
	};

	/**
	 * Detect the scalar's quote style, or {@link #NONE} for plain text.
	 */
	public static QuoteStyle of(YAMLScalar scalar) {

		if (!(scalar instanceof YAMLQuotedText quoted)) {
			return NONE;
		}
		return quoted.isSingleQuote() ? SINGLE : DOUBLE;
	}

	/**
	 * Quote unquoted scalar content using this style.
	 */
	public abstract String wrap(String rawValue);

}
