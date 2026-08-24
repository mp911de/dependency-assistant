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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.VersionSource;
import com.intellij.psi.PsiElement;

/**
 * Extension of {@link DependencySite} for dependency sites with a resolved
 * version.
 * <p>In addition to the package identity, provenance, and declaration anchor
 * exposed through {@link DependencySite}, implementations provide the concrete
 * {@link #getVersion() dependency version} and its separate PSI anchor. The
 * declaration and version anchors may identify different elements.
 *
 * @author Mark Paluch
 * @see ArtifactVersion
 * @see DependencySite
 */
public interface VersionedDependencySite extends DependencySite {

	/**
	 * Return the resolved dependency version.
	 *
	 * @return the dependency version.
	 */
	ArtifactVersion getVersion();

	/**
	 * Return the PSI element that points to the {@link #getVersion() version
	 * value}.
	 * <p>The returned element typically represents the version literal or property
	 * usage from which the dependency version was obtained.
	 *
	 * @return the PSI element representing the dependency version.
	 */
	PsiElement getVersionElement();

	/**
	 * Create a versioned dependency site from a complete package identity,
	 * provenance, and PSI anchors.
	 *
	 * @param pkg the package identity.
	 * @param version the artifact version.
	 * @param versionSource the version source, an inline-version or a property.
	 * @param declarationSource the declaration source.
	 * @param declarationElement element that represents this dependency site.
	 * @param versionElement element that represents the version literal.
	 * @return the versioned dependency site.
	 */
	static VersionedDependencySite of(PackageIdentity pkg, ArtifactVersion version,
			VersionSource versionSource, DeclarationSource declarationSource,
			PsiElement declarationElement, PsiElement versionElement) {
		return new ResolvedDependencySite(pkg, version, versionSource, declarationSource,
				declarationElement, versionElement);
	}

}
