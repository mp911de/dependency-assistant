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

package biz.paluch.dap.support;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.HasPackageIdentity;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import com.intellij.psi.PsiElement;

/**
 * Dependency usage or declaration site in a build file.
 *
 * <p>A site exposes artifact coordinates, the source from which the version is
 * obtained, and the PSI element that owns the declaration.
 *
 * @author Mark Paluch
 * @see biz.paluch.dap.artifact.Dependency
 * @see VersionSource
 * @see ArtifactDeclaration
 */
public interface DependencySite extends HasPackageIdentity {

	/**
	 * Return the artifact coordinates associated with this dependency site.
	 */
	ArtifactId getArtifactId();

	/**
	 * Return the package system.
	 */
	PackageSystem getPackageSystem();

	@Override
	default PackageIdentity getPackageIdentity() {
		return PackageIdentity.of(getArtifactId(), getPackageSystem());
	}

	/**
	 * Return the source from which the dependency version is obtained.
	 */
	VersionSource getVersionSource();

	/**
	 * Return the source from which the dependency declaration is obtained.
	 */
	DeclarationSource getDeclarationSource();

	/**
	 * Return the PSI element that represents this dependency site.
	 */
	PsiElement getDeclarationElement();

	/**
	 * Create a new {@link VersionedDependencySite} with the given version and
	 * version element.
	 * @param version the artifact version.
	 * @param versionElement the PSI element representing the resolved version
	 * literal.
	 * @return the new {@link VersionedDependencySite}.
	 */
	default VersionedDependencySite withVersion(ArtifactVersion version, PsiElement versionElement) {
		return new ResolvedDependencySite(getPackageIdentity(), version, getVersionSource(),
				getDeclarationSource(), getDeclarationElement(), versionElement);
	}

	/**
	 * Create a new {@code DependencySite}.
	 * @param artifactId the artifact identifier.
	 * @param packageSystem the package system.
	 * @param versionSource the version source.
	 * @param declarationSource the declaration source.
	 * @param declarationElement element that represents this dependency site.
	 * @return the dependency site.
	 */
	static DependencySite of(ArtifactId artifactId, PackageSystem packageSystem,
			VersionSource versionSource, DeclarationSource declarationSource,
			PsiElement declarationElement) {
		return new SimpleDependencySite(PackageIdentity.of(artifactId, packageSystem),
				versionSource, declarationSource, declarationElement);
	}

	/**
	 * Create a new {@code DependencySite}.
	 * @param aware the object providing a {@link PackageIdentity}.
	 * @param versionSource the version source.
	 * @param declarationSource the declaration source.
	 * @param declarationElement element that represents this dependency site.
	 * @return the dependency site.
	 */
	static DependencySite of(HasPackageIdentity aware, VersionSource versionSource,
			DeclarationSource declarationSource, PsiElement declarationElement) {
		return of(aware.getPackageIdentity(), versionSource, declarationSource,
				declarationElement);
	}

	/**
	 * Create a new {@code DependencySite}.
	 * @param pkg the artifact identifier.
	 * @param versionSource the version source.
	 * @param declarationSource the declaration source.
	 * @param declarationElement element that represents this dependency site.
	 * @return the dependency site.
	 */
	static DependencySite of(PackageIdentity pkg, VersionSource versionSource,
			DeclarationSource declarationSource, PsiElement declarationElement) {
		return new SimpleDependencySite(pkg, versionSource, declarationSource,
				declarationElement);
	}

}
