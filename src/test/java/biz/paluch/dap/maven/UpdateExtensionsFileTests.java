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

package biz.paluch.dap.maven;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assertions.UpdatedBuildFile;
import biz.paluch.dap.assistant.BuildActionDelegate;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for {@link UpdateExtensionsFile}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdateExtensionsFileTests {

	@Test
	@ProjectFile(name = "extensions.xml", content = """
				<extensions>
					<extension>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
						<version>3.19.0</version>
					</extension>
				</extensions>
			""")
	void dependencyInlineVersionIsUpdated(PsiFile pom) {

		UpdatedBuildFile updated = applyUpdate(pom, "org.apache.commons", "commons-lang3", "3.19.0", "3.20.0");

		assertThat(updated).hasDependency("commons-lang3", "3.20.0");
	}

	public static UpdatedBuildFile applyUpdate(PsiFile targetFile, String groupId, String artifactId,
			String fromVersion, String toVersion) {

		ArtifactId id = ArtifactId.of(groupId, artifactId);
		ArtifactVersion current = ArtifactVersion.of(fromVersion);
		ArtifactVersion updateTo = ArtifactVersion.of(toVersion);

		Dependency dependency = new Dependency(id, current);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.declared(fromVersion));

		DependencyUpdate update = DependencyUpdate.from(dependency, updateTo);

		new BuildActionDelegate(targetFile.getProject(),
				(file, updates) -> new UpdateExtensionsFile().applyUpdates(targetFile, updates))
						.updateBuildFile(targetFile.getVirtualFile(), List.of(update));
		return UpdateTestSupport.of(targetFile);
	}

}
