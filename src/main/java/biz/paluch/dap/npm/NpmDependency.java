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

package biz.paluch.dap.npm;

import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;

/**
 * NPM dependency declared in {@code dependencies} or {@code devDependencies}.
 *
 * <p>The {@link ArtifactId} uses the canonical NPM coordinate normalization
 * documented on {@link NpmPackageParser}: an unscoped package name produces
 * {@code groupId == artifactId == name}; a scoped name {@code @scope/name}
 * splits into {@code groupId = "@scope"} and {@code artifactId = "name"}.
 *
 * <p>The Git case is not a sibling type; it is the
 * {@link NpmVersionExpression.Git} variant of {@link #version()}. Anything
 * Git-specific (host, owner, repository, committish, replaceable range) lives
 * on the variant rather than on this record.
 *
 * @param artifactId the NPM coordinate; collisions with Maven artifacts are
 * permitted because lookup is content-addressed by release source.
 * @param version the parsed version expression.
 * @param declarationSource the {@code package.json} section that declares the
 * dependency.
 * @author Mark Paluch
 */
record NpmDependency(ArtifactId artifactId, NpmVersionExpression version, DeclarationSource declarationSource) {

	public VersionSource versionSource() {
		return version.versionSource();
	}

	public Optional<ArtifactVersion> artifactVersion() {
		return version().artifactVersion();
	}

}
