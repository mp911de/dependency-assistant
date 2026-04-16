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
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Common base class for Gradle build file parsers.
 *
 * @author Mark Paluch
 */
abstract class GradleParserSupport extends BuildFileParserSupport implements PropertyResolver {

	public GradleParserSupport(DependencyCollector collector) {
		super(collector);
	}

	@Override
	public @Nullable String getProperty(String key) {
		return getPropertyMap().get(key);
	}

	protected abstract Map<String, String> getPropertyMap();

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

	/**
	 * Map-style dependency declaration in the style of: <pre class="code">
	 *     implementation group: 'com.google.guava', name: 'guava', version: '33.0.0-jre', classifier: 'android'
	 * </pre>
	 */
	record NamedDependencyDeclaration(PsiFile buildFile, @Nullable String id, @Nullable String group,
			@Nullable String artifact,
			@Nullable String versionProperty,
			@Nullable String version, PsiElement declaration, @Nullable PsiElement versionLiteral) {

		/**
		 * Check whether the declaration is complete (having id and version information
		 * or group and artifact with version information).
		 */
		public boolean isComplete() {

			if (versionLiteral == null || (StringUtils.isEmpty(versionProperty) && StringUtils.isEmpty(version))) {
				return false;
			}

			if (StringUtils.hasText(id)) {
				return true;
			}

			return StringUtils.hasText(group) && StringUtils.hasText(artifact);
		}

		public PsiElement getRequiredVersionLiteral() {
			Assert.state(versionLiteral != null, "Version literal must be set");
			return versionLiteral;
		}

		public GradleDependency toDependency(PropertyResolver propertyResolver) {

			Assert.state(group != null && artifact != null, "Group and name must be set");
			Assert.hasText(version, "Version must be set");

			if (StringUtils.hasText(versionProperty)) {
				ArtifactId artifactId = GradleDependency.getArtifactId(group, artifact, propertyResolver);
				return GradleDependency.of(artifactId, PropertyExpression.property(versionProperty));
			}

			return GradleDependency.of(group, artifact, version, propertyResolver);
		}

		/**
		 * Check whether the given {@link ArtifactId} matches this declaration.
		 */
		public boolean matches(ArtifactId id) {

			if (GradlePlugin.isPlugin(id) && id.artifactId().equals(this.id)) {
				return true;
			}

			return id.artifactId().equals(this.artifact) && id.groupId().equals(this.group);
		}

	}

}
