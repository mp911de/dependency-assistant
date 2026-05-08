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

package biz.paluch.dap.gradle;

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Contract for a Gradle version constraint such as {@code prefer} or
 * {@code strictly}.
 *
 * <p>Gradle allows dependency declarations to express version intent through
 * named constraint operators within a {@code version { ... }} block. This
 * interface models the extracted constraint value and exposes common helpers
 * used by parsers and lookup-site infrastructure.
 *
 * <p>Implementations are expected to provide the raw version text while the
 * default methods expose higher-level predicates for text presence and range
 * detection.
 *
 * @author Mark Paluch
 */
interface GradleVersionConstraint {

	/**
	 * Constraint name for Gradle's {@code prefer(...)} version declaration.
	 */
	String PREFER = GradleUtils.PREFER;

	/**
	 * Constraint name for Gradle's {@code strictly(...)} version declaration.
	 */
	String STRICTLY = GradleUtils.STRICTLY;

	/**
	 * Return the declared version text for this constraint.
	 *
	 * @return the declared version text.
	 */
	String getVersion();

	/**
	 * Return whether this constraint declares non-empty version text.
	 *
	 * @return {@literal true} if {@link #getVersion()} contains text.
	 */
	default boolean hasText() {
		return StringUtils.hasText(getVersion());
	}

	/**
	 * Return whether this constraint declares a version range.
	 *
	 * @return {@literal true} if the declared version uses Gradle range syntax.
	 * @see GradleUtils#isVersionRange(String)
	 */
	default boolean isRange() {
		return GradleUtils.isVersionRange(getVersion());
	}

	/**
	 * Return whether the given call name represents a supported Gradle version
	 * constraint.
	 *
	 * @param call the call name to inspect.
	 * @return {@literal true} if the call matches a supported constraint name.
	 */
	static boolean isConstraint(@Nullable String call) {
		return PREFER.equals(call) || STRICTLY.equals(call);
	}

}
