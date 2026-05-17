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

package biz.paluch.dap.artifact;

import java.util.regex.Pattern;

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Classification of a Git ref string by rendering style.
 *
 * <p>
 * The ref text after {@code @} in a {@code uses:} declaration, or after
 * {@code #} in an NPM Git URL, is classified by the rendering model rather than
 * by Git semantics. Hex SHA prefixes are treated as SHA-pinned refs. All other
 * non-empty refs, including tags, branches, and semantic versions with or
 * without a {@code v} prefix, are rendered as version-style refs.
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
	 * Determine a {@link RefStyle} from the given ref string.
	 * <p>
	 * An empty or {@literal null} ref defaults to {@link #SHA}, matching the
	 * conservative behavior for declarations that cannot reveal their original
	 * style. Whitespace-only input also classifies as {@link #SHA} because
	 * {@link StringUtils#isEmpty(String)} treats blank input as empty.
	 * @param ref the raw ref as written in the build file; can be {@literal null}.
	 * @return the resulting {@code RefStyle}; guaranteed to be not {@literal null}.
	 */
	public static RefStyle from(@Nullable String ref) {
		return StringUtils.isEmpty(ref) || FULL_SHA.matcher(ref).matches() ? SHA : VERSION;
	}

}
