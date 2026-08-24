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
 * Version-control system behind a declared repository URL.
 *
 * <p>Parsed from the provider segment of a Maven {@code scm:<provider>:} URL or
 * from the npm {@code repository.type} field. Providers without dedicated link
 * handling (Mercurial, CVS, and other exotic systems) map to {@link #UNKNOWN}.
 *
 * @author Mark Paluch
 * @see RepositoryUrl
 */
public enum RepositoryType {

	/**
	 * Git repository.
	 */
	GIT,

	/**
	 * Subversion repository.
	 */
	SVN,

	/**
	 * Unrecognized or undeclared version-control system.
	 */
	UNKNOWN;

	/**
	 * Resolve the repository type from a declared provider name.
	 * @param provider the provider name from a Maven {@code scm:<provider>:} prefix
	 * or an npm {@code repository.type} field, or {@literal null} if undeclared.
	 * @return the matching repository type. Returns {@link #UNKNOWN} if the
	 * provider is {@literal null}, blank, or not recognized.
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
