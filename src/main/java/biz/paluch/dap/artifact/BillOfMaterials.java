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
 * <p>Instances are value objects; equality is defined over the full BOM
 * coordinates including the version (GAV), excluding the member map.
 *
 * @author Mark Paluch
 * @see DeclarationSource.Bom
 */
public interface BillOfMaterials extends HasArtifactId, VersionAware {

	/**
	 * Create a Bill of Materials for the given BOM coordinates and members.
	 *
	 * @param artifactId the BOM artifact coordinates.
	 * @param version the BOM version the membership is scoped to.
	 * @param members the managed members keyed by artifact coordinates.
	 * @return the Bill of Materials; guaranteed to be not {@literal null}.
	 */
	static BillOfMaterials of(ArtifactId artifactId, ArtifactVersion version,
			Map<ArtifactId, ArtifactVersion> members) {
		return new DefaultBillOfMaterials(artifactId, version, members);
	}

	/**
	 * Return the managed members of this BOM keyed by artifact coordinates, each
	 * mapped to its managed version.
	 *
	 * @return an unmodifiable member map; may be empty, guaranteed to be not .
	 */
	Map<ArtifactId, ArtifactVersion> getMembers();

}
