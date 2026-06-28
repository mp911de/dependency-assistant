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

package biz.paluch.dap.assistant;

import com.intellij.openapi.util.text.StringUtil;

/**
 * Inline Markdown fragment for rendering text embedded in documentation HTML.
 *
 * <p>Supports balanced single-backtick code spans only. All text is
 * XML-escaped, and unbalanced backticks are rendered literally instead of
 * producing partial HTML markup.
 *
 * @author Mark Paluch
 */
class Markdown {

	private final String text;

	private final boolean balancedCodeFences;

	private Markdown(String text) {
		this.text = text;
		this.balancedCodeFences = hasBalancedCodeFences(text);
	}

	private static boolean hasBalancedCodeFences(String text) {
		int fences = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '`') {
				fences++;
			}
		}
		return fences != 0 && fences % 2 == 0;
	}

	/**
	 * Create a Markdown fragment and determine whether its code fences are
	 * balanced.
	 * @param text the advisory text to render.
	 * @return a Markdown fragment for the given text.
	 */
	static Markdown of(String text) {
		return new Markdown(text);
	}

	/**
	 * Render this fragment as documentation HTML.
	 * @return escaped HTML with balanced single-backtick spans rendered as
	 * {@code <code>} elements.
	 */
	String toHtml() {

		if (!balancedCodeFences) {
			return StringUtil.escapeXmlEntities(text);
		}

		StringBuilder result = new StringBuilder();
		boolean code = false;
		int segmentStart = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) != '`') {
				continue;
			}
			result.append(StringUtil.escapeXmlEntities(text.substring(segmentStart, i)));
			result.append(code ? "</code>" : "<code>");
			code = !code;
			segmentStart = i + 1;
		}
		result.append(StringUtil.escapeXmlEntities(text.substring(segmentStart)));
		return result.toString();
	}

	@Override
	public String toString() {
		return text;
	}

}
