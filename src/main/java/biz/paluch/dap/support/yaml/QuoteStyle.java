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

package biz.paluch.dap.support.yaml;

import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLScalar;

/**
 * YAML scalar quoting style.
 *
 * <p>Detected from a {@link YAMLScalar} and used to wrap replacement text so the
 * original quoting form survives a PSI rewrite. {@link #wrap(String)} applies
 * YAML quote-escaping rules so a replacement value that contains the wrapping
 * quote character round-trips through the YAML parser.
 *
 * @author Mark Paluch
 */
public enum QuoteStyle {

	/**
	 * Unquoted plain scalar.
	 */
	NONE {
		@Override
		public String wrap(String rawValue) {
			return rawValue;
		}
	},

	/**
	 * Single-quoted scalar. Single quotes inside the value are escaped by
	 * doubling them, per the YAML 1.2 single-quoted style.
	 */
	SINGLE {
		@Override
		public String wrap(String rawValue) {
			return "'" + rawValue.replace("'", "''") + "'";
		}
	},

	/**
	 * Double-quoted scalar. Backslashes and double quotes inside the value are
	 * escaped, per the YAML 1.2 double-quoted style.
	 */
	DOUBLE {
		@Override
		public String wrap(String rawValue) {
			return "\"" + rawValue.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		}
	};

	/**
	 * Detect the quoting style of a YAML scalar.
	 * @param scalar the scalar to inspect; must not be {@literal null}.
	 * @return the detected style; never {@literal null}.
	 */
	public static QuoteStyle of(YAMLScalar scalar) {

		if (!(scalar instanceof YAMLQuotedText quoted)) {
			return NONE;
		}
		return quoted.isSingleQuote() ? SINGLE : DOUBLE;
	}

	/**
	 * Wrap a raw value in the quoting characters for this style, escaping any
	 * occurrences of the wrapping quote character per YAML rules.
	 * @param rawValue the unwrapped value; must not be {@literal null}.
	 * @return the value formatted for direct insertion into a YAML scalar.
	 */
	public abstract String wrap(String rawValue);

}
