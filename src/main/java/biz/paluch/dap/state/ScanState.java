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

package biz.paluch.dap.state;

/**
 * Vulnerability scan lifecycle. Only {@link #SCANNED} can yield a clean or
 * vulnerable result. All other states remain unknown.
 *
 * @author Mark Paluch
 * @see CachedRelease#scanState()
 */
public enum ScanState {

	/**
	 * Never submitted to a vulnerability source.
	 */
	NEVER_SCANNED,

	/**
	 * No data received yet. Further attempts are allowed.
	 */
	ATTEMPTED,

	/**
	 * Retry budget exhausted without data. The result remains unknown.
	 */
	UNRESOLVABLE,

	/**
	 * Completed with a clean or vulnerable result.
	 */
	SCANNED

}
