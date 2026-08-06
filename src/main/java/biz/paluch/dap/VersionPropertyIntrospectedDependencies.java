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

package biz.paluch.dap;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.util.StringUtils;

/**
 * {@link IntrospectedDependencies} that promotes a declaration backed by a
 * version property to a usage, for build ecosystems where the property and the
 * declaration it versions can live in different build files.
 *
 * <p>A declaration whose version comes from a property is registered without a
 * usage during collection, because the version literal is not available in the
 * declaring file. This handle closes that gap: it indexes every property-backed
 * declaration seen anywhere in the scan and registers a managed usage on each
 * collector that resolves the corresponding property.
 *
 * <p>Instances are seeded with the collectors already held by
 * {@link biz.paluch.dap.state.StateService#getCollectors() the runtime
 * dependency state} and are updated through
 * {@link #register(ProjectId, DependencyCollector)} as the current pass
 * proceeds, so a pass that re-collects only a single build file still sees what
 * the remaining modules declared. A collector registered for a project identity
 * replaces the seeded one, which keeps the current pass authoritative over
 * previously stored state.
 *
 * <p>Promotion never overwrites an existing usage and never reads the
 * persistent cache, so the result does not depend on a previous pass having
 * been stored.
 *
 * @author Mark Paluch
 * @see IntrospectedDependencies
 * @see DependencyCollector#getPropertyValues()
 */
public class VersionPropertyIntrospectedDependencies implements IntrospectedDependencies {

	private final Map<ProjectId, DependencyCollector> collectors = new LinkedHashMap<>();

	/**
	 * Create a handle seeded with the collectors already known for this project.
	 *
	 * @param known the collectors held by the runtime dependency state, keyed by
	 * project identity; retained by reference to their collectors, not copied.
	 */
	public VersionPropertyIntrospectedDependencies(Map<ProjectId, DependencyCollector> known) {
		this.collectors.putAll(known);
	}

	/**
	 * Register a collector of the current pass, replacing any collector previously
	 * known for the same project identity.
	 *
	 * @param projectId the identity of the module the collector belongs to.
	 * @param collector the collector to track.
	 */
	public void register(ProjectId projectId, DependencyCollector collector) {
		collectors.put(projectId, collector);
	}

	@Override
	public void complete(DependencyCollector collector) {

		Set<DependencyCollector> all = new LinkedHashSet<>();
		all.add(collector);
		all.addAll(collectors.values());

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

		Map<String, String> propertyValues = collector.getPropertyValues();
		for (Map.Entry<String, Set<ArtifactId>> entry : propertyToArtifacts.entrySet()) {

			String propertyName = entry.getKey();
			String value = propertyValues.get(propertyName);
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
