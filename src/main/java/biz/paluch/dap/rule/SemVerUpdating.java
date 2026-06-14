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

package biz.paluch.dap.rule;

/**
 * Controls whether semver-based upgrade strategy limits are derived from the
 * project version when resolving a branch rule.
 *
 * <p>The state is determined from the {@code semver} property in
 * {@code dependencyfile.json}: {@link #ENABLED} when {@code "semver": true},
 * {@link #DISABLED} when {@code "semver": false} or when no descriptor is
 * present, and {@link #INFERRED} when the property is absent but a descriptor
 * exists (the default behavior).
 *
 * @author Mark Paluch
 * @see DependencyRules#resolveBranchRule
 */
enum SemVerUpdating {

	/**
	 * Semver-based upgrade strategy limits are inferred from the project version.
	 * This is the default when {@code dependencyfile.json} is present but does not
	 * declare a {@code semver} property.
	 */
	INFERRED,

	/**
	 * Semver-based upgrade strategy limits are explicitly enabled via
	 * {@code "semver": true} in {@code dependencyfile.json}.
	 */
	ENABLED,

	/**
	 * Semver-based upgrade strategy limits are disabled. Set via
	 * {@code "semver": false} in {@code dependencyfile.json}, or when no
	 * {@code dependencyfile.json} is present.
	 */
	DISABLED

}
