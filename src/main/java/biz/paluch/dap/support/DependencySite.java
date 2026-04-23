/*
 * Copyright 2026the original author or authors.
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
 * Interface representing dependency usage (or declaration) site in a build file
 *
 * <p>A dependency site may represent a full dependency declaration or a usage
 * that derives its version from another location. In both cases,
 * implementations expose the {@link #getArtifactId() artifact coordinates}, the
 * associated {@link #getVersionSource() version source}, and the backing
 * {@link PsiElement PSI} element that identifies the site within the parsed
 * file.
 * <p>The {@link #getDeclarationElement() declaration element} is typically a
 * Maven {@code <dependency>/<plugin>} tag, a Maven {@code property tag}, Gradle
 * {@code implementation} call, Gradle property/local variable holding the
 * version literal, or TOML library element.
 *
 * @author Mark Paluch
 * @see biz.paluch.dap.artifact.Dependency
 * @see VersionSource
 * @see ArtifactDeclaration
 */
public interface DependencySite {

	/**
	 * Return the artifact coordinates associated with this dependency site.
	 *
	 * @return the artifact coordinates.
	 */
	ArtifactId getArtifactId();

	/**
	 * Return the source from which the dependency version is obtained.
	 *
	 * @return the version source.
	 */
	VersionSource getVersionSource();

	/**
	 * Return the PSI element that represents this dependency site.
	 * <p>The returned element typically identifies the declaration or reference
	 * from which dependency metadata was collected.
	 *
	 * @return the PSI element representing this dependency site.
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
		return new ResolvedDependencySite(getArtifactId(), version, getVersionSource(), getDeclarationElement(),
				versionElement);
	}

	/**
	 * Create a new {@code DependencySite} instance given {@link ArtifactId},
	 * {@link VersionSource} and its {@link PsiElement declaration}.
	 * @param artifactId the artifact identifier.
	 * @param versionSource the version source, an inline-version or a property.
	 * @param declarationElement element that represents this dependency site.
	 * @return the dependency site.
	 */
	static DependencySite of(ArtifactId artifactId, VersionSource versionSource,
			PsiElement declarationElement) {
		return new SimpleDependencySite(artifactId, versionSource, declarationElement);
	}

}
