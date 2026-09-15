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

package biz.paluch.dap.plan;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.HasPackageIdentity;
import biz.paluch.dap.checker.Vulnerabilities;

/**
 * Strategy interface for creating planned dependency upgrades.
 *
 * @author Mark Paluch
 */
public interface DependencyUpgradeSource extends HasPackageIdentity {

	/**
	 * Return the dependency with its current version, declaration sources, and
	 * version sources. Capture does not modify these facts.
	 */
	Dependency getDependency();

	/**
	 * Return the stable integration id used to restore the dependency.
	 */
	String getAssistantId();

	/**
	 * Return the rule-provided name, or an empty string when unnamed. A
	 * rule-provided name takes precedence over remembered plan names.
	 */
	String getDependencyName();

	/**
	 * Return vulnerability facts for the supplied current or target version.
	 * Capture compares these facts to classify the planned upgrade.
	 */
	Vulnerabilities getVulnerabilities(ArtifactVersion version);

}
