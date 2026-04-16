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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.RemoteRepository;
import com.intellij.externalSystem.MavenRepositoryData;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jspecify.annotations.Nullable;

import org.springframework.util.ClassUtils;

/**
 * PSI navigation utilities for Gradle build files.
 * <p>Provides methods to locate the version element at the cursor position in a
 * Groovy or Kotlin DSL dependency declaration, and to resolve the artifact
 * coordinates for that declaration. Gradle projects can declare dependencies in
 * multiple file types:
 * <ul>
 * <li>{@code build.gradle} — Groovy DSL build script</li>
 * <li>{@code build.gradle.kts} — Kotlin DSL build script</li>
 * <li>{@code settings.gradle} — Groovy DSL settings script</li>
 * <li>{@code settings.gradle.kts} — Kotlin DSL settings script</li>
 * <li>{@code gradle.properties} — project and system properties</li>
 * <li>{@code gradle/libs.versions.toml} — version catalog (TOML format)</li>
 * </ul>
 *
 * @author Mark Paluch
 * @see KotlinDslUtils
 */
class GradleUtils {

	static final boolean KOTLIN_AVAILABLE = ClassUtils.isPresent("org.jetbrains.kotlin.psi.KtStringTemplateExpression",
			GradleUtils.class.getClassLoader());

	/**
	 * Dependency configuration names that carry {@code group:artifact:version}
	 * strings.
	 */
	static final Set<String> DEPENDENCY_CONFIGS = Set.of("implementation", "api", "runtimeOnly", "compileOnly",
			"testImplementation", "testRuntimeOnly", "testCompileOnly", "androidTestImplementation",
			"debugImplementation",
			"releaseImplementation", "annotationProcessor", "kapt", "ksp", "provided", "compile", "runtime",
			"testCompile",
			"testRuntime", "classpath");

	/** Configuration names that represent a BOM / platform import (managed). */
	static final Set<String> PLATFORM_FUNCTIONS = Set.of("platform", "enforcedPlatform", "mavenBom");

	/**
	 * Name of the plugins section.
	 */
	static final String PLUGINS = "plugins";


	/** Name of the Gradle Settings file. */
	public static final String GROOVY_SETTINGS = "settings.gradle";

	/** Name of the Gradle Settings.kts file. */
	public static final String KOTLIN_SETTINGS = "settings.gradle.kts";

	/** Name of the Gradle properties file. */
	public static final String GRADLE_PROPERTIES = "gradle.properties";

	/** Name of the Gradle version catalog file. */
	public static final String LIBS_VERSIONS_TOML = "libs.versions.toml";

	/** Name of the Gradle version catalog file. */
	public static final String DEFAULT_TOML_LOCATION = "gradle/" + GradleUtils.LIBS_VERSIONS_TOML;

	/** Names of Gradle build/settings script files. */
	public static final Set<String> GRADLE_SCRIPT_NAMES = Set.of("build.gradle", "build.gradle.kts", GROOVY_SETTINGS,
			KOTLIN_SETTINGS);

	private GradleUtils() {
	}

	/**
	 * Returns {@code true} if the given {@link PsiFile} is a Gradle build or
	 * settings script, a {@code gradle.properties} file, or a
	 * {@code libs.versions.toml} version catalog.
	 */
	public static boolean isGradleFile(@Nullable PsiFile file) {
		return file != null && isGradleFile(file.getVirtualFile());
	}

	/**
	 * Returns {@code true} if the given {@link VirtualFile} is any Gradle-related
	 * build file.
	 */
	public static boolean isGradleFile(@Nullable VirtualFile file) {
		if (file == null) {
			return false;
		}
		return isGradleScript(file) || isGradlePropertiesFile(file) || isVersionCatalog(file);
	}

	/**
	 * Returns {@code true} if the file is a Gradle build or settings script
	 * ({@code build.gradle}, {@code build.gradle.kts}, {@code settings.gradle},
	 * {@code settings.gradle.kts}).
	 */
	public static boolean isGradleScript(@Nullable VirtualFile file) {
		return file != null && GRADLE_SCRIPT_NAMES.contains(file.getName());
	}

	/**
	 * Returns {@code true} if the file is a Gradle build or settings script
	 * ({@code build.gradle}, {@code build.gradle.kts}, {@code settings.gradle},
	 * {@code settings.gradle.kts}).
	 */
	public static boolean isGradleScript(@Nullable PsiFile file) {
		return file != null && GRADLE_SCRIPT_NAMES.contains(file.getName());
	}

	/**
	 * Returns {@code true} if the file is a {@code gradle.properties} file.
	 */
	public static boolean isGradlePropertiesFile(@Nullable VirtualFile file) {
		return file != null && GRADLE_PROPERTIES.equals(file.getName());
	}

	/**
	 * Returns {@code true} if the file is a {@code gradle.properties} file.
	 */
	public static boolean isGradlePropertiesFile(@Nullable PsiFile file) {
		return file != null && GRADLE_PROPERTIES.equals(file.getName());
	}

	/**
	 * Returns {@code true} if the file is a {@code *.versions.toml} version
	 * catalog.
	 */
	public static boolean isVersionCatalog(@Nullable VirtualFile file) {
		return file != null && file.getName().endsWith(".versions.toml");
	}

