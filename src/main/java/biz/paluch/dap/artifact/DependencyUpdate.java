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

package biz.paluch.dap.artifact;

import java.util.Collection;

/**
 * Value object to represent a dependency update.
 *
 * @param coordinate the dependency coordinate to update.
 * @param version the selected target version.
 * @param declarationSources the declaration sources to update.
 * @param versionSources the version sources to update.
 */
public record DependencyUpdate(ArtifactId coordinate, ArtifactVersion version,
		Collection<DeclarationSource> declarationSources, Collection<VersionSource> versionSources) {

	/**
	 * Create an update command from a selected update option.
	 */
	public static DependencyUpdate of(DependencyUpdateOption option) {
		return new DependencyUpdate(option.getArtifactId(), option.getRequiredUpdateTo(),
				option.getDependency().getDeclarationSources(), option.getDependency().getVersionSources());
	}

}
