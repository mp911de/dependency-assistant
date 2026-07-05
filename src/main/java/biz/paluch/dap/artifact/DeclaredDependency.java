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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Dependency declaration found while scanning build files.
 *
 * <p>The same artifact can appear in several structural locations. Each
 * declaration location is captured as a {@link DeclarationSource}, and each
 * version origin is captured as a {@link VersionSource}.
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

	/**
	 * Create a new {@code DeclaredDependency}.
	 * @param artifactId the declared artifact coordinates.
	 */
	public DeclaredDependency(ArtifactId artifactId) {
		this.artifactId = artifactId;
	}

	@Override
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	/**
	 * Return the version sources associated with this dependency.
	 */
	public Set<VersionSource> getVersionSources() {
		return Collections.unmodifiableSet(versionSources);
	}

	/**
	 * Return the declaration sources associated with this dependency.
	 */
	public Set<DeclarationSource> getDeclarationSources() {
		return Collections.unmodifiableSet(declarationSources);
	}

	/**
	 * Return the first declaration source that is an instance of the given type.
	 * Accepts concrete source types and marker interfaces such as
	 * {@link DeclarationSource.Bom}.
	 * @param declarationSource the declaration source type or marker to look up.
	 * @return the first matching declaration source; may be {@literal null}.
	 */
	public <T> @Nullable T getDeclarationSource(Class<T> declarationSource) {
		for (DeclarationSource source : declarationSources) {
			if (declarationSource.isInstance(source)) {
				return declarationSource.cast(source);
			}
		}
		return null;
	}

	/**
	 * Add a version source to this dependency.
	 * @param versionSource the version source to add.
	 * @return this instance for method chaining.
	 */
	public DeclaredDependency addVersionSource(VersionSource versionSource) {
		this.versionSources.add(versionSource);
		return this;
	}

	public void addAllVersionSources(Collection<? extends VersionSource> versionSources) {
		this.versionSources.addAll(versionSources);
	}

	/**
	 * Add a declaration source to this dependency.
	 * @param declarationSource the declaration source to add.
	 * @return this instance for method chaining.
	 */
	public DeclaredDependency addDeclarationSource(DeclarationSource declarationSource) {
		this.declarationSources.add(declarationSource);
		return this;
	}

	public void addAllDeclarationSources(Collection<? extends DeclarationSource> declarationSources) {
		this.declarationSources.addAll(declarationSources);
	}

	/**
	 * Return whether any registered version source is property-based.
	 */
	public boolean hasPropertyVersion() {
		return findPropertyVersion() != null;
	}

	public boolean hasDefinedVersion() {
		for (VersionSource versionSource : versionSources) {
			if (versionSource.isDefined())
				return true;
		}
		return false;
	}

	/**
	 * Return the first property-based version source, or {@literal null} if none is
	 * registered.
	 */
	public VersionSource.@Nullable VersionProperty findPropertyVersion() {
		for (VersionSource versionSource : versionSources) {
			if (versionSource instanceof VersionSource.VersionProperty vp) {
				return vp;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return getArtifactId().toString();
	}

}
