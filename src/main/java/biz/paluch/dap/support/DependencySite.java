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

	ArtifactId getArtifactId();

	PackageSystem getPackageSystem();

	@Override
	default PackageIdentity getPackageIdentity() {
		return PackageIdentity.of(getArtifactId(), getPackageSystem());
	}

	VersionSource getVersionSource();

	DeclarationSource getDeclarationSource();

	PsiElement getDeclarationElement();

	/**
	 * Add a resolved version and its editable anchor, retaining this site's
	 * provenance.
	 */
	default VersionedDependencySite withVersion(ArtifactVersion version, PsiElement versionElement) {
		return new ResolvedDependencySite(getPackageIdentity(), version, getVersionSource(),
				getDeclarationSource(), getDeclarationElement(), versionElement);
	}

	static DependencySite of(ArtifactId artifactId, PackageSystem packageSystem,
			VersionSource versionSource, DeclarationSource declarationSource,
			PsiElement declarationElement) {
		return new SimpleDependencySite(PackageIdentity.of(artifactId, packageSystem),
				versionSource, declarationSource, declarationElement);
	}

	static DependencySite of(HasPackageIdentity aware, VersionSource versionSource,
			DeclarationSource declarationSource, PsiElement declarationElement) {
		return of(aware.getPackageIdentity(), versionSource, declarationSource,
				declarationElement);
	}

	static DependencySite of(PackageIdentity pkg, VersionSource versionSource,
			DeclarationSource declarationSource, PsiElement declarationElement) {
		return new SimpleDependencySite(pkg, versionSource, declarationSource,
				declarationElement);
	}

}
