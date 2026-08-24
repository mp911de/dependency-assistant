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

import java.io.IOException;

import biz.paluch.dap.util.Sequence;
import com.intellij.openapi.progress.ProgressIndicator;

/**
 * Strategy for obtaining known repository tags for an artifact.
 *
 * <p>Throw {@link ArtifactNotFoundException} only for a definitive absence at
 * this source. Return an empty sequence when tag data is simply unavailable.
 *
 * @author Mark Paluch
 * @see biz.paluch.dap.github.GitHubReleases
 * @see biz.paluch.dap.metadata.GitLabReleases
 */
public interface TagSource {

	/**
	 * Return the unique identifier of this source.
	 * @return the source identifier.
	 */
	String getId();

	/**
	 * Return all known tags for the given artifact at this source.
	 * @param artifactId the artifact whose tags to retrieve.
	 * @param indicator the progress indicator used to honor cancellation.
	 * @return the tags known to this source.
	 * @throws ArtifactNotFoundException if the artifact is definitively absent.
	 * @throws IOException if tag data cannot be read from the source.
	 */
	Sequence<String> getTags(ArtifactId artifactId, ProgressIndicator indicator) throws IOException;

}
