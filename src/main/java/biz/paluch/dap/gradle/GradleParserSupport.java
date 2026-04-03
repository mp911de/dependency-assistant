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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactUsage;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;

import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

/**
 * @author Mark Paluch
 */
abstract class GradleParserSupport extends BuildFileParserSupport {

	public GradleParserSupport(DependencyCollector collector) {
		super(collector);
	}

	static ArtifactId parseArtifactId(String module) {
		String[] split = module.split(":");
		return split.length >= 2 ? ArtifactId.of(split[0], split[1]) : ArtifactId.of(module, module);
	}

	// -------------------------------------------------------------------------
	// Shared helpers
	// -------------------------------------------------------------------------

	/**
	 * Parses a {@code group:artifact:version} GAV string, resolving any {@code ${prop}} references.
	 */
	@Nullable
	GradleDependency parseGav(@Nullable String raw) {

		if (raw == null) {
			return null;
		}

		String[] parts = raw.split(":");
		if (parts.length < 3) {
			return null;
		}

		String group = resolveValue(parts[0].trim());
		String artifact = resolveValue(parts[1].trim());
		String version = parts[2].trim();
		String propertyName;

		if (group == null || artifact == null || !StringUtils.hasText(version)) {
			return null;
		}

		ArtifactId artifactId = ArtifactId.of(group, artifact);
		if (version.startsWith("${") && version.endsWith("}")) {
			propertyName = version.substring(2, version.length() - 1);
			return new PropertyManagedDependency(artifactId, propertyName, VersionSource.property(propertyName));
		}

		return new SimpleDependency(artifactId, version, VersionSource.declared(version));
	}

	void register(GradleDependency dependency, DeclarationSource declarationSource) {

		if (dependency instanceof PropertyManagedDependency pmd) {
			getCollector().add(pmd.id(), new ArtifactUsage(declarationSource, pmd.getVersionSource()));

			String version = getProperty(pmd.property());
			if (StringUtils.hasText(version)) {
				register(pmd.id(), version, declarationSource, pmd.versionSource);
			}
		}

		if (dependency instanceof SimpleDependency sd) {
			register(sd.id(), sd.version(), declarationSource, sd.versionSource());
		}
	}

	void register(ArtifactId id, @Nullable String version, DeclarationSource declarationSource) {

		if (!StringUtils.hasText(version)) {
			return;
		}

		VersionSource versionSource = VersionSource.declared(version);
		if (version.startsWith("${") && version.endsWith("}")) {
			String property = version.substring(2, version.length() - 1);
			version = resolveValue(property);

			versionSource = VersionSource.property(version);
			getCollector().add(id, new ArtifactUsage(declarationSource, versionSource));
		}

		register(id, version, declarationSource, versionSource);
	}

	private void register(ArtifactId id, String rawVersion, DeclarationSource declarationSource,
			VersionSource versionSource) {
		ArtifactVersion.from(rawVersion)
				.ifPresent(version -> getCollector().registerUpdateCandidate(id, version, declarationSource, versionSource));
	}

	GradleDependency toGradleDependency(GroovyDslUtils.VersionLocation versionLocation) {

		if (versionLocation.isPropertyReference()) {
			return new PropertyManagedDependency(versionLocation.artifactId(), versionLocation.rawVersion(),
					VersionSource.property(versionLocation.rawVersion()));
		}

		return new SimpleDependency(versionLocation.artifactId(), versionLocation.rawVersion(),
				VersionSource.declared(versionLocation.rawVersion()));
	}

	/**
	 * Strategy interface for representing Gradle dependencies.
	 */
	interface GradleDependency {

		ArtifactId getId();

		VersionSource getVersionSource();
	}

	record SimpleDependency(ArtifactId id, String version, VersionSource versionSource) implements GradleDependency {
		@Override
		public ArtifactId getId() {
			return id();
		}

		@Override
		public VersionSource getVersionSource() {
			return versionSource();
		}
	}

	record PropertyManagedDependency(ArtifactId id, String property,
			VersionSource versionSource) implements GradleDependency {
		@Override
		public ArtifactId getId() {
			return id();
		}

		@Override
		public VersionSource getVersionSource() {
			return versionSource();
		}
	}

}
