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

package biz.paluch.dap.artifact;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Declaration and version sources observed for one package.
 * <p>Sources are merged without retaining their build-file origin. This
 * declaration has no effective version. {@link Dependency} adds one when a
 * usage can be resolved.
 *
 * @author Mark Paluch
 * @see DependencyCollector
 */
public class DeclaredDependency implements VersionedPackage {

	private final PackageIdentity pkg;

	private final Set<VersionSource> versionSources = new LinkedHashSet<>();

	private final Set<DeclarationSource> declarationSources = new LinkedHashSet<>();

	public DeclaredDependency(PackageIdentity pkg) {
		this.pkg = pkg;
	}

	@Override
	public ArtifactId getArtifactId() {
		return pkg.getArtifactId();
	}

	@Override
	public PackageIdentity getPackageIdentity() {
		return pkg;
	}

	/**
	 * Return the version sources associated with this dependency.
	 * @return an unmodifiable live view in registration order.
	 */
	public Set<VersionSource> getVersionSources() {
		return Collections.unmodifiableSet(versionSources);
	}

	/**
	 * Return the declaration sources associated with this dependency.
	 * @return an unmodifiable live view in registration order.
	 */
	public Set<DeclarationSource> getDeclarationSources() {
		return Collections.unmodifiableSet(declarationSources);
	}

	/**
	 * Add a version source and return this dependency.
	 */
	public DeclaredDependency addVersionSource(VersionSource versionSource) {
		this.versionSources.add(versionSource);
		return this;
	}

	public void addAllVersionSources(Collection<? extends VersionSource> versionSources) {
		this.versionSources.addAll(versionSources);
	}

	/**
	 * Add a declaration source and return this dependency.
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
	public boolean isVersioned() {
		return false;
	}

	/**
	 * Reject access to an effective version.
	 * @throws IllegalStateException because this declaration is unversioned.
	 */
	@Override
	public ArtifactVersion getVersion() {
		throw new IllegalStateException("Not a versioned dependency");
	}

	@Override
	public String toString() {
		return getArtifactId().toString();
	}

}
