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

package biz.paluch.dap.assistant;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.HasArtifactId;

/**
 * UI-facing update candidate enriched with assistant context and version-drift
 * details.
 *
 * <p>The wrapped {@link DependencyUpdateOption} provides the artifact and
 * target release data. The associated {@link InterfaceAssistant} identifies the
 * project format that can apply the update.
 *
 * @author Mark Paluch
 */
class UpdateCandidate implements HasArtifactId {

	private final DependencyUpdateOption option;

	private final InterfaceAssistant interfaceAssistant;

	private final DeclaredVersions declaredVersions;

	/**
	 * Create an update candidate.
	 *
	 * @param option the dependency update option to offer.
	 * @param assistant the format-specific assistant that can apply the update.
	 * @param declaredVersions the versions the artifact is declared at, used for
	 * drift reporting.
	 */
	public UpdateCandidate(DependencyUpdateOption option, InterfaceAssistant assistant,
			DeclaredVersions declaredVersions) {
		this.option = option;
		this.interfaceAssistant = assistant;
		this.declaredVersions = declaredVersions;
	}

	/**
	 * Return the declared versions for this candidate, carrying drift information.
	 *
	 * @return the declared versions associated with the candidate.
	 */
	public DeclaredVersions getDeclaredVersions() {
		return declaredVersions;
	}

	@Override
	public ArtifactId getArtifactId() {
		return option.getArtifactId();
	}

	/**
	 * Return the currently declared artifact version.
	 *
	 * @return the current version from the wrapped update option.
	 */
	public ArtifactVersion currentVersion() {
		return option.currentVersion();
	}

	/**
	 * Return the wrapped dependency update option.
	 *
	 * @return the update option used to render and apply the candidate.
	 */
	public DependencyUpdateOption option() {
		return option;
	}

	/**
	 * Return the format-specific assistant for this candidate.
	 *
	 * @return the assistant that can apply updates for the candidate's project
	 * format.
	 */
	public InterfaceAssistant interfaceAssistant() {
		return interfaceAssistant;
	}

	@Override
	public String toString() {
		return getArtifactId() + "@" + currentVersion();
	}

}
