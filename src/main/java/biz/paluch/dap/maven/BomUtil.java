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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.BillOfMaterials;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PomLocator;
import biz.paluch.dap.artifact.VersionedPackage;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.support.ArtifactDeclaration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the managed member map of a Bill of Materials, consulting the cache
 * before locating and parsing the BOM POM from a local repository.
 *
 * @author Mark Paluch
 * @see PomLocator
 * @see MavenBomParser
 */
public class BomUtil {

	private BomUtil() {
	}

	/**
	 * Resolve the managed members of the given declaration and register them with
	 * the collector, provided the declaration is a Bill of Materials import
	 * carrying a fully resolved version.
	 * <p>A BOM whose contents cannot be resolved is registered without members, so
	 * the artifact is still recorded as a BOM. Declarations whose version literal
	 * retains an unresolved property reference are skipped: their coordinates
	 * cannot be located and would only produce a phantom artifact entry.
	 *
	 * @param cache the cache holding previously resolved memberships.
	 * @param project the project providing repository configuration.
	 * @param declaration the declaration to inspect.
	 * @param collector the collector receiving the resolved Bill of Materials.
	 */
	public static void registerBillOfMaterials(Cache cache, Project project, ArtifactDeclaration declaration,
			DependencyCollector collector) {

		if (!(declaration.getDeclarationSource() instanceof DeclarationSource.Bom)
				|| !declaration.isVersioned()) {
			return;
		}

		if (declaration.getVersion().toString().contains("${")) {
			return;
		}

		BillOfMaterials billOfMaterials = resolveBillOfMaterials(cache, project, declaration);
		if (billOfMaterials != null) {
			collector.registerBillOfMaterials(billOfMaterials);
		}
	}

	/**
	 * Resolve the Bill of Materials for the given BOM version, preferring cached
	 * membership over locating and parsing the BOM POM.
	 * <p>An unresolvable BOM yields {@literal null} rather than a member-less Bill
	 * of Materials, so callers can tell an empty membership apart from a BOM that
	 * could not be located.
	 *
	 * @param cache the cache holding previously resolved memberships.
	 * @param project the project providing repository configuration.
	 * @param bom the BOM identity and version to resolve members for.
	 * @return the resolved Bill of Materials, or {@literal null} when the BOM
	 * cannot be located or parsed.
	 */
	public static @Nullable BillOfMaterials resolveBillOfMaterials(Cache cache, Project project,
			VersionedPackage bom) {

		ArtifactVersion version = bom.getVersion();
		CachedArtifact cachedArtifact = cache.findCachedArtifact(bom.getPackageIdentity());
		if (cachedArtifact != null && cachedArtifact.hasBom(version)) {
			return cachedArtifact.getBom(version);
		}

		VirtualFile bomPom = PomLocator.findPom(project, bom.getArtifactId(), version);
		if (bomPom == null) {
			return null;
		}

		MavenBomParser mavenBomParser = new MavenBomParser(project, bomPom);
		return BillOfMaterials.from(bom, mavenBomParser.readMembers());
	}

}
