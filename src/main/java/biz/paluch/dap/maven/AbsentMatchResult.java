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
 * TODO
 * @author Mark Paluch
 */
enum AbsentMatchResult implements MatchResult {

	ABSENT;

	@Override
	public int start() {
		return 0;
	}

	@Override
	public int start(int group) {
		return 0;
	}

	@Override
	public int end() {
		return 0;
	}

	@Override
	public int end(int group) {
		return 0;
	}

	@Override
	public String group() {
		return "";
	}

	@Override
	public String group(int group) {
		return "";
	}

	@Override
	public int groupCount() {
		return 0;
	}

	@Override
	public boolean hasMatch() {
		return false;
	}

}
