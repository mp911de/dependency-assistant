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

import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.BetterPsiManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;
import org.toml.lang.psi.TomlFile;
import org.toml.lang.psi.TomlTable;

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

	private final @Nullable VirtualFile projectRoot;

	private final @Nullable BetterPsiManager psiManager;

	VersionCatalogRegistry(Map<String, String> catalogPaths, String defaultAlias) {
		this(catalogPaths, defaultAlias, null, null);
	}

	private VersionCatalogRegistry(Map<String, String> catalogPaths, String defaultAlias, @Nullable Project project,
			@Nullable VirtualFile projectRoot) {
		this.catalogPaths = catalogPaths;
		this.defaultAlias = defaultAlias;
		this.projectRoot = projectRoot;
		this.psiManager = project != null ? BetterPsiManager.getInstance(project) : null;
	}

	/**
	 * Return the registry anchored at the project root of {@code file}, or the
	 * {@link #ABSENT} registry when {@code file} has no backing virtual file. The
	 * result is cached on {@code file} and shared for the same PSI file.
	 *
	 * @param file the Gradle build or settings file to anchor the registry at; must
	 * not be {@literal null}.
	 * @return the version-catalog registry for the file.
	 */
	public static VersionCatalogRegistry from(PsiFile file) {

		return CachedValuesManager.getProjectPsiDependentCache(file, it -> {

			Project project = it.getProject();
			VirtualFile virtualFile = it.getVirtualFile();
			BetterPsiManager psiManager = BetterPsiManager.getInstance(it.getProject());
			if (virtualFile == null) {
				return ABSENT;
			}

			VirtualFile projectRoot = GradleUtils.findProjectRoot(it);
			VirtualFile kotlin = projectRoot.findChild(GradleUtils.KOTLIN_SETTINGS);
			VirtualFile groovy = projectRoot.findChild(GradleUtils.GROOVY_SETTINGS);

			if (kotlin != null && GradleUtils.KOTLIN_AVAILABLE) {
				PsiFile settings = psiManager.findFile(kotlin);
				if (settings != null) {
					return getCachedValue(project, settings).withContext(project, projectRoot);
				}
			}

			if (groovy != null) {
				PsiFile settings = psiManager.findFile(groovy);
				if (settings != null) {
					return getCachedValue(project, settings).withContext(project, projectRoot);
				}
			}

			return defaults().withContext(project, projectRoot);
		});
	}

	private VersionCatalogRegistry withContext(Project project, VirtualFile projectRoot) {
		return new VersionCatalogRegistry(catalogPaths, defaultAlias, project, projectRoot);
	}

	private static VersionCatalogRegistry getCachedValue(Project project, PsiFile settings) {
		return CachedValuesManager.getManager(project).getCachedValue(settings, KEY,
				new FileProvider(project, settings), false);
	}

	/**
	 * Return the default registry containing only the standard {@code libs}
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
	 * Return the TOML file path for the given alias, or {@literal null} if the
	 * alias is not registered.
	 */
	@Nullable
	String pathForAlias(String alias) {
		return catalogPaths.get(alias);
	}

	/**
	 * Return {@literal true} if the given alias is registered in this registry.
	 */
	boolean containsAlias(String alias) {
		return catalogPaths.containsKey(alias);
	}

	/**
	 * Resolve the given TOML reference to the catalog artifact declaration. The
	 * returned reference is anchored at the TOML catalog entry, not at a Gradle DSL
	 * usage site.
	 */
	ArtifactReference resolve(TomlReference reference) {

		PsiFile catalogPsi = findCatalogForReference(reference);
		if (!(catalogPsi instanceof TomlFile tomlFile)) {
			return ArtifactReference.unresolved();
		}

		PropertyResolver propertyResolver = PropertyResolver.fromMap(TomlParser.parseTomlVersions(tomlFile));

		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(tomlFile, TomlTable.class)) {
			if (!reference.getTableName().equals(TomlParser.getTomlTableName(table))) {
				continue;
			}
			ArtifactReference resolved = resolveFromTable(reference, table, propertyResolver);
			if (resolved.isResolved()) {
				return resolved;
			}
		}

		return ArtifactReference.unresolved();
	}

	private ArtifactReference resolveFromTable(TomlReference reference, TomlTable table,
			PropertyResolver propertyResolver) {

		ArtifactReference[] resolved = {ArtifactReference.unresolved()};
		TomlParser.parseEntries(table, propertyResolver, declaration -> {
			if (resolved[0].isResolved() || !declaration.hasKeyMatching(reference)) {
				return;
			}
			resolved[0] = ArtifactReference.from(
					declaration.toArtifactDeclaration(declaration.getElement(),
							declaration.getRequiredVersionLiteral()));
		});

		return resolved[0];
	}

	private @Nullable PsiFile findCatalogForReference(TomlReference reference) {

		if (projectRoot == null || psiManager == null) {
			return null;
		}

		String path = pathForAlias(reference.getCatalogAlias());
		if (path == null) {
			return null;
		}

		return TomlParser.findCatalogFile(psiManager, projectRoot, path);
	}

	/**
	 * Return the alias-to-path mappings for this registry.
	 */
	public Map<String, String> catalogPaths() {
		return catalogPaths;
	}

	/**
	 * Return the default catalog alias.
	 */
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
		VersionCatalogRegistry that = (VersionCatalogRegistry) obj;
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
