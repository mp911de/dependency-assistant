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

package biz.paluch.dap.maven.wrapper;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.VersionSource;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Parsed Maven Wrapper URL declaration.
 *
 * <p>An entry retains the supported property kind, the PSI property and value,
 * the repository derived from the URL, and the separate path and file-name
 * version texts. The two version texts may differ while a user is editing the
 * declaration.
 *
 * @author Mark Paluch
 */
record WrapperEntry(WrapperProperty property, Property propertyLiteral, PsiElement versionLiteral,
		RemoteRepository repository, String pathVersion, String fileVersion) {

	public boolean hasArtifactId(ArtifactId coordinate) {
		return property.artifactId().equals(coordinate);
	}

	/**
	 * Return whether the path and file version segments carry the same text.
	 *
	 * <p>A well-formed wrapper URL holds the same version twice; mismatched
	 * versions indicate a malformed URL, or a mid-typed URL where the completion
	 * placeholder has been inserted into only one segment. Dependency collection
	 * rejects such entries; completion and resolution accept them so the user can
	 * still get version suggestions while typing.
	 *
	 * @return {@literal true} if both version segments contain the same text.
	 */
	public boolean hasConsistentVersions() {
		return pathVersion.equals(fileVersion);
	}

	/**
	 * Return the parsed artifact version, or {@literal null} when
	 * {@link #pathVersion} cannot be parsed as an {@link ArtifactVersion}.
	 *
	 * @return the parsed path version, or {@literal null} if it is not recognized.
	 */
	@Nullable
	public ArtifactVersion version() {
		return ArtifactVersion.from(pathVersion).orElse(null);
	}

	/**
	 * Return a {@link VersionSource} based on the declared version.
	 *
	 * @return the version source derived from the path version text.
	 */
	public VersionSource versionSource() {
		return VersionSource.from(pathVersion());
	}

}
