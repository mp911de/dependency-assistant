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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Default {@link BillOfMaterials} implementation. Equality follows the
 * {@link BillOfMaterials} contract: package identity plus version, excluding
 * the member map.
 *
 * @author Mark Paluch
 */
class DefaultBillOfMaterials implements BillOfMaterials {

	private final PackageIdentity pkg;

	private final ArtifactVersion version;

	private final Map<ArtifactId, ArtifactVersion> members;

	DefaultBillOfMaterials(PackageIdentity pkg, ArtifactVersion version,
			Map<ArtifactId, ArtifactVersion> members) {
		this.pkg = pkg;
		this.version = version;
		this.members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
	}

	@Override
	public ArtifactId getArtifactId() {
		return pkg.getArtifactId();
	}

	@Override
	public PackageIdentity getPackageIdentity() {
		return pkg;
	}

	@Override
	public ArtifactVersion getVersion() {
		return version;
	}

	@Override
	public boolean isEmpty() {
		return members.isEmpty();
	}

	@Override
	public Map<ArtifactId, ArtifactVersion> getMembers() {
		return members;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (!(o instanceof BillOfMaterials that)) {
			return false;
		}
		return Objects.equals(pkg, that.getPackageIdentity()) && Objects.equals(version, that.getVersion());
	}

	@Override
	public int hashCode() {
		return Objects.hash(pkg, version);
	}

	@Override
	public String toString() {
		return "%s:%s [%d members]".formatted(pkg, version, members.size());
	}

}
