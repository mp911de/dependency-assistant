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

import java.util.Map;
import java.util.Objects;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jspecify.annotations.Nullable;

/**
 * Registry mapping version-catalog alias names to their TOML file paths
 * relative to the project root. The default catalog is {@code libs} at
 * {@code gradle/libs.versions.toml}.
 *
 * @author Mark Paluch
 */
class VersionCatalogRegistry {

	private static final Key<CachedValue<VersionCatalogRegistry>> KEY = Key
			.create("biz.paluch.dap.gradle.VersionCatalogRegistry");

	private static final VersionCatalogRegistry ABSENT = defaults();

	private final Map<String, String> catalogPaths;

	private final String defaultAlias;

	VersionCatalogRegistry(Map<String, String> catalogPaths, String defaultAlias) {
		this.catalogPaths = catalogPaths;
		this.defaultAlias = defaultAlias;
	}

	/**
	 * Returns a resolver anchored at {@code file} project root, or an empty
	 * resolver when {@code file} is {@code null}. File-backed instances are cached
	 * on {@code file} (shared for the same PSI file).
	 */
	public static VersionCatalogRegistry from(PsiFile file) {

		Project project = file.getProject();
		VirtualFile virtualFile = file.getVirtualFile();
		PsiManager psiManager = PsiManager.getInstance(file.getProject());
		VirtualFile projectRoot = GradleUtils.findProjectRoot(virtualFile);

		VirtualFile kotlin = projectRoot.findChild(GradleUtils.KOTLIN_SETTINGS);
		VirtualFile groovy = projectRoot.findChild(GradleUtils.GROOVY_SETTINGS);
		if (kotlin != null && GradleUtils.KOTLIN_AVAILABLE) {
			PsiFile settings = psiManager.findFile(kotlin);
			if (settings != null) {
				return getCachedValue(project, settings);
			}
		}

		if (groovy != null) {
			PsiFile settings = psiManager.findFile(groovy);
			if (settings != null) {
				return getCachedValue(project, settings);
			}
		}

		return ABSENT;
	}

	private static VersionCatalogRegistry getCachedValue(Project project, PsiFile settings) {
		return CachedValuesManager.getManager(project).getCachedValue(settings, KEY,
				new FileProvider(project, settings), false);
	}

	/**
	 * Returns the default registry containing only the standard {@code libs}
	 * catalog.
	 */
	public static VersionCatalogRegistry defaults() {
		return new VersionCatalogRegistry(Map.of(TomlParser.LIBS, GradleUtils.DEFAULT_TOML_LOCATION),
				TomlParser.LIBS);
	}

	static class FileProvider implements CachedValueProvider<VersionCatalogRegistry> {

		private final Project project;

		private final VirtualFile virtualFile;

		private final PsiManager psiManager;

		FileProvider(Project project, PsiFile psiFile) {
			this.project = project;
			this.virtualFile = psiFile.getVirtualFile();
			this.psiManager = PsiManager.getInstance(project);
		}

		@Override
		public CachedValueProvider.@Nullable Result<VersionCatalogRegistry> compute() {
			PsiFile psiFile = psiManager.findFile(virtualFile);
			if (psiFile == null) {
				return CachedValueProvider.Result.create(ABSENT, PsiModificationTracker.MODIFICATION_COUNT);
			}

			if (GradleUtils.KOTLIN_AVAILABLE && GradleUtils.KOTLIN_SETTINGS.equals(virtualFile.getName())) {
				return CachedValueProvider.Result.create(KotlinDslSettingsParser.parseRegistry(psiFile),
						PsiModificationTracker.MODIFICATION_COUNT);
			}

			return CachedValueProvider.Result.create(GroovyDslSettingsParser.parseRegistry(psiFile),
					PsiModificationTracker.MODIFICATION_COUNT);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof VersionCatalogRegistry.FileProvider that)) {
				return false;
			}
			return project.equals(that.project) && virtualFile.equals(that.virtualFile);
		}

		@Override
		public int hashCode() {
			return 31 * project.hashCode() + virtualFile.hashCode();
		}

	}

	/**
	 * Returns the TOML file path for the given alias, or {@literal null} if the
	 * alias is not registered.
	 */
	@Nullable
	String pathForAlias(String alias) {
		return catalogPaths.get(alias);
	}

	/**
	 * Returns {@literal true} if the given alias is registered in this registry.
	 */
	boolean containsAlias(String alias) {
		return catalogPaths.containsKey(alias);
	}

	public Map<String, String> catalogPaths() {
		return catalogPaths;
	}

	public String defaultAlias() {
		return defaultAlias;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		var that = (VersionCatalogRegistry) obj;
		return Objects.equals(this.catalogPaths, that.catalogPaths) &&
				Objects.equals(this.defaultAlias, that.defaultAlias);
	}

	@Override
	public int hashCode() {
		return Objects.hash(catalogPaths, defaultAlias);
	}

	@Override
	public String toString() {
		return "VersionCatalogRegistry[" +
				"catalogPaths=" + catalogPaths + ", " +
				"defaultAlias=" + defaultAlias + ']';
	}

}
