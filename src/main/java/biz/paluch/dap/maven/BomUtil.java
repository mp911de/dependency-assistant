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

import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.BomLocator;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Resolves the managed member map of a Bill of Materials, consulting the cache
 * before locating and parsing the BOM POM from a local repository.
 *
 * @author Mark Paluch
 * @see BomLocator
 * @see MavenBomParser
 */
public class BomUtil {

	private BomUtil() {
	}

	/**
	 * Resolve the managed members of the given BOM version, preferring cached
	 * membership over locating and parsing the BOM POM.
	 *
	 * @param cache the cache holding previously resolved memberships.
	 * @param project the project providing repository configuration.
	 * @param pkg the BOM package identity.
	 * @param version the BOM version.
	 * @return the managed members keyed by artifact coordinates; empty when the BOM
	 * cannot be located or parsed.
	 */
	public static Map<ArtifactId, ArtifactVersion> resolveBom(Cache cache, Project project, PackageIdentity pkg,
			ArtifactVersion version) {

		CachedArtifact cachedArtifact = cache.findCachedArtifact(pkg);
		if (cachedArtifact != null && cachedArtifact.hasBom(version.toString())) {
			return cachedArtifact.getBom(version);
		}

		VirtualFile bomPom = BomLocator.findBom(project, pkg.getArtifactId(), version);
		if (bomPom == null) {
			return Map.of();
		}

		Map<ArtifactId, ArtifactVersion> members = new MavenBomParser(project, bomPom).readMembers();
		return members != null ? members : Map.of();
	}

}
