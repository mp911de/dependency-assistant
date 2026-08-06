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

package biz.paluch.dap.fixtures;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.BillOfMaterials;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionedPackage;

/**
 * Versioned artifact coordinates fixture parsed from the
 * {@code group:artifact:version} form. Identity covers the full coordinates,
 * including the literal version form, so versions such as {@code 1.0} and
 * {@code 1.0.0} remain distinct when used as map keys.
 *
 * @author Mark Paluch
 */
public class Coordinates implements VersionedPackage {

	private final ArtifactId artifactId;

	private final ArtifactVersion version;

	private Coordinates(ArtifactId artifactId, ArtifactVersion version) {
		this.artifactId = artifactId;
		this.version = version;
	}

	/**
	 * Create coordinates from the {@code group:artifact:version} form.
	 */
	public static Coordinates of(String coordinates) {

		String[] segments = coordinates.split(":");
		if (segments.length != 3) {
			throw new IllegalArgumentException(
					"Coordinates '%s' must use the group:artifact:version form".formatted(coordinates));
		}
		return of(segments[0], segments[1], segments[2]);
	}

	/**
	 * Create coordinates from group id, artifact id, and version.
	 */
	public static Coordinates of(String groupId, String artifactId, String version) {
		return new Coordinates(ArtifactId.of(groupId, artifactId), ArtifactVersion.of(version));
	}

	/**
	 * Create coordinates from an artifact identifier and version string.
	 */
	public static Coordinates of(ArtifactId artifactId, String version) {
		return new Coordinates(artifactId.detach(), ArtifactVersion.of(version));
	}

	/**
	 * Create a Bill of Materials fixture from the given coordinates and member
	 * declarations.
	 *
	 * @param coordinates the BOM coordinates in {@code group:artifact:version}
	 * form.
	 * @param customizer the managed member declarations.
	 * @return the Bill of Materials fixture.
	 */
	public static BillOfMaterials bom(String coordinates, Consumer<BomBuilder> customizer) {

		Coordinates bom = of(coordinates);
		BomBuilder builder = new BomBuilder(bom);
		customizer.accept(builder);
		return builder.build();
	}

	@Override
	public PackageIdentity getPackageIdentity() {
		return PackageIdentity.of(getArtifactId(), PackageSystem.MAVEN);
	}

	@Override
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	@Override
	public boolean isVersioned() {
		return true;
	}

	@Override
	public ArtifactVersion getVersion() {
		return version;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Coordinates coordinates && artifactId.equals(coordinates.artifactId)
				&& version.toString().equals(coordinates.version.toString());
	}

	@Override
	public int hashCode() {
		return Objects.hash(artifactId, version.toString());
	}

	@Override
	public String toString() {
		return artifactId.groupId() + ":" + artifactId.artifactId() + ":" + version;
	}

	/**
	 * Managed member declarations for a Bill of Materials fixture.
	 */
	public static class BomBuilder {

		private final Coordinates bom;

		private final Map<ArtifactId, ArtifactVersion> members = new LinkedHashMap<>();

		private BomBuilder(Coordinates bom) {
			this.bom = bom;
		}

		/**
		 * Add managed member coordinates in {@code group:artifact:version} form.
		 */
		public void member(String coordinates) {
			Coordinates member = Coordinates.of(coordinates);
			members.put(member.getArtifactId(), member.getVersion());
		}

		private BillOfMaterials build() {
			return BillOfMaterials.from(bom, members);
		}

	}

}
