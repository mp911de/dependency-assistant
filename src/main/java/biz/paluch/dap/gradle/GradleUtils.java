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
import java.util.function.Consumer;

import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.util.StringUtils;
import com.intellij.externalSystem.MavenRepositoryData;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
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
	static final Set<String> DEPENDENCY_CONFIGS = Set.of("implementation", "api", "runtimeOnly", "compileOnly",
			"testImplementation", "testRuntimeOnly", "testCompileOnly", "androidTestImplementation",
			"debugImplementation", "releaseImplementation", "annotationProcessor", "kapt", "ksp", "provided", "compile",
			"runtime", "testCompile", "testRuntime", "classpath");

	/** Configuration names that represent a BOM / platform import (managed). */
	static final Set<String> PLATFORM_FUNCTIONS = Set.of("platform", "enforcedPlatform", "mavenBom");

	/**
	 * Name of the plugins section.
	 */
	static final String PLUGINS = "plugins";

	/**
	 * Name of the dependencies section.
	 */
	static final String DEPENDENCIES = "dependencies";

	/**
	 * Name of the {@code id} plugin/catalog key.
	 */
	static final String ID = "id";

	/**
	 * Name of the {@code alias} catalog consumer call.
	 */
	static final String ALIAS = "alias";

	/**
	 * Name of the {@code group} dependency key.
	 */
	static final String GROUP = "group";

	/**
	 * Name of the {@code name} dependency key.
	 */
	static final String NAME = "name";

	/**
	 * Name of the {@code module} catalog key.
	 */
	static final String MODULE = "module";

	/**
	 * Name of the {@code version} dependency key/call.
	 */
	static final String VERSION = "version";

	/**
	 * Name of the {@code version.ref} catalog key.
	 */
	static final String VERSION_REF = "version.ref";

	/**
	 * Name of the catalog {@code libs} accessor root.
	 */
	static final String LIBS = "libs";

	/**
	 * Name of the catalog {@code libraries} table.
	 */
	static final String LIBRARIES = "libraries";

	/**
	 * Name of the catalog {@code versions} table.
	 */
	static final String VERSIONS = "versions";

	/**
	 * Name of the catalog {@code bundles} table.
	 */
	static final String BUNDLES = "bundles";

	/**
	 * Name of the Groovy {@code ext} property block/qualifier.
	 */
	static final String EXT = "ext";

	/**
	 * Name of the Groovy {@code set} property method.
	 */
	static final String SET = "set";

	/**
	 * Name of Gradle's {@code prefer(...)} version declaration.
	 */
	static final String PREFER = "prefer";

	/**
	 * Name of Gradle's {@code strictly(...)} version declaration.
	 */
	static final String STRICTLY = "strictly";

	/** Name of the Gradle Settings file. */
	public static final String GROOVY_SETTINGS = "settings.gradle";

	/** Name of the Gradle Settings.kts file. */
	public static final String KOTLIN_SETTINGS = "settings.gradle.kts";

	/** Name of the Gradle properties file. */
	public static final String GRADLE_PROPERTIES = "gradle.properties";

	/** Name of the Gradle version catalog file. */
	public static final String LIBS_VERSIONS_TOML = "libs.versions.toml";

	/** Default relative path of the Gradle version catalog file. */
	public static final String DEFAULT_TOML_LOCATION = "gradle/" + GradleUtils.LIBS_VERSIONS_TOML;

	/** Names of Gradle build/settings script files. */
	public static final Set<String> GRADLE_SCRIPT_NAMES = Set.of("build.gradle", "build.gradle.kts", GROOVY_SETTINGS,
			KOTLIN_SETTINGS);

	private GradleUtils() {
	}

	/**
	 * Return whether the given {@link PsiFile} is a Gradle-related file.
	 */
	public static boolean isGradleFile(@Nullable PsiFile file) {
		if (file == null) {
			return false;
		}
		return isGradleScript(file) || isGradlePropertiesFile(file) || isVersionCatalog(file);
	}

	/**
	 * Return whether the given {@link VirtualFile} is a Gradle-related file.
	 */
	public static boolean isGradleFile(@Nullable VirtualFile file) {
		if (file == null) {
			return false;
		}
		return isGradleScript(file) || isGradlePropertiesFile(file) || isVersionCatalog(file);
	}

	/**
	 * Return {@literal true} if the file is a Gradle build or settings script
	 * ({@code build.gradle}, {@code build.gradle.kts}, {@code settings.gradle},
	 * {@code settings.gradle.kts}).
	 */
	public static boolean isGradleScript(@Nullable VirtualFile file) {
		return file != null && GRADLE_SCRIPT_NAMES.contains(file.getName());
	}

	/**
	 * Return {@literal true} if the file is a Gradle build or settings script
	 * ({@code build.gradle}, {@code build.gradle.kts}, {@code settings.gradle},
	 * {@code settings.gradle.kts}).
	 */
	public static boolean isGradleScript(@Nullable PsiFile file) {
		return file != null && GRADLE_SCRIPT_NAMES.contains(file.getName());
	}

	/**
	 * Return {@literal true} if the file is a {@code gradle.properties} file.
	 */
	public static boolean isGradlePropertiesFile(@Nullable VirtualFile file) {
		return file != null && GRADLE_PROPERTIES.equals(file.getName());
	}

	/**
	 * Return {@literal true} if the file is a {@code gradle.properties} file.
	 */
	public static boolean isGradlePropertiesFile(@Nullable PsiFile file) {
		return file != null && GRADLE_PROPERTIES.equals(file.getName());
	}

	/**
	 * Return {@literal true} if the file is a {@code *.versions.toml} version
	 * catalog.
	 */
	public static boolean isVersionCatalog(@Nullable VirtualFile file) {
		return file != null && file.getName().endsWith(".versions.toml");
	}

	/**
	 * Return {@literal true} if the file is a {@code *.versions.toml} version
	 * catalog.
	 */
	public static boolean isVersionCatalog(@Nullable PsiFile file) {
		return file != null && file.getName().endsWith(".versions.toml");
	}

	/**
	 * Return {@literal true} if the file uses the Kotlin DSL ({@code .kts}
	 * extension).
	 */
	public static boolean isKotlinDsl(@Nullable VirtualFile file) {
		return file != null && file.getName().endsWith(".kts");
	}

	/**
	 * Return {@literal true} if the file uses the Kotlin DSL ({@code .kts}
	 * extension).
	 */
	public static boolean isKotlinDsl(@Nullable PsiFile file) {
		return file != null && file.getName().endsWith(".kts");
	}

	/**
	 * Return {@literal true} if the file uses the Groovy DSL ({@code .gradle}
	 * without a {@code .kts} suffix).
	 */
	public static boolean isGroovyDsl(@Nullable PsiFile file) {
		return isGradleScript(file) && !isKotlinDsl(file);
	}

	/**
	 * Return {@literal true} if the file uses the Groovy DSL ({@code .gradle}
	 * without a {@code .kts} suffix).
	 */
	public static boolean isGroovyDsl(@Nullable VirtualFile file) {
		return isGradleScript(file) && !isKotlinDsl(file);
	}

	/**
	 * Locates the Gradle project root for the given file by walking up to the first
	 * ancestor that contains a settings file or returns the parent directory if
	 * none is found.
	 * <p>
	 * When a linked Gradle project root can be resolved for the file, any
	 * {@code settings.gradle} found above that boundary is ignored so a stray
	 * settings file cannot widen resolution scope.
	 */
	static VirtualFile findProjectRoot(PsiFile file) {
		return findProjectRoot(file.getProject(), file.getVirtualFile());
	}

	/**
	 * Locates the Gradle project root for the given file by walking up to the first
	 * ancestor that contains a settings file or returns the parent directory if
	 * none is found.
	 * <p>
	 * When a linked Gradle project root can be resolved for the file, any
	 * {@code settings.gradle} found above that boundary is ignored so a stray
	 * settings file cannot widen resolution scope.
	 */
	// TODO: Refine root detection
	static VirtualFile findProjectRoot(Project project, VirtualFile file) {

		String name = "ROOT: " + file.getCanonicalPath();
		Key<VirtualFile> ROOT = (Key) Key.findKeyByName(name);
		if (ROOT == null) {
			ROOT = Key.create(name);
		}

		VirtualFile root = project.getUserData(ROOT);
		if (root == null) {
			root = doFindProjectRoot(project, file);
			project.putUserData(ROOT, root);
		} else {
			return root;
		}

		return root;
	}

	private static VirtualFile doFindProjectRoot(Project project, VirtualFile file) {

		VirtualFile ceiling = resolveGradleProjectCeiling(project, file);
		VirtualFile dir = file.isDirectory() ? file : file.getParent();

		while (dir != null) {
			boolean hasSettings = dir.findChild("settings.gradle") != null
					|| dir.findChild("settings.gradle.kts") != null;
			if (hasSettings && (VfsUtil.isAncestor(ceiling, dir, false) || dir.equals(ceiling))) {
				return dir;
			}
			VirtualFile parent = dir.getParent();
			if (parent != null && !VfsUtil.isAncestor(ceiling, parent, false)) {
				break;
			}
			dir = parent;
		}

		VirtualFile fallback = file.getParent();
		if (fallback != null && !VfsUtil.isAncestor(ceiling, fallback, false)) {
			return ceiling;
		}

		if (fallback == null) {
			throw new IllegalStateException("Could not find linked project root for " + project);
		}
		return fallback;
	}

	private static VirtualFile resolveGradleProjectCeiling(Project project, VirtualFile file) {

		String linked = findLinkedProjectPath(project, file);
		if (StringUtils.isEmpty(linked)) {
			return file.getParent();
		}
		VirtualFile linkedRoot = LocalFileSystem.getInstance().findFileByPath(linked);
		if (linkedRoot != null) {
			return linkedRoot;
		}

		return file.getParent();
	}

	/**
	 * Return whether the call name refers to a plugin configuration.
	 */
	public static boolean isPlugin(@Nullable String name) {
		return ID.equals(name);
	}

	/**
	 * Return whether the call name refers to a plugin section configuration.
	 */
	public static boolean isPluginSection(@Nullable String name) {
		return StringUtils.hasText(name) && PLUGINS.equals(name);
	}

	/**
	 * Return whether the call name refers to a dependency configuration.
	 */
	public static boolean isDependencySection(@Nullable String name) {
		return StringUtils.hasText(name) && DEPENDENCY_CONFIGS.contains(name);
	}

	/**
	 * Return whether the call name refers to a managed (platform) configuration.
	 */
	public static boolean isPlatformSection(@Nullable String name) {
		return StringUtils.hasText(name) && PLATFORM_FUNCTIONS.contains(name);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Replace the version segment in a Gradle GAV string.
	 */
	public static @Nullable String updateGavVersion(String gav, String newVersion) {
		if (StringUtils.isEmpty(gav)) {
			return null;
		}

		int firstColon = gav.indexOf(':');
		int secondColon = firstColon == -1 ? -1 : gav.indexOf(':', firstColon + 1);
		if (secondColon == -1) {
			return GradleRichVersion.update(gav, newVersion);
		}

		String version = gav.substring(secondColon + 1);
		return gav.substring(0, secondColon + 1) + GradleRichVersion.update(version, newVersion);
	}

	/**
	 * Apply a version update to the given GAV text, if it contains text.
	 */
	public static void updateVersion(@Nullable String gav, String newVersion, Consumer<String> updateConsumer) {
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
	public static boolean isVersionRange(@Nullable String version) {
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
	public static boolean isCatalogConsumerCall(@Nullable String name) {

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

}
