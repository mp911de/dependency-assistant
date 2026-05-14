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

package biz.paluch.dap.maven;

import java.util.regex.MatchResult;

/**
 * Default {@link MatchResult} implementation.
 * 
 * @author Mark Paluch
 */
class DefaultMatchResult implements MatchResult {

	private final String match;

	private final int index;

	private final int endIndex;

	public DefaultMatchResult(String match, int index, int endIndex) {
		this.match = match;
		this.index = index;
		this.endIndex = endIndex;
	}

	@Override
	public int start() {
		return index;
	}

	@Override
	public int start(int group) {
		if (group == 0) {
			return start();
		}
		throw new IndexOutOfBoundsException();
	}

	@Override
	public int end() {
		return endIndex;
	}

	@Override
	public int end(int group) {
		if (group == 0) {
			return end();
		}
		throw new IndexOutOfBoundsException();
	}

	@Override
	public String group() {
		return match;
	}

	@Override
	public String group(int group) {
		if (group == 0) {
			return group();
		}
		throw new IndexOutOfBoundsException();
	}

	@Override
	public int groupCount() {
		return 0;
	}

	@Override
	public boolean hasMatch() {
		return true;
	}

}
