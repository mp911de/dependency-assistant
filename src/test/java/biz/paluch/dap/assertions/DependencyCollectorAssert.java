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

package biz.paluch.dap.assertions;

import java.util.Collection;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AssertProvider;
import org.jspecify.annotations.Nullable;

/**
 * Assertions for collected usages and managed declarations.
 * <p>Overloads taking only a name match the artifact ID without the group ID.
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

	public DependencyCollectorAssert hasUsageCount(int expected) {
		isNotNull();
		Collection<Dependency> usages = this.actual.getUsages();
		if (usages.size() != expected) {
			failWithMessage("Expected %d dependency usage(s) but found %d: %s",
					expected, usages.size(), usages);
		}
		return this;
	}

	public DependencyCollectorAssert hasDeclarationCount(int expected) {
		isNotNull();
		Collection<DeclaredDependency> declarations = this.actual.getDeclarations();
		if (declarations.size() != expected) {
			failWithMessage("Expected %d dependency declaration(s) but found %d: %s",
					expected, declarations.size(), declarations);
		}
		return this;
	}

	/**
	 * Require no dependency usages. Managed declarations may still be present.
	 */
	public DependencyCollectorAssert isEmpty() {
		return hasUsageCount(0);
	}

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

	public DependencyCollectorAssert hasNoDependencyUsage(String name) {
		isNotNull();
		Dependency usage = getDependency(name);
		if (usage != null) {
			failWithMessage(
					"Expected no dependency usage for '%s' but found: %s", name, usage);
		}
		return this;
	}

	public DependencyCollectorAssert hasNoDependencyDeclaration(String groupId, String artifactId) {
		isNotNull();
		DeclaredDependency declaration = this.actual.getDeclaration(ArtifactId.of(groupId, artifactId));
		if (declaration != null) {
			failWithMessage(
					"Expected no dependency declaration for '%s:%s' but found: %s",
					groupId, artifactId, declaration);
		}
		return this;
	}

	public DependencyCollectorAssert hasNoDependencyDeclaration(String name) {
		isNotNull();
		DeclaredDependency declaration = getDeclaration(name);
		if (declaration != null) {
			failWithMessage(
					"Expected no dependency declaration for '%s' but found: %s", name, declaration);
		}
		return this;
	}

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

	public DependencyDeclarationAssert hasDependencyDeclaration(String groupId, String artifactId) {
		isNotNull();
		DeclaredDependency declaration = this.actual.getDeclaration(ArtifactId.of(groupId, artifactId));
		if (declaration == null) {
			failWithMessage(
					"Expected dependency declaration for '%s:%s' but none was registered. "
							+ "Registered declarations: %s",
					groupId, artifactId, this.actual.getDeclarations());
		}
		return new DependencyDeclarationAssert(declaration);
	}

	public DependencyDeclarationAssert hasDependencyDeclaration(String name) {
		isNotNull();

		DeclaredDependency declaration = getDeclaration(name);
		if (declaration == null) {
			failWithMessage(
					"Expected dependency declaration for '%s' but none was registered. "
							+ "Registered declarations: %s",
					name, this.actual.getDeclarations());
		}
		return new DependencyDeclarationAssert(declaration);
	}

	private @Nullable Dependency getDependency(String name) {
		return this.actual.getUsages().stream().filter(it -> it.getArtifactId().artifactId().equals(name))
				.findFirst().orElse(null);
	}

	private @Nullable DeclaredDependency getDeclaration(String name) {
		return this.actual.getDeclarations().stream().filter(it -> it.getArtifactId().artifactId().equals(name))
				.findFirst().orElse(null);
	}

	/**
	 * Source assertions shared by managed declarations and dependency usages.
	 */
	public abstract static class AbstractDeclaredDependencyAssert<SELF extends AbstractDeclaredDependencyAssert<SELF, ACTUAL>, ACTUAL extends DeclaredDependency>
			extends AbstractAssert<SELF, ACTUAL> {

		AbstractDeclaredDependencyAssert(ACTUAL actual, Class<?> selfType) {
			super(actual, selfType);
		}

		/**
		 * Require a declaration matching a concrete type or marker interface.
		 */
		public SELF hasDeclaration(Class<?> type) {
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
			return myself;
		}

		/**
		 * Require no declaration matching the given type or marker interface.
		 */
		public SELF hasNoDeclaration(Class<?> type) {
			isNotNull();
			boolean found = this.actual.getDeclarationSources().stream()
					.anyMatch(type::isInstance);
			if (found) {
				failWithMessage(
						"Expected dependency '%s' to have no declaration of type %s "
								+ "but declaration sources were: %s",
						this.actual.getArtifactId(), type.getSimpleName(),
						this.actual.getDeclarationSources());
			}
			return myself;
		}

		public SELF hasDeclaration(DeclarationSource expected) {
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
			return myself;
		}

		public SELF hasPropertyVersion(String propertyName) {
			return hasVersionSource(VersionSource.property(propertyName));
		}

		public SELF hasNoPropertyVersion() {
			isNotNull();
			if (this.actual.findPropertyVersion() != null) {
				failWithMessage(
						"Expected dependency '%s' to have no property-based version but found: %s",
						this.actual.getArtifactId(), this.actual.findPropertyVersion());
			}
			return myself;
		}

		public SELF hasVersionSource(VersionSource expected) {
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
			return myself;
		}

		public SELF hasNoVersionSource(VersionSource unexpected) {
			isNotNull();
			boolean found = this.actual.getVersionSources().stream()
					.anyMatch(unexpected::equals);
			if (found) {
				failWithMessage(
						"Expected dependency '%s' to have no version source '%s' "
								+ "but version sources were: %s",
						this.actual.getArtifactId(), unexpected,
						this.actual.getVersionSources());
			}
			return myself;
		}

		public SELF hasVersionSource(Class<? extends VersionSource> type) {
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
			return myself;
		}

		public SELF hasArtifactId(ArtifactId expected) {
			isNotNull();
			if (!expected.equals(this.actual.getArtifactId())) {
				failWithMessage(
						"Expected dependency artifact id to be '%s' but was '%s'",
						expected, this.actual.getArtifactId());
			}
			return myself;
		}

	}

	/**
	 * Assertions for a dependency usage, including its effective current version.
	 */
	public static class DependencyUsageAssert
			extends AbstractDeclaredDependencyAssert<DependencyUsageAssert, Dependency> {

		DependencyUsageAssert(Dependency dependency) {
			super(dependency, DependencyUsageAssert.class);
		}

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

	}

	/**
	 * Assertions for a managed declaration, which has no current version.
	 */
	public static class DependencyDeclarationAssert
			extends AbstractDeclaredDependencyAssert<DependencyDeclarationAssert, DeclaredDependency> {

		DependencyDeclarationAssert(DeclaredDependency declaration) {
			super(declaration, DependencyDeclarationAssert.class);
		}

	}

}
