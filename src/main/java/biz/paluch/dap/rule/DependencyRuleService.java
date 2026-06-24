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

import com.intellij.openapi.project.Project;

/**
 * Resolver for {@link DependencyRule}s.
 *
 * @author Mark Paluch
 * @see DependencyfileService
 */
public interface DependencyRuleService {

	/**
	 * Return a resolver that resolves every artifact to
	 * {@link DependencyRule#absent()}.
	 *
	 * @return the absent rule resolver.
	 */
	static DependencyRuleService absent() {
		return AbsentDependencyRuleService.INSTANCE;
	}

	/**
	 * Return the rule service for the given project.
	 * @param project the project.
	 * @return the project rule service.
	 */
	static DependencyRuleService getInstance(Project project) {
		return project.getService(DependencyfileService.class);
	}

	/**
	 * Resolve an effective {@link DependencyRule} for the given resolution context.
	 *
	 * @param context the resolution input.
	 * @return the governing dependency rule, or {@link DependencyRule#absent()}
	 * when no rule applies.
	 */
	DependencyRule resolve(ResolutionContext context);

}
