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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.PackageIdentity;

/**
 * Materialized dependency member of an Upgrade Plan item.
 *
 * <p>An inactive member remains part of the item for display and risk facts but
 * emits no dependency update because another member owns its version-property
 * write.
 *
 * @author Mark Paluch
 */
class ItemDependency extends Dependency {

	private final boolean active;

	ItemDependency(PackageIdentity pkg, ArtifactVersion currentVersion, boolean active) {
		super(pkg, currentVersion);
		this.active = active;
	}

	boolean isActive() {
		return active;
	}

	public String getArtifactCoordinates() {
		return getArtifactId().artifactId();
	}

}
