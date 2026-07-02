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

package biz.paluch.dap.github;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitHubAssistant.GitHubInterface}.
 *
 * @author Mark Paluch
 */
class GitHubInterfaceUnitTests {

	@Test
	void documentationTextRendersShortShaForGitVersions() {

		InterfaceAssistant assistant = GitHubAssistant.GitHubInterface.INSTANCE;
		GitVersion version = GitVersion.of("d1185ce59f7757407fe6a5febb1e03e3dba2a530", ArtifactVersion.of("v4.2.0"));

		assertThat(assistant.getDocumentationText(version)).isEqualTo("v4.2.0 (d1185ce5)");
		assertThat(assistant.getDocumentationText(ArtifactVersion.of("v4.2.0"))).isEqualTo("v4.2.0");
	}

}
