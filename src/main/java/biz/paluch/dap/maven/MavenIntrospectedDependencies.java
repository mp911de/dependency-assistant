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

package biz.paluch.dap.maven;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.IntrospectedDependencies;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.StringUtils;

/**
 * Phase-scoped {@link IntrospectedDependencies} for Maven that promotes
 * property-backed declarations using scan-derived property metadata.
 *
 * <p>
 * Maven's two-phase population first collects a {@link DependencyCollector}
 * instance per POM and registers each through
 * {@link #register(DependencyCollector)}. Once all collectors are known, the
 * shared updater invokes {@link #complete(DependencyCollector)} for each
 * collector. Completion walks the union of declarations across the scan to
 * derive property-to-artifact associations and uses each collector's effective
 * property values to register usages for property-backed declarations,
 * including parent/child POM cases.
 *
 * <p>
 * Instances are scoped to a single Maven assistant run and are not shared with
 * other assistants.
 *
 * @author Mark Paluch
 */
class MavenIntrospectedDependencies implements IntrospectedDependencies {

	private final List<DependencyCollector> collectors = new ArrayList<>();

	/**
	 * Register a phase-one collector with this run so its declarations and
	 * effective property values contribute to scan-wide promotion.
	 * @param collector the phase-one collector to track; must not be
	 * {@literal null}.
	 */
	void register(DependencyCollector collector) {
		collectors.add(collector);
	}

	@Override
	public void complete(DependencyCollector collector) {

		Set<DependencyCollector> all = new LinkedHashSet<>(collectors);
		all.add(collector);

		Map<String, Set<ArtifactId>> propertyToArtifacts = new LinkedHashMap<>();
		for (DependencyCollector candidate : all) {
			for (DeclaredDependency declaration : candidate.getDeclarations()) {
				for (VersionSource versionSource : declaration.getVersionSources()) {
					if (versionSource instanceof VersionSource.VersionProperty property) {
						propertyToArtifacts
								.computeIfAbsent(property.getProperty(), k -> new LinkedHashSet<>())
								.add(declaration.getArtifactId());
					}
				}
			}
		}

		Map<String, String> values = collector.getPropertyValues();
		for (Map.Entry<String, Set<ArtifactId>> entry : propertyToArtifacts.entrySet()) {

			String propertyName = entry.getKey();
			String value = values.get(propertyName);
			if (!StringUtils.hasText(value)) {
				continue;
			}

			ArtifactVersion.from(value).ifPresent(version -> {
				for (ArtifactId artifactId : entry.getValue()) {
					if (collector.getUsage(artifactId) == null) {
						collector.registerUsage(artifactId, version, DeclarationSource.managed(),
								VersionSource.property(propertyName));
					}
				}
			});
		}
	}

}
