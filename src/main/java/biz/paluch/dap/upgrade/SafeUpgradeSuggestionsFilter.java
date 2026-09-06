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

import java.util.LinkedHashMap;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.UpgradeStrategy;
import org.jspecify.annotations.Nullable;

/**
 * Add the lowest newer release explicitly known to be clean for a vulnerable
 * dependency.
 * <p>Selection stays in the current versioning scheme but may cross major or
 * minor lines. Missing vulnerability results do not establish safety.
 *
 * @author Mark Paluch
 */
// TODO: consider successor scheme
class SafeUpgradeSuggestionsFilter implements UpgradeSuggestionsFilter {

	@Override
	public UpgradeSuggestions filter(Dependency dependency, Releases releases,
			VulnerabilityRepository vulnerabilities, DependencyRule rule, UpgradeSuggestions suggestions) {

		Release safeVersion = resolveSafeVersion(dependency, releases, vulnerabilities);
		if (safeVersion != null) {

			Map<UpgradeStrategy, UpgradeSuggestion> newSuggestions = new LinkedHashMap<>();
			newSuggestions.put(UpgradeStrategy.SAFE, UpgradeSuggestion.of(UpgradeStrategy.SAFE, safeVersion));
			newSuggestions.putAll(suggestions.toMap());
			return UpgradeSuggestions.of(newSuggestions);
		}

		return suggestions;
	}

	private static @Nullable Release resolveSafeVersion(Dependency dependency, Releases releases,
			VulnerabilityRepository vulnerabilities) {

		ArtifactVersion current = dependency.getCurrentVersion();
		if (!vulnerabilities.getVulnerabilities(current).isVulnerable()) {
			return null;
		}

		return releases.inScheme(current.scheme()).reversed().stream()
				.filter(release -> release.isNewer(current))
				.filter(release -> vulnerabilities.getVulnerabilities(release.getVersion()).isClean())
				.findFirst().orElse(null);
	}

}
