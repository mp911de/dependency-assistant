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

package biz.paluch.dap.fixtures;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import com.intellij.openapi.progress.ProgressIndicator;

/**
 * @author Mark Paluch
 */
public record TestReleaseSource(String name) implements ReleaseSource {

	@Override
	public String getId() {
		return name;
	}

	@Override
	public List<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) {
		return List.of();
	}

}
