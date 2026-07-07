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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.rule.DependencyRule;

/**
 * Subject for upgrading a dependency. The value object represents the
 * dependency, its releases, the vulnerabilities, and the governing
 * {@link DependencyRule}.
 *
 * @author Mark Paluch
 */
public class DependencyUpgradeSubject {

	private final Dependency dependency;

	private final Releases releases;

	private final VulnerabilityRepository vulnerabilities;

	private final DependencyRule rule;

	private DependencyUpgradeSubject(Dependency dependency, Releases releases,
			VulnerabilityRepository vulnerabilities, DependencyRule rule) {
		this.dependency = dependency;
		this.releases = releases;
		this.vulnerabilities = vulnerabilities;
		this.rule = rule;
	}

	/**
	 * Create a new subject for upgrading the given dependency.
	 */
	public static DependencyUpgradeSubject of(Dependency dependency, Releases releases,
			VulnerabilityRepository vulnerabilities, DependencyRule rule) {
		return new DependencyUpgradeSubject(dependency, releases.withVersion(dependency.getCurrentVersion()),
				vulnerabilities, rule);
	}

	public boolean isVulnerable() {
		return getVulnerabilities(getDependency().getCurrentVersion()).isVulnerable();
	}

	public Vulnerabilities getVulnerabilities(ArtifactVersion version) {
		return vulnerabilities.getVulnerabilities(version);
	}

	public Dependency getDependency() {
		return dependency;
	}

	public Releases getReleases() {
		return releases;
	}

	public VulnerabilityRepository getVulnerabilities() {
		return vulnerabilities;
	}

	public DependencyRule getRule() {
		return rule;
	}

}
