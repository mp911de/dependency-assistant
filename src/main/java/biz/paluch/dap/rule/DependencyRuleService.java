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

package biz.paluch.dap.rule;

import com.intellij.openapi.project.Project;

/**
 * Resolver for {@link DependencyRule}s.
 *
 * @author Mark Paluch
 * @see DependencyfileService
 */
public interface DependencyRuleService {

	/**
	 * Return a resolver that always returns {@link DependencyRule#absent()}.
	 */
	static DependencyRuleService absent() {
		return AbsentDependencyRuleService.INSTANCE;
	}

	static DependencyRuleService getInstance(Project project) {
		return project.getService(DependencyfileService.class);
	}

	/**
	 * Resolve the governing rule, or {@link DependencyRule#absent()} if none
	 * applies.
	 */
	DependencyRule resolve(ResolutionContext context);

}
