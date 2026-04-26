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

package biz.paluch.dap.support;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionSource;
import com.intellij.psi.PsiElement;

/**
 * Extension of {@link DependencySite} for dependency sites with a resolved
 * version.
 * <p>In addition to the artifact coordinates, version source, and declaration
 * element exposed through {@link DependencySite}, implementations provide the
 * concrete {@link #getVersion() dependency version} together with the
 * {@link PsiElement PSI} element that points to the version value within the
 * parsed file.
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
	 * Create a new {@code DependencySite} instance given {@link ArtifactId},
	 * {@link VersionSource} and its {@link PsiElement declaration}.
	 * @param artifactId the artifact identifier.
	 * @param version the artifact version.
	 * @param versionSource the version source, an inline-version or a property.
	 * @param declarationElement element that represents this dependency site.
	 * @param versionElement element that represents the version literal.
	 * @return the dependency site.
	 */
	static VersionedDependencySite of(ArtifactId artifactId, ArtifactVersion version, VersionSource versionSource,
			PsiElement declarationElement, PsiElement versionElement) {
		return new ResolvedDependencySite(artifactId, version, versionSource, declarationElement, versionElement);
	}

}
