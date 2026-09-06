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

package biz.paluch.dap.assistant.check;

import biz.paluch.dap.artifact.Releases;
import org.jspecify.annotations.Nullable;

/**
 * Release lookup outcome, including partial success across sources.
 * <p>A failed result has an error and empty release sets.
 *
 * @author Mark Paluch
 * @param error the failure message, or {@literal null} on success.
 * @param newReleases releases newly cached by this lookup, used for delta
 * vulnerability scans.
 */
public record ReleaseLookupResult(@Nullable String error, Releases releases, Releases newReleases) {

	public static ReleaseLookupResult of(Releases releases) {
		return of(releases, Releases.empty());
	}

	public static ReleaseLookupResult of(Releases releases, Releases newReleases) {
		return new ReleaseLookupResult(null, releases, newReleases);
	}

	public static ReleaseLookupResult failed(String error) {
		return new ReleaseLookupResult(error, Releases.empty(), Releases.empty());
	}

	public static ReleaseLookupResult empty() {
		return of(Releases.empty());
	}

}
