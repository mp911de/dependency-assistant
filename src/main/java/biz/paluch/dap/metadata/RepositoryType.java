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

package biz.paluch.dap.metadata;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Version-control system identified by repository metadata.
 *
 * @author Mark Paluch
 * @see RepositoryUrl
 */
public enum RepositoryType {

	GIT,

	SVN,

	/**
	 * Unsupported or undeclared version-control system.
	 */
	UNKNOWN;

	/**
	 * Resolve a provider name without regard to case or surrounding whitespace.
	 * @return {@link #UNKNOWN} if the provider is absent or unsupported.
	 */
	public static RepositoryType of(@Nullable String provider) {

		if (provider == null || provider.isBlank()) {
			return UNKNOWN;
		}

		return switch (provider.trim().toLowerCase(Locale.ROOT)) {
		case "git" -> GIT;
		case "svn" -> SVN;
		default -> UNKNOWN;
		};
	}

}
