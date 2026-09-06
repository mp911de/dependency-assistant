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

package biz.paluch.dap.artifact;

import java.util.regex.Pattern;

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Rendering style of a declared Git ref.
 * <p>This is a textual classification, not Git ref resolution. Branches and
 * tags can both use version-style rendering.
 *
 * @author Mark Paluch
 */
public enum RefStyle {

	/**
	 * A lowercase hexadecimal commit SHA or SHA prefix, e.g.
	 * {@code be666c2fcd27ec809703dec50e508c2fdc7f6654} or {@code be666c2f}.
	 */
	SHA,

	/**
	 * A non-SHA ref rendered directly as a version-style value.
	 */
	VERSION;

	private static final Pattern FULL_SHA = Pattern.compile("^[0-9a-f]{4,40}$");

	/**
	 * Classify the raw ref text. Absent or blank refs default to {@link #SHA}.
	 */
	public static RefStyle from(@Nullable String ref) {
		return StringUtils.isEmpty(ref) || FULL_SHA.matcher(ref).matches() ? SHA : VERSION;
	}

}
