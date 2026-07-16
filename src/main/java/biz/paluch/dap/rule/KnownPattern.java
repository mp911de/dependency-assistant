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

package biz.paluch.dap.rule;

import java.util.function.Predicate;
import java.util.regex.Pattern;

class KnownPattern implements Predicate<String> {

	public static final KnownPattern ANY = new KnownPattern("*");

	private final Pattern glob;

	private final String pattern;

	private KnownPattern(String pattern) {
		this.glob = Pattern.compile(Pattern.quote(pattern).replace("*", "\\E.*\\Q"));
		this.pattern = pattern;
	}

	public static KnownPattern of(String pattern) {

		if ("*".equals(pattern)) {
			return ANY;
		}
		return new KnownPattern(pattern);
	}

	public String getPattern() {
		return pattern;
	}

	@Override
	public boolean test(String s) {
		return this == ANY || glob.matcher(s).matches();
	}

	@Override
	public String toString() {
		return pattern;
	}

}
