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
 * Resolves the managed members of a Maven Bill of Materials (BOM), consulting
 * cached membership before locating and parsing the BOM POM.
 *
 * @author Mark Paluch
 * @see PomLocator
 * @see MavenBomParser
 */
public class BomUtil {

	private BomUtil() {
	}

	/**
	 * Resolve and register the managed members of the given BOM import.
	 *
	 * <p>Non-BOM declarations, unversioned declarations, and versions retaining a
	 * property reference are ignored. If neither cached membership nor a local POM
	 * is available, no BOM is registered. A located POM with no resolvable managed
	 * entries produces a BOM with empty membership.
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
	 * Resolve the Bill of Materials for the given BOM version.
	 *
	 * <p>Cached membership takes precedence. Otherwise, the BOM POM is located
	 * through the registered {@link PomLocator} extensions and parsed. A located
	 * POM with no resolvable managed entries produces a BOM with empty membership.
	 *
	 * @param cache the cache holding previously resolved memberships.
	 * @param project the project providing repository configuration.
	 * @param bom the BOM identity and version to resolve members for.
	 * @return the resolved Bill of Materials, or {@literal null} when no locator
	 * finds its POM.
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
