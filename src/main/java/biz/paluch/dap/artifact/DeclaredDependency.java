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
package biz.paluch.dap.artifact;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * A dependency entry found in the scanned build files, together with all
 * structural locations where it appears and how its version is defined.
 * <p>The same artifact can be declared in multiple places across a project -
 * for example in both {@code <dependencies>} and {@code <dependencyManagement>}
 * in Maven, or in both a TOML catalog and a Gradle build file. Each location is
 * captured as a {@link DeclarationSource}, and each version origin is captured
 * as a {@link VersionSource}. Both sets preserve insertion order so that
 * iteration reflects the scan sequence.
 * <p>{@link DeclaredDependency} represents a version-constraint entry that may
 * or may not carry a directly resolved version. {@link Dependency} extends this
 * class for entries that do carry a definitive version.
 *
 * @author Mark Paluch
 * @see Dependency
 * @see DeclarationSource
 * @see VersionSource
 * @see DependencyCollector
 */
public class DeclaredDependency implements HasArtifactId {

	private final ArtifactId artifactId;

	private final Set<VersionSource> versionSources = new LinkedHashSet<>();

	private final Set<DeclarationSource> declarationSources = new LinkedHashSet<>();

	public DeclaredDependency(ArtifactId artifactId) {
		this.artifactId = artifactId;
	}

	@Override
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	/**
	 * Return the version sources associated with this dependency, in the order they
	 * were registered during the scan.
	 *
	 * @return an unmodifiable set of version sources; guaranteed to be not
	 * {@literal null} but may be empty.
	 */
	public Set<VersionSource> getVersionSources() {
		return Collections.unmodifiableSet(versionSources);
	}

	/**
	 * Return the declaration sources associated with this dependency, in the order
	 * they were registered during the scan.
	 *
	 * @return an unmodifiable set of declaration sources; guaranteed to be not
	 * {@literal null} but may be empty.
	 */
	public Set<DeclarationSource> getDeclarationSources() {
		return Collections.unmodifiableSet(declarationSources);
	}

	/**
	 * Add a version source to this dependency.
	 * <p>Called by {@link DependencyCollector} as the scan progresses across files.
	 * Duplicates are silently ignored by the underlying {@link LinkedHashSet}.
	 *
	 * @param versionSource the version source to add; must not be {@literal null}.
	 * @return this instance for method chaining.
	 */
	public DeclaredDependency addVersionSource(VersionSource versionSource) {
		this.versionSources.add(versionSource);
		return this;
	}

	/**
	 * Add a declaration source to this dependency.
	 * <p>Called by {@link DependencyCollector} as the scan progresses across files.
	 * Duplicates are silently ignored by the underlying {@link LinkedHashSet}.
	 *
	 * @param declarationSource the declaration source to add; must not be
	 * {@literal null}.
	 * @return this instance for method chaining.
	 */
	public DeclaredDependency addDeclarationSource(DeclarationSource declarationSource) {
		this.declarationSources.add(declarationSource);
		return this;
	}

	/**
	 * Return whether any of the registered version sources is a property-based
	 * version (i.e. implements {@link VersionSource.VersionProperty}).
	 *
	 * @return {@literal true} if a property version source is present;
	 * {@literal false} otherwise.
	 */
	public boolean hasPropertyVersion() {
		return findPropertyVersion() != null;
	}

	/**
	 * Return the first property-based version source, or {@literal null} if none is
	 * registered.
	 * <p>Covers both Maven property references and TOML version catalog property
	 * entries, both of which implement {@link VersionSource.VersionProperty}.
	 *
	 * @return the first {@link VersionSource.VersionProperty}, or {@literal null}
	 * if no property version is present.
	 */
	public VersionSource.@Nullable VersionProperty findPropertyVersion() {

		for (VersionSource versionSource : versionSources) {
			if (versionSource instanceof VersionSource.VersionProperty) {
				return (VersionSource.VersionProperty) versionSource;
			}
		}

		return null;
	}

	@Override
	public String toString() {
		return getArtifactId().toString();
	}

}
