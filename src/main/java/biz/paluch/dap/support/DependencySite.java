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
 * One place in a build file where a dependency is declared or used.
 *
 * <p>A site identifies the package, records separate declaration and version
 * provenance, and exposes the PSI element that owns the declaration. It does
 * not necessarily carry a resolved version. Use {@link VersionedDependencySite}
 * when both the effective version and its PSI anchor are known.
 *
 * @author Mark Paluch
 * @see biz.paluch.dap.artifact.Dependency
 * @see VersionSource
 * @see ArtifactDeclaration
 */
public interface DependencySite extends HasPackageIdentity {

	/**
	 * Return the artifact coordinates associated with this dependency site.
	 *
	 * @return the artifact coordinates.
	 */
	ArtifactId getArtifactId();

	/**
	 * Return the package system.
	 *
	 * @return the package system that interprets the artifact coordinates.
	 */
	PackageSystem getPackageSystem();

	@Override
	default PackageIdentity getPackageIdentity() {
		return PackageIdentity.of(getArtifactId(), getPackageSystem());
	}

	/**
	 * Return the source from which the dependency version is obtained.
	 *
	 * @return the version source.
	 */
	VersionSource getVersionSource();

	/**
	 * Return the source from which the dependency declaration is obtained.
	 *
	 * @return the declaration source.
	 */
	DeclarationSource getDeclarationSource();

	/**
	 * Return the PSI element that represents this dependency site.
	 *
	 * @return the declaration's PSI anchor.
	 */
	PsiElement getDeclarationElement();

	/**
	 * Add a resolved version and its PSI anchor to this dependency site.
	 *
	 * @param version the artifact version.
	 * @param versionElement the PSI element representing the resolved version
	 * literal.
	 * @return a versioned site retaining this site's identity, provenance, and
	 * declaration anchor.
	 */
	default VersionedDependencySite withVersion(ArtifactVersion version, PsiElement versionElement) {
		return new ResolvedDependencySite(getPackageIdentity(), version, getVersionSource(),
				getDeclarationSource(), getDeclarationElement(), versionElement);
	}

	/**
	 * Create a dependency site from separate artifact and package-system values.
	 *
	 * @param artifactId the artifact identifier.
	 * @param packageSystem the package system.
	 * @param versionSource the version source.
	 * @param declarationSource the declaration source.
	 * @param declarationElement element that represents this dependency site.
	 * @return a dependency site for the given package identity and provenance.
	 */
	static DependencySite of(ArtifactId artifactId, PackageSystem packageSystem,
			VersionSource versionSource, DeclarationSource declarationSource,
			PsiElement declarationElement) {
		return new SimpleDependencySite(PackageIdentity.of(artifactId, packageSystem),
				versionSource, declarationSource, declarationElement);
	}

	/**
	 * Create a dependency site using another object's package identity.
	 *
	 * @param aware the object providing a {@link PackageIdentity}.
	 * @param versionSource the version source.
	 * @param declarationSource the declaration source.
	 * @param declarationElement element that represents this dependency site.
	 * @return a dependency site for the supplied package identity and provenance.
	 */
	static DependencySite of(HasPackageIdentity aware, VersionSource versionSource,
			DeclarationSource declarationSource, PsiElement declarationElement) {
		return of(aware.getPackageIdentity(), versionSource, declarationSource,
				declarationElement);
	}

	/**
	 * Create a dependency site from a complete package identity.
	 *
	 * @param pkg the package identity.
	 * @param versionSource the version source.
	 * @param declarationSource the declaration source.
	 * @param declarationElement element that represents this dependency site.
	 * @return a dependency site for the given package identity and provenance.
	 */
	static DependencySite of(PackageIdentity pkg, VersionSource versionSource,
			DeclarationSource declarationSource, PsiElement declarationElement) {
		return new SimpleDependencySite(pkg, versionSource, declarationSource,
				declarationElement);
	}

}