	/**
	 * Returns {@code true} if the file is a {@code *.versions.toml} version
	 * catalog.
	 */
	public static boolean isVersionCatalog(@Nullable PsiFile file) {
		return file != null && file.getName().endsWith(".versions.toml");
	}

	/**
	 * Returns {@code true} if the file uses the Kotlin DSL ({@code .kts}
	 * extension).
	 */
	public static boolean isKotlinDsl(@Nullable VirtualFile file) {
		return file != null && file.getName().endsWith(".kts");
	}

	/**
	 * Returns {@code true} if the file uses the Kotlin DSL ({@code .kts}
	 * extension).
	 */
	public static boolean isKotlinDsl(@Nullable PsiFile file) {
		return file != null && file.getName().endsWith(".kts");
	}

	/**
	 * Returns {@code true} if the file uses the Groovy DSL ({@code .gradle} without
	 * a {@code .kts} suffix).
	 */
	public static boolean isGroovyDsl(@Nullable PsiFile file) {
		return isGradleScript(file) && !isKotlinDsl(file);
	}

	/**
	 * Returns {@code true} if the file uses the Groovy DSL ({@code .gradle} without
	 * a {@code .kts} suffix).
	 */
	public static boolean isGroovyDsl(@Nullable VirtualFile file) {
		return isGradleScript(file) && !isKotlinDsl(file);
	}

	/**
	 * Locates the Gradle project root for the given file by walking up to the first
	 * ancestor that contains a settings file or returns the parent directory if
	 * none is found.
	 */
	static VirtualFile findProjectRoot(VirtualFile file) {

		VirtualFile dir = file.isDirectory() ? file : file.getParent();

		while (dir != null) {
			if (dir.findChild("settings.gradle") != null || dir.findChild("settings.gradle.kts") != null) {
				return dir;
			}
			dir = dir.getParent();
		}
		// Fall back to the immediate parent directory.
		return file.getParent();
	}

	/**
	 * Returns the element call refers to a plugin configuration.
	 */
	public static boolean isPlugin(@Nullable String name) {
		return "id".equals(name);
	}

	/**
	 * Returns the element call refers to a plugin section configuration.
	 */
	public static boolean isPluginSection(@Nullable String name) {
		return PLUGINS.equals(name);
	}

	/**
	 * Returns the element call refers to a dependency configuration.
	 */
	public static boolean isDependencySection(String name) {
		return DEPENDENCY_CONFIGS.contains(name);
	}

	/**
	 * Returns the element call refers to a managed (platform) configuration.
	 */
	public static boolean isPlatformSection(String name) {
		return PLATFORM_FUNCTIONS.contains(name);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	public static @Nullable String updateGavVersion(String gav, String newVersion) {
		String[] parts = gav.split(":");
		if (parts.length < 3) {
			return null;
		}
		parts[2] = newVersion;
		return String.join(":", parts);
	}

	/**
	 * Walks the ancestor directories of {@code file} until one of them matches a
	 * registered linked Gradle project root.
	 */
	public static @Nullable String findLinkedProjectPath(Project project, VirtualFile file) {

		GradleSettings settings = GradleSettings.getInstance(project);
		Collection<GradleProjectSettings> linkedProjects = settings.getLinkedProjectsSettings();

		if (linkedProjects.isEmpty()) {
			return null;
		}

		VirtualFile dir = file.isDirectory() ? file : file.getParent();

		while (dir != null) {
			String dirPath = dir.getPath();
			for (GradleProjectSettings ps : linkedProjects) {
				if (dirPath.equals(ps.getExternalProjectPath())) {
					return dirPath;
				}
			}
			dir = dir.getParent();
		}

		return null;
	}

	/**
	 * Read repositories from already-imported external project data. This is the
	 * direct consumer side of MavenRepositoriesProjectResolver: that resolver has
	 * already written MavenRepositoryData nodes into the project node.
	 */
	public static List<RemoteRepository> getRepositoriesFromImportedProject(Project ideaProject,
			String externalProjectPath) {

		ExternalProjectInfo projectInfo = ExternalSystemUtil.getExternalProjectInfo(ideaProject,
				GradleConstants.SYSTEM_ID,
				externalProjectPath);

		if (projectInfo == null) {
			return List.of();
		}

		DataNode<ProjectData> projectNode = projectInfo.getExternalProjectStructure();
		if (projectNode == null) {
			return List.of();
		}

		return getRepositories(projectNode);
	}

	/**
	 * Read repositories from a ProjectData node. MavenRepositoriesProjectResolver
	 * stores MavenRepositoryData as direct children of the project node.
	 */
	public static List<RemoteRepository> getRepositories(DataNode<ProjectData> projectNode) {

		Collection<DataNode<MavenRepositoryData>> repoNodes = ExternalSystemApiUtil.getChildren(projectNode,
				MavenRepositoryData.KEY);

		Set<RemoteRepository> repositories = new LinkedHashSet<>();
		for (DataNode<MavenRepositoryData> repoNode : repoNodes) {
			MavenRepositoryData data = repoNode.getData();
			if (data.getUrl().equals(RemoteRepository.mavenCentral().url())) {
				repositories.add(RemoteRepository.mavenCentral());
			} else {
				RemoteRepository repo = new RemoteRepository(data.getName(), data.getUrl(), null);
				repositories.add(repo);
			}
		}

		return List.copyOf(repositories);
	}

}
