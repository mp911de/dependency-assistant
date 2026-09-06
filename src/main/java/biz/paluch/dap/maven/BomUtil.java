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
	 * Register membership for a versioned BOM import.
	 * <p>Unresolved property versions and unavailable POMs are ignored.
	 * @see #resolveBillOfMaterials
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
	 * Resolve BOM membership, preferring cached results over a locally located POM.
	 * @return the BOM, or {@literal null} if no POM is found. A POM without
	 * resolvable managed entries produces empty membership.
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
