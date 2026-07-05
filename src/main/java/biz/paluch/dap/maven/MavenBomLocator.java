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

import java.nio.file.Path;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.BomLocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jspecify.annotations.Nullable;

/**
 * {@link BomLocator} resolving BOM POMs through the project's Maven repository
 * configuration.
 *
 * @author Mark Paluch
 */
public class MavenBomLocator implements BomLocator {

	private final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();

	@Override
	public @Nullable VirtualFile locateBom(Project project, ArtifactId artifactId, ArtifactVersion version) {

		MavenId mavenId = new MavenId(artifactId.groupId(), artifactId.artifactId(), version.toString());
		Path pomFile = MavenUtil.getRepositoryFile(project, mavenId, "pom", null);
		return pomFile == null ? null : localFileSystem.findFileByNioFile(pomFile);
	}

}
