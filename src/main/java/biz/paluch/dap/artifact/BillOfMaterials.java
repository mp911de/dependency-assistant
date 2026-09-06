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

package biz.paluch.dap.artifact;

import java.util.Map;

/**
 * Managed versions supplied by one release of a Bill of Materials.
 * <p>Membership belongs to a specific BOM version. An empty member map means no
 * member versions are known. A BOM always has a version.
 * <p>Equality uses package identity and version, excluding members.
 * Implementations must preserve this equality across implementation types.
 *
 * @author Mark Paluch
 * @see DeclarationSource.Bom
 */
public interface BillOfMaterials extends VersionedPackage {

	/**
	 * Create a BOM with a copy of the supplied member versions.
	 */
	static BillOfMaterials of(PackageIdentity pkg, ArtifactVersion version,
			Map<ArtifactId, ArtifactVersion> members) {
		return new DefaultBillOfMaterials(pkg, version, members);
	}

	/**
	 * Create a BOM using the given identity and version and a copy of the members.
	 */
	static BillOfMaterials from(VersionedPackage bom, Map<ArtifactId, ArtifactVersion> members) {
		return of(bom.getPackageIdentity(), bom.getVersion(), members);
	}

	boolean isEmpty();

	/**
	 * Return the unmodifiable map of managed artifact versions.
	 */
	Map<ArtifactId, ArtifactVersion> getMembers();

	@Override
	default boolean isVersioned() {
		return true;
	}

}
