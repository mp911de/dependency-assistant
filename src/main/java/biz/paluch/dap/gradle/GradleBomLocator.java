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

package biz.paluch.dap.gradle;

import java.io.File;
import java.nio.file.Path;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.BomLocator;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.gradle.service.execution.GradleUserHomeUtil;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jspecify.annotations.Nullable;

/**
 * {@link BomLocator} resolving BOM POMs from the Gradle module cache
 * ({@code caches/modules-2/files-2.1}) under the configured Gradle user home,
 * scanning the per-artifact hash directories for the POM file.
 *
 * @author Mark Paluch
 */
public class GradleBomLocator implements BomLocator {

	private static final String MODULE_CACHE_ROOT = "caches/modules-2/files-2.1";

	private final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();

	@Override
	public @Nullable VirtualFile locateBom(Project project, ArtifactId artifactId, ArtifactVersion version) {

		File versionDirectory = resolveGradleUserHome(project).resolve(MODULE_CACHE_ROOT)
				.resolve(artifactId.groupId()) //
				.resolve(artifactId.artifactId()) //
				.resolve(version.toString()).toFile();

		File[] hashDirectories = versionDirectory.listFiles();
		if (hashDirectories == null) {
			return null;
		}

		String fileName = "%s-%s.pom".formatted(artifactId.artifactId(), version);
		for (File hashDirectory : hashDirectories) {

			File file = new File(hashDirectory, fileName);
			if (file.isFile()) {
				return localFileSystem.findFileByIoFile(file);
			}
		}
		return null;
	}

	private static Path resolveGradleUserHome(Project project) {

		String serviceDirectory = GradleSettings.getInstance(project).getServiceDirectoryPath();
		if (StringUtils.hasText(serviceDirectory)) {
			return Path.of(serviceDirectory);
		}

		return GradleUserHomeUtil.gradleUserHomeDir().toPath();
	}

}
