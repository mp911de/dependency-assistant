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
 * <p>Typical usage: <pre class="code">
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

	@Override
	public DependencyCollectorAssert assertThat() {
		return this;
	}

	/**
	 * Verify that the collector contains exactly the given number of usage entries.
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
	 * Verify that the collector contains no usage entries.
	 */
	public DependencyCollectorAssert isEmpty() {
		return hasUsageCount(0);
	}

	/**
	 * Verify that no usage is registered for the given coordinates and return
	 * {@code this} for further chaining.
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
	 * Verify that no usage is registered for the given artifact id.
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
	 * Verify that a usage is registered for the given coordinates and navigate to a
	 * {@link DependencyUsageAssert} for further assertions.
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
	 * Verify that a usage is registered for the given artifact id and navigate to a
	 * {@link DependencyUsageAssert} for further assertions.
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
	 * <p>Instances are obtained via
	 * {@link DependencyCollectorAssert#hasDependencyUsage(String, String)}.
	 */
	public static class DependencyUsageAssert
			extends AbstractAssert<DependencyUsageAssert, Dependency> {

		DependencyUsageAssert(Dependency dependency) {
			super(dependency, DependencyUsageAssert.class);
		}

		/**
		 * Verify that the current version string equals the given value.
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
		 * Verify that at least one {@link DeclarationSource} in this dependency is an
		 * instance of the given type.
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
		 * Verify that at least one {@link DeclarationSource} in this dependency equals
		 * the given value.
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
		 * Verify that the version is sourced from a Gradle property with the given name
		 * (sugar for {@link VersionSource#property(String)}).
		 */
		public DependencyUsageAssert hasPropertyVersion(String propertyName) {
			return hasVersionSource(VersionSource.property(propertyName));
		}

		/**
		 * Verify that the version is not sourced from a Gradle property reference.
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
		 * Verify that at least one {@link VersionSource} in this dependency equals the
		 * given value.
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
		 * Verify that at least one {@link VersionSource} in this dependency is an
		 * instance of the given type.
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
		 * Verify that the artifact id of this dependency equals the given value.
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
