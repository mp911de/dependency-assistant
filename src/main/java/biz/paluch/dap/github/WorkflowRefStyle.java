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

package biz.paluch.dap.github;

import java.util.regex.Pattern;

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Classification of a workflow {@code uses:} ref string.
 *
 * <p>The ref after {@code @} in a {@code uses:} declaration can take three
 * forms: a SHA-1 hash or a bare semantic version.
 *
 * @author Mark Paluch
 */
enum WorkflowRefStyle {

	/**
	 * A full lowercase hex SHA-1 commit hash, e.g.
	 * {@code be666c2fcd27ec809703dec50e508c2fdc7f6654}.
	 */
	SHA,

	/**
	 * A semantic version without a leading {@code v} prefix, e.g. {@code 1.2.3}.
	 */
	VERSION;

	private static final Pattern FULL_SHA = Pattern.compile("^[0-9a-f]{4,40}$");

	/**
	 * Classify the given ref string. Defaults to {@link #SHA} if the ref is empty.
	 * @param ref the raw ref as written in the YAML file.
	 * @return the style.
	 */
	static WorkflowRefStyle from(@Nullable String ref) {

		if (StringUtils.isEmpty(ref) || FULL_SHA.matcher(ref).matches()) {
			return SHA;
		}

		return VERSION;
	}

}
