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

package biz.paluch.dap.gradle;

import java.util.List;

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Extracted Gradle version constraint.
 *
 * @author Mark Paluch
 */
interface GradleVersionConstraint {

	String PREFER = "prefer";

	String STRICTLY = "strictly";

	String REQUIRE = "require";

	/**
	 * Constraint names from strongest to weakest. {@code prefer} applies only when
	 * no stronger concrete version is declared.
	 */
	List<String> PRECEDENCE = List.of(STRICTLY, REQUIRE, PREFER);

	String getVersion();

	default boolean hasText() {
		return StringUtils.hasText(getVersion());
	}

	default boolean isRange() {
		return GradleUtils.isVersionRange(getVersion());
	}

	static boolean isConstraint(@Nullable String call) {
		return call != null && PRECEDENCE.contains(call);
	}

}
