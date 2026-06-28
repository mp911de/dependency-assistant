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

package biz.paluch.dap.upgrade;

/**
 * Strategy that refines the {@link UpgradeSuggestions} computed for a
 * {@link DependencyUpgradeSubject}, for example by adding a remediation target
 * or dropping suggestions a {@link biz.paluch.dap.rule.DependencyRule}
 * disallows.
 *
 * @author Mark Paluch
 */
@FunctionalInterface
public interface UpgradeSuggestionsFilter {

	/**
	 * Refine the suggestions for the given subject.
	 *
	 * @param subject the dependency under consideration with its releases,
	 * vulnerabilities, and rule.
	 * @param suggestions the suggestions to refine.
	 * @return the refined suggestions; never {@literal null}.
	 */
	UpgradeSuggestions filter(DependencyUpgradeSubject subject, UpgradeSuggestions suggestions);

}
