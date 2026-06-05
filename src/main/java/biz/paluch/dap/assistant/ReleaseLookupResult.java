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

import java.util.List;

import biz.paluch.dap.artifact.Release;
import org.jspecify.annotations.Nullable;

/**
 * Outcome of resolving releases for an artifact, carrying either the resolved
 * releases, a non-fatal lookup error, or both when a source partially succeeds.
 *
 * <p>Callers treat a {@literal null} error as success.
 *
 * @author Mark Paluch
 * @param error the lookup error message, or {@literal null} when release lookup
 * succeeded.
 * @param releases the releases that were resolved for the artifact.
 */
record ReleaseLookupResult(@Nullable String error, List<Release> releases) {

}
