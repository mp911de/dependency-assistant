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

package biz.paluch.dap.state;

/**
 * Lifecycle of one {@link CachedRelease}'s vulnerability check, derived from
 * its persisted scan field.
 *
 * <p>Only {@link #SCANNED} yields clean or vulnerable scan results; every other
 * state reads as unknown. The state is the scan-side axis (how far we got
 * trying to obtain vulnerabilities), distinct from the result itself (what we
 * know).
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
	 * Submitted but the source has returned no data so far; still within the scan
	 * attempt budget and will be retried.
	 */
	ATTEMPTED,

	/**
	 * The scan attempt budget is spent and the source never returned data; reads as
	 * unknown and is no longer requested.
	 */
	UNRESOLVABLE,

	/**
	 * Successfully scanned; carries a real scan timestamp and clean or vulnerable
	 * scan results.
	 */
	SCANNED

}
