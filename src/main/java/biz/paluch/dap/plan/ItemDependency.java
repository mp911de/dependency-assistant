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

package biz.paluch.dap.plan;

import java.util.stream.Collectors;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.VersionSource;
import org.jspecify.annotations.Nullable;

/**
 * Materialized dependency member of an Upgrade Plan item.
 *
 * <p>An implicit group member remains a full member of the item for display and
 * risk facts and is upgraded through the item's target, but emits no dependency
 * update of its own because another member owns its version-property write.
 *
 * @author Mark Paluch
 */
class ItemDependency extends Dependency {

	private final boolean implicit;

	private final String currentVersionString;

	private @Nullable String versionProperties;

	ItemDependency(PackageIdentity pkg, ArtifactVersion currentVersion, boolean implicit) {
		super(pkg, currentVersion);
		this.implicit = implicit;
		this.currentVersionString = currentVersion.toDocumentationString();
	}

	boolean isImplicit() {
		return implicit;
	}

	public String getArtifactCoordinates() {
		return getPackageSystem().getCoordinates(getArtifactId());
	}

	public String getCurrentVersionString() {
		return currentVersionString;
	}

	public String getVersionProperties() {
		if (versionProperties == null) {
			this.versionProperties = getVersionSources().stream()
					.filter(VersionSource::isProperty)
					.map(it -> (VersionSource.VersionProperty) it)
					.map(VersionSource.VersionProperty::getProperty)
					.map(it -> "${" + it + "}")
					.collect(Collectors.joining(", "));
		}
		return this.versionProperties;
	}
}
