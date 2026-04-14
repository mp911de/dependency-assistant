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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PsiPropertyValueElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jspecify.annotations.Nullable;

/**
 * Gradle-specific {@link PropertyResolver} that resolves properties from Gradle
 * files associated with a given PSI element's containing file.
 *
 * @author Mark Paluch
 */
class GradlePropertyResolver implements PropertyResolver {

	private static final Key<CachedValue<GradlePropertyResolver>> TREE = Key
			.create("biz.paluch.dap.gradle.CACHED_GRADLE_PROPERTY_RESOLVER");

	private static final Key<CachedValue<GradlePropertyResolver>> FILE = Key
			.create("biz.paluch.dap.gradle.CACHED_GRADLE_PROPERTY_RESOLVER");

	private static final GradlePropertyResolver ABSENT = new GradlePropertyResolver(Map.of());

	private final Map<String, PsiPropertyValueElement> propertyElements;

	public GradlePropertyResolver(
			Map<String, PsiPropertyValueElement> propertyElements) {
		this.propertyElements = propertyElements;
	}

	/**
	 * Returns a resolver anchored at {@code file}, or an empty resolver when
	 * {@code file} is {@code null}. File-backed instances are cached on
	 * {@code file} (shared for the same PSI file).
	 */
	public static GradlePropertyResolver create(PsiFile file) {

		Project project = file.getProject();
		return CachedValuesManager.getManager(project).getCachedValue(file, TREE,
				new TreeProvider(project, file), false);
	}

	/**
	 * Returns a resolver containing only properties from the given file.
	 */
	public static GradlePropertyResolver forFile(PsiFile file) {

		Project project = file.getProject();
		return CachedValuesManager.getManager(project).getCachedValue(file, FILE,
				new FileProvider(project, file), false);
	}

	private static GradlePropertyResolver parseTree(PsiFile file) {

		VirtualFile virtualFile = file.getVirtualFile();
		PsiManager psiManager = PsiManager.getInstance(file.getProject());

		if (virtualFile == null) {
			return ABSENT;
		}

		VirtualFile root = GradleUtils.findProjectRoot(virtualFile);
		VirtualFile scriptDir = virtualFile.getParent();
		if (scriptDir == null || root == null) {
			return ABSENT;
		}

		List<VirtualFile> dirsLeafToRoot = new ArrayList<>();
		VirtualFile cursor = scriptDir;
		while (cursor != null) {
			dirsLeafToRoot.add(cursor);
			if (cursor.equals(root)) {
				break;
			}
			VirtualFile parent = cursor.getParent();
			if (parent != null && !VfsUtil.isAncestor(root, parent, false)) {
				break;
			}
			cursor = parent;
		}
		Collections.reverse(dirsLeafToRoot);

		Map<String, PsiPropertyValueElement> properties = new LinkedHashMap<>();
		for (VirtualFile directory : dirsLeafToRoot) {
			VirtualFile gradleProps = directory.findChild(GradleUtils.GRADLE_PROPERTIES);
			if (gradleProps != null) {
				PsiFile psiFile = psiManager.findFile(gradleProps);
				if (psiFile != null) {
					properties.putAll(GradlePropertiesParser.parseGradleProperties(psiFile));
				}
			}

			for (String fileName : GradleUtils.GRADLE_SCRIPT_NAMES) {

				VirtualFile child = directory.findChild(fileName);
				if (child == null) {
					continue;
				}

				PsiFile psiFile = psiManager.findFile(child);
				if (psiFile == null) {
					continue;
				}
				if (GradleUtils.isGroovyDsl(psiFile)) {
					properties.putAll(GroovyDslExtParser.parseExtProperties(file));
				} else if (GradleUtils.isKotlinDsl(psiFile) && GradleUtils.KOTLIN_AVAILABLE) {
					properties.putAll(KotlinDslExtraParser.parseExtraProperties(psiFile));
				}
			}

		}

		return new GradlePropertyResolver(properties);
	}

	private static GradlePropertyResolver parseFile(PsiFile file) {

		Map<String, PsiPropertyValueElement> properties = new LinkedHashMap<>();

		if (GradleUtils.isGroovyDsl(file)) {
			properties.putAll(GroovyDslExtParser.parseExtProperties(file));
		} else if (GradleUtils.isKotlinDsl(file) && GradleUtils.KOTLIN_AVAILABLE) {
			properties.putAll(KotlinDslExtraParser.parseExtraProperties(file));
		} else if (GradleUtils.isGradlePropertiesFile(file)) {
			properties = GradlePropertiesParser.parseGradleProperties(file);
		} else if (GradleUtils.isVersionCatalog(file)) {
			// TODO
		}

		return new GradlePropertyResolver(properties);
	}

	static class TreeProvider implements CachedValueProvider<GradlePropertyResolver> {

		private final Project project;

		private final VirtualFile virtualFile;

		private final PsiManager psiManager;

		TreeProvider(Project project, PsiFile psiFile) {
			this.project = project;
			this.virtualFile = psiFile.getVirtualFile();
			this.psiManager = PsiManager.getInstance(project);
		}

		@Override
		public CachedValueProvider.@Nullable Result<GradlePropertyResolver> compute() {
			PsiFile psiFile = psiManager.findFile(virtualFile);
			if (psiFile == null) {
				return CachedValueProvider.Result.create(ABSENT, PsiModificationTracker.MODIFICATION_COUNT);
			}
			return CachedValueProvider.Result.create(parseTree(psiFile), PsiModificationTracker.MODIFICATION_COUNT);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof TreeProvider that)) {
				return false;
			}
			return project.equals(that.project) && virtualFile.equals(that.virtualFile);
		}

		@Override
		public int hashCode() {
			return 31 * project.hashCode() + virtualFile.hashCode();
		}

	}

	static class FileProvider implements CachedValueProvider<GradlePropertyResolver> {

		private final Project project;

		private final VirtualFile virtualFile;

		private final PsiManager psiManager;

		FileProvider(Project project, PsiFile psiFile) {
			this.project = project;
			this.virtualFile = psiFile.getVirtualFile();
			this.psiManager = PsiManager.getInstance(project);
		}

		@Override
		public CachedValueProvider.@Nullable Result<GradlePropertyResolver> compute() {
			PsiFile psiFile = psiManager.findFile(virtualFile);
			if (psiFile == null) {
				return CachedValueProvider.Result.create(ABSENT, PsiModificationTracker.MODIFICATION_COUNT);
			}
			return CachedValueProvider.Result.create(parseFile(psiFile), PsiModificationTracker.MODIFICATION_COUNT);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof TreeProvider that)) {
				return false;
			}
			return project.equals(that.project) && virtualFile.equals(that.virtualFile);
		}

		@Override
		public int hashCode() {
			return 31 * project.hashCode() + virtualFile.hashCode();
		}

	}

	@Override
	public @Nullable String getProperty(String key) {

		PsiPropertyValueElement element = getElement(key);
		if (element != null) {
			return element.propertyValue();
		}
		return null;
	}

	@Override
	public @Nullable PsiPropertyValueElement getElement(String key) {
		return propertyElements.get(key);
	}

}
