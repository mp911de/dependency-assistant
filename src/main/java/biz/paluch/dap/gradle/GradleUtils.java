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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.util.StringUtils;
import com.intellij.externalSystem.MavenRepositoryData;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jspecify.annotations.Nullable;

import org.springframework.util.ClassUtils;

/**
 * Utility methods for Gradle build files and related project metadata.
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
	private static final Set<String> DEPENDENCY_CONFIGS = Set.of("implementation", "api", "runtimeOnly", "compileOnly",
			"testImplementation", "testRuntimeOnly", "testCompileOnly", "androidTestImplementation",
			"debugImplementation", "releaseImplementation", "annotationProcessor", "kapt", "ksp", "provided", "compile",
			"runtime", "testCompile", "testRuntime", "classpath");

	/**
	 * Configuration name suffixes that identify custom dependency configurations
	 * derived from the canonical ones (e.g. {@code optionalApi},
	 * {@code integrationTestImplementation}, {@code testFixturesRuntimeOnly}).
	 */
	private static final Set<String> DEPENDENCY_CONFIG_SUFFIXES = Set.of("Implementation", "Api", "RuntimeOnly",
			"CompileOnly", "AnnotationProcessor");

	/**
	 * Configuration names that represent a BOM / platform import (managed).
	 */
	static final Set<String> PLATFORM_FUNCTIONS = Set.of("platform", "enforcedPlatform", "mavenBom");

	/**
	 * Name of the plugins section.
	 */
	static final String PLUGINS = "plugins";

	/**
	 * Name of the {@code id} plugin/catalog key.
	 */
	static final String ID = "id";

	/**
	 * Name of the {@code alias} catalog consumer call.
	 */
	private static final String ALIAS = "alias";

	/**
	 * Name of the {@code group} dependency key.
	 */
	static final String GROUP = "group";

	/**
	 * Name of the {@code name} dependency key.
	 */
	static final String NAME = "name";

	/**
	 * Name of the {@code version} dependency key/call.
	 */
	static final String VERSION = "version";

	/** Name of the Gradle Settings file. */
	static final String GROOVY_SETTINGS = "settings.gradle";

	/** Name of the Gradle Settings.kts file. */
	static final String KOTLIN_SETTINGS = "settings.gradle.kts";

	/** Name of the Gradle properties file. */
	static final String GRADLE_PROPERTIES = "gradle.properties";

	/** Name of the Gradle version catalog file. */
	static final String LIBS_VERSIONS_TOML = "libs.versions.toml";

	static final String GRADLE_DIR = "gradle";

	/** Default relative path of the Gradle version catalog file. */
	static final String DEFAULT_TOML_LOCATION = GRADLE_DIR + "/" + LIBS_VERSIONS_TOML;

	/** File name suffixes of Gradle build/settings script files. */
	private static final Set<String> GRADLE_SCRIPT_SUFFIXES = Set.of(".gradle", ".gradle.kts");

	private GradleUtils() {
	}

	/**
	 * Return whether the given {@link PsiFile} is a Gradle-related file.
	 */
	static boolean isGradleFile(@Nullable PsiFile file) {
		if (file == null || isVirtualFileWindow(file.getVirtualFile())) {
			return false;
		}
		return isGradleScript(file) || isGradlePropertiesFile(file) || isVersionCatalog(file);
	}

	/**
	 * Return whether the given {@link VirtualFile} is a Gradle-related file.
	 */
	static boolean isGradleFile(@Nullable VirtualFile file) {
		if (file == null || isVirtualFileWindow(file)) {
			return false;
		}
		return isGradleScript(file) || isGradlePropertiesFile(file) || isVersionCatalog(file);
	}

	/**
	 * Return {@literal true} if the file is a Gradle build or settings script
	 * ({@code *.gradle}, {@code *.gradle.kts}).
	 * <p>Build scripts may use any base name (e.g.
	 * {@code spring-security-config.gradle}); settings scripts are the files named
	 * {@link #GROOVY_SETTINGS} / {@link #KOTLIN_SETTINGS}. Code that deliberately
	 * parses settings content must check those names explicitly. Directories and
	 * files without a base name (such as the {@code .gradle} cache directory) do
	 * not qualify.
	 */
	static boolean isGradleScript(@Nullable VirtualFile file) {
		return file != null && !isVirtualFileWindow(file) && !file.isDirectory() && hasGradleScriptName(file.getName());
	}

	/**
	 * Return {@literal true} if the file is a Gradle build or settings script
	 * ({@code *.gradle}, {@code *.gradle.kts}).
	 * <p>Build scripts may use any base name (e.g.
	 * {@code spring-security-config.gradle}); settings scripts are the files named
	 * {@link #GROOVY_SETTINGS} / {@link #KOTLIN_SETTINGS}. Code that deliberately
	 * parses settings content must check those names explicitly. Files without a
	 * base name (a file named just {@code .gradle}) do not qualify.
	 */
	static boolean isGradleScript(@Nullable PsiFile file) {
		return file != null && hasGradleScriptName(file.getName()) && !isVirtualFileWindow(file);
	}

	/**
	 * Return the Gradle scripts directly contained in {@code directory}, sorted by
	 * file name.
	 */
	static List<VirtualFile> findGradleScripts(VirtualFile directory) {

		List<VirtualFile> scripts = new ArrayList<>();
		for (VirtualFile child : directory.getChildren()) {
			if (isGradleScript(child)) {
				scripts.add(child);
			}
		}
		scripts.sort(Comparator.comparing(VirtualFile::getName));
		return scripts;
	}

	private static boolean hasGradleScriptName(String fileName) {

		for (String suffix : GRADLE_SCRIPT_SUFFIXES) {
			if (fileName.length() > suffix.length() && fileName.endsWith(suffix)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return {@literal true} if the file is a {@code gradle.properties} file.
	 */
	static boolean isGradlePropertiesFile(@Nullable VirtualFile file) {
		return file != null && !isVirtualFileWindow(file) && GRADLE_PROPERTIES.equals(file.getName());
	}

	/**
	 * Return {@literal true} if the file is a {@code gradle.properties} file.
	 */
	static boolean isGradlePropertiesFile(@Nullable PsiFile file) {
		return file != null && GRADLE_PROPERTIES.equals(file.getName()) && !isVirtualFileWindow(file);
	}

	/**
	 * Return {@literal true} if the file is a {@code *.versions.toml} version
	 * catalog.
	 */
	static boolean isVersionCatalog(@Nullable VirtualFile file) {
		return file != null && !isVirtualFileWindow(file) && file.getName().endsWith(".versions.toml");
	}

	/**
	 * Return {@literal true} if the file is a {@code *.versions.toml} version
	 * catalog.
	 */
	static boolean isVersionCatalog(@Nullable PsiFile file) {
		return file != null && file.getName().endsWith(".versions.toml") && !isVirtualFileWindow(file);
	}

	/**
	 * Return {@literal true} if the file uses the Kotlin DSL ({@code .kts}
	 * extension).
	 */
	static boolean isKotlinDsl(@Nullable VirtualFile file) {
		return file != null && !isVirtualFileWindow(file) && file.getName().endsWith(".kts");
	}

	/**
	 * Return {@literal true} if the file uses the Kotlin DSL ({@code .kts}
	 * extension).
	 */
	static boolean isKotlinDsl(@Nullable PsiFile file) {
		return file != null && file.getName().endsWith(".kts");
	}

	/**
	 * Return {@literal true} if the file uses the Groovy DSL ({@code .gradle}
	 * without a {@code .kts} suffix).
	 */
	static boolean isGroovyDsl(@Nullable PsiFile file) {
		return isGradleScript(file) && !isKotlinDsl(file);
	}

	/**
	 * Return {@literal true} if the file uses the Groovy DSL ({@code .gradle}
	 * without a {@code .kts} suffix).
	 */
	static boolean isGroovyDsl(@Nullable VirtualFile file) {
		return isGradleScript(file) && !isKotlinDsl(file);
	}

	private static boolean isVirtualFileWindow(PsiFile file) {
		return isVirtualFileWindow(file.getVirtualFile());
	}

	private static boolean isVirtualFileWindow(VirtualFile file) {
		return file instanceof VirtualFileWindow;
	}

	/**
	 * Locate the Gradle project root for the given file.
	 *
	 * @see #findProjectRoot(Project, VirtualFile)
	 */
	static VirtualFile findProjectRoot(PsiFile file) {
		return findProjectRoot(file.getProject(), file.getVirtualFile());
	}

	/**
	 * Locate the Gradle project root for the given file by walking up from its
	 * containing directory to the first directory holding a
	 * {@link #GROOVY_SETTINGS} or {@link #KOTLIN_SETTINGS} file.
	 * <p>The walk is bounded by the linked Gradle project root, or by the content
	 * root containing the file when it is not part of a linked Gradle project, so a
	 * stray settings file outside the project cannot widen resolution scope.
	 *
	 * @return the first directory containing a settings file; the linked Gradle
	 * project root if none is found; the containing directory otherwise.
	 * @throws IllegalStateException if the file has no parent directory.
	 */
	static VirtualFile findProjectRoot(Project project, VirtualFile file) {

		VirtualFile start = file.isDirectory() ? file : file.getParent();
		if (start == null) {
			throw new IllegalStateException("Cannot determine Gradle project root for %s".formatted(file.getPath()));
		}

		VirtualFile linkedRoot = findLinkedProjectRoot(project, file);
		VirtualFile ceiling = linkedRoot != null ? linkedRoot
				: ProjectFileIndex.getInstance(project).getContentRootForFile(file);

		for (VirtualFile dir = start; dir != null; dir = dir.getParent()) {

			if (dir.findChild(GROOVY_SETTINGS) != null || dir.findChild(KOTLIN_SETTINGS) != null) {
				return dir;
			}
			if (ceiling == null || !VfsUtil.isAncestor(ceiling, dir, true)) {
				break;
			}
		}

		return linkedRoot != null ? linkedRoot : start;
	}

	/**
	 * Return whether the call name refers to a plugin configuration.
	 */
	static boolean isPlugin(@Nullable String name) {
		return ID.equals(name);
	}

	/**
	 * Return whether the call name refers to a plugin section configuration.
	 */
	static boolean isPluginSection(@Nullable String name) {
		return StringUtils.hasText(name) && PLUGINS.equals(name);
	}

	/**
	 * Return whether the call name refers to a dependency configuration.
	 * <p>Matches the canonical configuration names as well as custom configurations
	 * that follow Gradle's camelCase derivation convention, such as
	 * {@code optionalApi} or {@code integrationTestImplementation}.
	 */
	static boolean isDependencySection(@Nullable String name) {

		if (!StringUtils.hasText(name)) {
			return false;
		}

		if (DEPENDENCY_CONFIGS.contains(name)) {
			return true;
		}

		for (String suffix : DEPENDENCY_CONFIG_SUFFIXES) {
			if (name.length() > suffix.length() && name.endsWith(suffix)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Return whether the call name refers to a managed (platform) configuration.
	 */
	static boolean isPlatformSection(@Nullable String name) {
		return StringUtils.hasText(name) && PLATFORM_FUNCTIONS.contains(name);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Replace the version segment in a Gradle GAV string.
	 */
	static @Nullable String updateGavVersion(String gav, String newVersion) {
		if (StringUtils.isEmpty(gav)) {
			return null;
		}

		int firstColon = gav.indexOf(':');
		int secondColon = firstColon == -1 ? -1 : gav.indexOf(':', firstColon + 1);
		if (secondColon == -1) {
			return GradleRichVersion.update(gav, newVersion);
		}

		String versionAndSuffix = gav.substring(secondColon + 1);
		int classifierColon = versionAndSuffix.indexOf(':');
		int extAt = versionAndSuffix.indexOf('@');
		int suffixStart = classifierColon == -1 ? extAt
				: extAt == -1 ? classifierColon
						: Math.min(classifierColon, extAt);

		if (suffixStart != -1) {
			String version = versionAndSuffix.substring(0, suffixStart);
			String suffix = versionAndSuffix.substring(suffixStart);
			return gav.substring(0, secondColon + 1) + GradleRichVersion.update(version, newVersion) + suffix;
		}
		return gav.substring(0, secondColon + 1) + GradleRichVersion.update(versionAndSuffix, newVersion);
	}

	/**
	 * Apply a version update to the given GAV text, if it contains text.
	 */
	static void updateVersion(@Nullable String gav, String newVersion, Consumer<String> updateConsumer) {
		if (StringUtils.isEmpty(gav)) {
			return;
		}
		String updated = updateGavVersion(gav, newVersion);
		if (StringUtils.hasText(updated)) {
			updateConsumer.accept(updated);
		} else {
			updateConsumer.accept(newVersion);
		}
	}

	/**
	 * Walk the ancestor directories of {@code file} until one of them matches a
	 * registered linked Gradle project root.
	 *
	 * @return the linked Gradle project root, or {@literal null} if the file is not
	 * part of a linked Gradle project.
	 */
	static @Nullable VirtualFile findLinkedProjectRoot(Project project, VirtualFile file) {

		Collection<GradleProjectSettings> linkedProjects = GradleSettings.getInstance(project)
				.getLinkedProjectsSettings();
		if (linkedProjects.isEmpty()) {
			return null;
		}

		for (VirtualFile dir = file.isDirectory() ? file : file.getParent(); dir != null; dir = dir.getParent()) {

			for (GradleProjectSettings settings : linkedProjects) {
				if (dir.getPath().equals(settings.getExternalProjectPath())) {
					return dir;
				}
			}
		}

		return null;
	}

	/**
	 * Read repositories from already-imported external project data. This is the
	 * direct consumer side of MavenRepositoriesProjectResolver: that resolver has
	 * already written MavenRepositoryData nodes into the project node.
	 */
	static List<RemoteRepository> getRepositoriesFromImportedProject(Project ideaProject,
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
	static List<RemoteRepository> getRepositories(DataNode<ProjectData> projectNode) {

		Collection<DataNode<MavenRepositoryData>> repoNodes = ExternalSystemApiUtil.getChildren(projectNode,
				MavenRepositoryData.KEY);

		Set<RemoteRepository> repositories = new LinkedHashSet<>();
		for (DataNode<MavenRepositoryData> repoNode : repoNodes) {
			MavenRepositoryData data = repoNode.getData();
			String url = data.getUrl();
			if (!StringUtils.hasText(url) || (!url.startsWith("http://") && !url.startsWith("https://"))) {
				continue;
			}
			if (url.equals(RemoteRepository.mavenCentral().url())) {
				repositories.add(RemoteRepository.mavenCentral());
			} else {
				String urlToUse = url.endsWith("/") ? url : url + "/";
				RemoteRepository repo = new RemoteRepository(data.getName(), urlToUse, null);
				repositories.add(repo);
			}
		}

		return List.copyOf(repositories);
	}

	/**
	 * Return {@literal true} if {@code version} contains range syntax characters.
	 */
	static boolean isVersionRange(@Nullable String version) {
		if (version == null) {
			return false;
		}
		return version.contains("[") || version.contains("]") || version.contains("(") || version.contains(")")
				|| version.contains(",") || version.endsWith(".+");
	}

	/**
	 * Return whether the call name can consume a version-catalog accessor.
	 */
	@Contract("null -> false")
	static boolean isCatalogConsumerCall(@Nullable String name) {

		if (StringUtils.isEmpty(name)) {
			return false;
		}

		if (ALIAS.equals(name)
				|| GradleUtils.isPlugin(name) || GradleUtils.isDependencySection(name)
				|| GradleUtils.isPlatformSection(name)) {
			return true;
		}
		return false;
	}

	@Contract("null -> false")
	static boolean isDirectory(@Nullable VirtualFile directory) {
		return directory != null && directory.isDirectory() && directory.exists();
	}
}
