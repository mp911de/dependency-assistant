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

package biz.paluch.dap.assertions;

import java.util.Collection;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AssertProvider;
import org.jspecify.annotations.Nullable;

/**
 * AssertJ assertions for {@link DependencyCollector}.
 *
 * <p>The collector assertion models parser output as registered dependency
 * usages. Methods that find a usage navigate to {@link DependencyUsageAssert}
 * so tests can continue with assertions on version, declaration source, and
 * version source.
 *
 * <p>Example: <pre class="code">
 * assertThat(collector).hasUsageCount(2);
 *
 * assertThat(collector)
 *     .hasDependencyUsage("org.junit", "junit-bom")
 *     .hasVersion("6.0.3")
 *     .hasDeclaration(DeclarationSource.dependency())
 *     .hasVersionSource(VersionSource.literal());
 *
 * assertThat(collector)
 *     .hasDependencyUsage("org.springframework.boot", "org.springframework.boot")
 *     .hasVersion("4.0.3")
 *     .hasDeclaration(DeclarationSource.Plugin.class);
 * </pre>
 *
 * @author Mark Paluch
 */
public class DependencyCollectorAssert
		extends AbstractAssert<DependencyCollectorAssert, DependencyCollector>
		implements AssertProvider<DependencyCollectorAssert> {

	DependencyCollectorAssert(DependencyCollector collector) {
		super(collector, DependencyCollectorAssert.class);
	}

	/**
	 * Returns this assertion object for AssertJ {@link AssertProvider} integration.
	 */
	@Override
	public DependencyCollectorAssert assertThat() {
		return this;
	}

	/**
	 * Verifies that the actual collector contains exactly the given number of
	 * dependency usages.
	 * @param expected the expected number of dependency usages.
	 * @return this assertion object.
	 */
	public DependencyCollectorAssert hasUsageCount(int expected) {
		isNotNull();
		Collection<Dependency> usages = this.actual.getUsages();
		if (usages.size() != expected) {
			failWithMessage("Expected %d dependency usage(s) but found %d: %s",
					expected, usages.size(), usages);
		}
		return this;
	}

	/**
	 * Verifies that the actual collector contains no dependency usages.
	 * @return this assertion object.
	 */
	public DependencyCollectorAssert isEmpty() {
		return hasUsageCount(0);
	}

	/**
	 * Verifies that no dependency usage is registered for the given coordinates.
	 * @param groupId the expected group id to be absent.
	 * @param artifactId the expected artifact id to be absent.
	 * @return this assertion object.
	 */
	public DependencyCollectorAssert hasNoDependencyUsage(String groupId, String artifactId) {
		isNotNull();
		Dependency usage = this.actual.getUsage(groupId, artifactId);
		if (usage != null) {
			failWithMessage(
					"Expected no dependency usage for '%s:%s' but found: %s",
					groupId, artifactId, usage);
		}
		return this;
	}

	/**
	 * Verifies that no dependency usage is registered for the given artifact id.
	 * @param name the artifact id expected to be absent.
	 * @return this assertion object.
	 */
	public DependencyCollectorAssert hasNoDependencyUsage(String name) {
		isNotNull();
		Dependency usage = getDependency(name);
		if (usage != null) {
			failWithMessage(
					"Expected no dependency usage for '%s' but found: %s", name, usage);
		}
		return this;
	}

	/**
	 * Verifies that a dependency usage is registered for the given coordinates and
	 * returns an assertion object for that usage.
	 * @param groupId the expected group id.
	 * @param artifactId the expected artifact id.
	 * @return an assertion object for the matching dependency usage.
	 */
	public DependencyUsageAssert hasDependencyUsage(String groupId, String artifactId) {
		isNotNull();
		Dependency usage = this.actual.getUsage(groupId, artifactId);
		if (usage == null) {
			failWithMessage(
					"Expected dependency usage for '%s:%s' but none was registered. "
							+ "Registered usages: %s",
					groupId, artifactId, this.actual.getUsages());
		}
		return new DependencyUsageAssert(usage);
	}

	/**
	 * Verifies that a dependency usage is registered for the given artifact id and
	 * returns an assertion object for that usage.
	 * @param name the expected artifact id.
	 * @return an assertion object for the matching dependency usage.
	 */
	public DependencyUsageAssert hasDependencyUsage(String name) {
		isNotNull();

		Dependency usage = getDependency(name);
		if (usage == null) {
			failWithMessage(
					"Expected dependency usage for '%s' but none was registered. "
							+ "Registered usages: %s",
					name, this.actual.getUsages());
		}
		return new DependencyUsageAssert(usage);
	}

	private @Nullable Dependency getDependency(String name) {
		return this.actual.getUsages().stream().filter(it -> it.getArtifactId().artifactId().equals(name))
				.findFirst().orElse(null);
	}

	/**
	 * AssertJ assertions for a single {@link Dependency} usage.
	 *
	 * <p>Instances are obtained from
	 * {@link DependencyCollectorAssert#hasDependencyUsage(String, String)} or
	 * {@link DependencyCollectorAssert#hasDependencyUsage(String)} after the
	 * collector assertion has established that the requested usage exists.
	 */
	public static class DependencyUsageAssert
			extends AbstractAssert<DependencyUsageAssert, Dependency> {

		DependencyUsageAssert(Dependency dependency) {
			super(dependency, DependencyUsageAssert.class);
		}

		/**
		 * Verifies that the actual dependency's current version string equals the given
		 * value.
		 * @param expectedVersion the expected version string.
		 * @return this assertion object.
		 */
		public DependencyUsageAssert hasVersion(String expectedVersion) {
			isNotNull();
			ArtifactVersion current = this.actual.getCurrentVersion();
			String actual = (current != null) ? current.toString() : null;
			if (!expectedVersion.equals(actual)) {
				failWithMessage(
						"Expected dependency '%s' to have version '%s' but was '%s'",
						this.actual.getArtifactId(), expectedVersion, actual);
			}
			return this;
		}

		/**
		 * Verifies that at least one {@link DeclarationSource} in the actual dependency
		 * is an instance of the given type.
		 * @param type the declaration source type expected to be present.
		 * @return this assertion object.
		 */
		public DependencyUsageAssert hasDeclaration(Class<? extends DeclarationSource> type) {
			isNotNull();
			boolean found = this.actual.getDeclarationSources().stream()
					.anyMatch(type::isInstance);
			if (!found) {
				failWithMessage(
						"Expected dependency '%s' to have a declaration of type %s "
								+ "but declaration sources were: %s",
						this.actual.getArtifactId(), type.getSimpleName(),
						this.actual.getDeclarationSources());
			}
			return this;
		}

		/**
		 * Verifies that at least one {@link DeclarationSource} in the actual dependency
		 * equals the given value.
		 * @param expected the declaration source expected to be present.
		 * @return this assertion object.
		 */
		public DependencyUsageAssert hasDeclaration(DeclarationSource expected) {
			isNotNull();
			boolean found = this.actual.getDeclarationSources().stream()
					.anyMatch(expected::equals);
			if (!found) {
				failWithMessage(
						"Expected dependency '%s' to have declaration source '%s' "
								+ "but declaration sources were: %s",
						this.actual.getArtifactId(), expected,
						this.actual.getDeclarationSources());
			}
			return this;
		}

		/**
		 * Verifies that the actual dependency version is sourced from a Gradle property
		 * with the given name.
		 * <p>This is convenience syntax for {@link #hasVersionSource(VersionSource)}
		 * with {@link VersionSource#property(String)}.
		 * @param propertyName the expected property name.
		 * @return this assertion object.
		 */
		public DependencyUsageAssert hasPropertyVersion(String propertyName) {
			return hasVersionSource(VersionSource.property(propertyName));
		}

		/**
		 * Verifies that the actual dependency version is not sourced from a Gradle
		 * property reference.
		 * @return this assertion object.
		 */
		public DependencyUsageAssert hasNoPropertyVersion() {
			isNotNull();
			if (this.actual.findPropertyVersion() != null) {
				failWithMessage(
						"Expected dependency '%s' to have no property-based version but found: %s",
						this.actual.getArtifactId(), this.actual.findPropertyVersion());
			}
			return this;
		}

		/**
		 * Verifies that at least one {@link VersionSource} in the actual dependency
		 * equals the given value.
		 * @param expected the version source expected to be present.
		 * @return this assertion object.
		 */
		public DependencyUsageAssert hasVersionSource(VersionSource expected) {
			isNotNull();
			boolean found = this.actual.getVersionSources().stream()
					.anyMatch(expected::equals);
			if (!found) {
				failWithMessage(
						"Expected dependency '%s' to have version source '%s' "
								+ "but version sources were: %s",
						this.actual.getArtifactId(), expected,
						this.actual.getVersionSources());
			}
			return this;
		}

		/**
		 * Verifies that at least one {@link VersionSource} in the actual dependency is
		 * an instance of the given type.
		 * @param type the version source type expected to be present.
		 * @return this assertion object.
		 */
		public DependencyUsageAssert hasVersionSource(Class<? extends VersionSource> type) {
			isNotNull();
			boolean found = this.actual.getVersionSources().stream()
					.anyMatch(type::isInstance);
			if (!found) {
				failWithMessage(
						"Expected dependency '%s' to have a version source of type %s "
								+ "but version sources were: %s",
						this.actual.getArtifactId(), type.getSimpleName(),
						this.actual.getVersionSources());
			}
			return this;
		}

		/**
		 * Verifies that the actual dependency artifact id equals the given value.
		 * @param expected the expected artifact id.
		 * @return this assertion object.
		 */
		public DependencyUsageAssert hasArtifactId(ArtifactId expected) {
			isNotNull();
			if (!expected.equals(this.actual.getArtifactId())) {
				failWithMessage(
						"Expected dependency artifact id to be '%s' but was '%s'",
						expected, this.actual.getArtifactId());
			}
			return this;
		}

	}

}
