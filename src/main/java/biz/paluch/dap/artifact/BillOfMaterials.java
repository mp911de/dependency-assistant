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

import java.util.Map;

/**
 * Bill of Materials: the managed member set of a BOM artifact at one specific
 * BOM version.
 *
 * <p>A BOM (Maven {@code dependencyManagement} import, Gradle platform) manages
 * an artifact set that never appears in the consuming build file. The BOM's own
 * {@code dependencyManagement} section is the truth for the member artifacts
 * along with their managed versions. Member sets differ across BOM releases, so
 * membership is always scoped to one BOM version.
 *
 * <p>A Bill of Materials is always versioned, so {@link #isVersioned()} returns
 * {@literal true} and {@link #getVersion()} never fails.
 *
 * <p>Instances are value objects; equality is defined over the BOM identity,
 * that is its {@link #getPackageIdentity() package identity} and
 * {@link #getVersion() version}, excluding the member map. Implementations must
 * honour that definition across implementation types so a Bill of Materials can
 * serve as a set element or map key regardless of where it was created.
 *
 * @author Mark Paluch
 * @see DeclarationSource.Bom
 * @see VersionedPackage
 */
public interface BillOfMaterials extends VersionedPackage {

	/**
	 * Create a Bill of Materials for the given BOM coordinates and members.
	 *
	 * @param pkg the BOM package identity.
	 * @param version the BOM version the membership is scoped to.
	 * @param members the managed members keyed by artifact coordinates; copied into
	 * the returned instance.
	 * @return the Bill of Materials; guaranteed to be not {@literal null}.
	 */
	static BillOfMaterials of(PackageIdentity pkg, ArtifactVersion version,
			Map<ArtifactId, ArtifactVersion> members) {
		return new DefaultBillOfMaterials(pkg, version, members);
	}

	/**
	 * Create a Bill of Materials adopting the identity and version of the given BOM
	 * coordinates.
	 *
	 * @param bom the BOM identity and version; typically the declaration that
	 * imported the BOM or the candidate version being resolved.
	 * @param members the managed members keyed by artifact coordinates; copied into
	 * the returned instance.
	 * @return the Bill of Materials; guaranteed to be not {@literal null}.
	 */
	static BillOfMaterials from(VersionedPackage bom, Map<ArtifactId, ArtifactVersion> members) {
		return of(bom.getPackageIdentity(), bom.getVersion(), members);
	}

	/**
	 * Return {@code true} if this BOM has no members.
	 *
	 * @return {@code true} if this BOM has no members; {@code false} otherwise.
	 */
	boolean isEmpty();

	/**
	 * Return the managed members of this BOM keyed by artifact coordinates, each
	 * mapped to its managed version.
	 *
	 * @return the member map.
	 */
	Map<ArtifactId, ArtifactVersion> getMembers();

	@Override
	default boolean isVersioned() {
		return true;
	}

}
