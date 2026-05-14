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

package biz.paluch.dap.maven;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import org.jspecify.annotations.Nullable;

/**
 * Parsed wrapper property.
 * 
 * @author Mark Paluch
 */
record WrapperEntry(WrapperProperty property,
		PropertyImpl propertyLiteral, PropertyValueImpl versionLiteral, RemoteRepository repository,
		String pathVersion, String fileVersion) {

	public boolean hasArtifactId(ArtifactId coordinate) {
		return property.artifactId().equals(coordinate);
	}

	/**
	 * Return whether the path and file version segments carry the same text.
	 * <p>A well-formed wrapper URL holds the same version twice; mismatched
	 * versions indicate a malformed or mid-typed URL that downstream consumers
	 * (such as dependency collection) typically reject.
	 */
	public boolean hasConsistentVersions() {
		return pathVersion.equals(fileVersion);
	}

	@Nullable
	public ArtifactVersion version() {
		return ArtifactVersion.from(pathVersion).orElse(null);
	}

	/**
	 * Return a {@link VersionSource} based on the declared version.
	 */
	public VersionSource versionSource() {
		return StringUtils.hasText(pathVersion) ? VersionSource.declared(pathVersion) : VersionSource.none();
	}

}
