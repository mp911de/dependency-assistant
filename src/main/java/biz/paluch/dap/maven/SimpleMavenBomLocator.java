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

import java.nio.file.Files;
import java.nio.file.Path;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.BomLocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * {@link BomLocator} resolving BOM POMs against the default local Maven
 * repository ({@code ~/.m2/repository}), serving IDEs without the Maven
 * integration.
 *
 * @author Mark Paluch
 */
public class SimpleMavenBomLocator implements BomLocator {

	private static final String MAVEN_LOCAL_CACHE_ROOT = ".m2/repository";

	private final Path mavenRepository = Path.of(System.getProperty("user.home"), MAVEN_LOCAL_CACHE_ROOT);

	private final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();

	@Override
	public @Nullable VirtualFile locateBom(Project project, ArtifactId artifactId, ArtifactVersion version) {

		String fileName = "%s-%s.pom".formatted(artifactId.artifactId(), version);
		Path pomFile = mavenRepository.resolve(artifactId.groupId().replace('.', '/'))
				.resolve(artifactId.artifactId()).resolve(version.toString()).resolve(fileName);

		return Files.isRegularFile(pomFile) ? localFileSystem.findFileByNioFile(pomFile) : null;
	}

}
