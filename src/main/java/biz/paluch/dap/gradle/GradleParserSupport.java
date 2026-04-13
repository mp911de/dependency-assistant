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

import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.gradle.GradleDependency.PropertyManagedDependency;
import biz.paluch.dap.gradle.GradleDependency.SimpleDependency;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Common base class for Gradle build file parsers.
 *
 * @author Mark Paluch
 */
abstract class GradleParserSupport extends BuildFileParserSupport implements PropertyResolver {

	public GradleParserSupport(DependencyCollector collector) {
		super(collector);
	}

	// TODO
	public static ArtifactId parseArtifactId(String module) {
		String[] split = module.split(":");
		return split.length >= 2 ? ArtifactId.of(split[0], split[1]) : ArtifactId.of(module, module);
	}

	@Override
	public @Nullable String getProperty(String key) {
		return getPropertyMap().get(key);
	}

	protected abstract Map<String, String> getPropertyMap();

	// -------------------------------------------------------------------------
	// Shared helpers
	// -------------------------------------------------------------------------

	/**
	 * Parses a {@code group:artifact:version} GAV string, resolving any
	 * {@code ${prop}} references.
	 */
	@Nullable
	GradleDependency parseGav(@Nullable String raw) {

		if (raw == null) {
			return null;
		}

		return GradleDependency.parse(raw, this);
	}

	void register(GradleDependency dependency, DeclarationSource declarationSource) {

		if (dependency instanceof PropertyManagedDependency pmd) {

			String version = getProperty(pmd.property());
			if (StringUtils.hasText(version)) {
				ArtifactVersion.from(version)
						.ifPresent(it -> getCollector().registerUsage(pmd.id(), it, declarationSource,
								pmd.versionSource()));
			}
		}

		if (dependency instanceof SimpleDependency sd) {

			ArtifactVersion.from(sd.version())
					.ifPresent(it -> getCollector().registerUsage(sd.id(), it, declarationSource,
							sd.versionSource()));
		}

		getCollector().registerDeclaration(dependency.getId(), declarationSource, dependency.getVersionSource());
	}

}
