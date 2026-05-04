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
import java.util.function.Predicate;

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
	public static DependencyUpdate from(DependencyUpdateOption option) {
		return new DependencyUpdate(option.getArtifactId(), option.getRequiredUpdateTo(),
				option.getDependency().getDeclarationSources(), option.getDependency().getVersionSources());
	}

	/**
	 * Create an update command from a dependency and release.
	 */
	public static DependencyUpdate from(Dependency dependency, Release release) {
		return new DependencyUpdate(dependency.getArtifactId(), release.getVersion(),
				dependency.getDeclarationSources(), dependency.getVersionSources());
	}

	/**
	 * Evaluate the {@link Predicate} against the {@link VersionSource}s and return
	 * {@code true} if any match.
	 * @param versionSourcePredicate the predicate to evaluate.
	 * @return {@code true} if any version source matches; {@code false} otherwise.
	 */
	public boolean hasVersionSource(Predicate<VersionSource> versionSourcePredicate) {
		for (VersionSource versionSource : versionSources) {
			if (versionSourcePredicate.test(versionSource)) {
				return true;
			}
		}
		return false;
	}

}
