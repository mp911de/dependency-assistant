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

package biz.paluch.dap.lookup;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactReference;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Integration tests for {@link VersionUpgradeLookup}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class VersionUpgradeLookupTests {

	@Test
	@ProjectFile(name = "build.gradle", content = "")
	void fallsBackToDeclarationVersionWithoutProjectState(PsiFile file) {

		ArtifactVersion declaredVersion = ArtifactVersion.of("6.2.1");
		ArtifactReference reference = ArtifactReference.from(it -> it
				.artifact(ArtifactId.of("org.springframework", "spring-core"))
				.version(declaredVersion)
				.versionSource(VersionSource.declared(declaredVersion.toString()))
				.declarationSource(DeclarationSource.dependency())
				.declarationElement(file)
				.versionLiteral(file));
		VersionUpgradeLookup lookup = new VersionUpgradeLookup(new StateService(), null, element -> reference);

		assertThat(lookup.getCurrentVersion(lookup.resolveArtifactReference(file))).isEqualTo(declaredVersion);
	}

}
