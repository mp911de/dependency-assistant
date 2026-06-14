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

import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.BetterPsiManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Gradle-specific {@link PropertyResolver} that resolves properties from Gradle
 * files associated with a given PSI element's containing file.
 *
 * @author Mark Paluch
 */
class GradlePropertyResolver implements PropertyResolver {

	private static final Key<CachedValue<GradlePropertyResolver>> TREE = Key
			.create("biz.paluch.dap.gradle.CACHED_GRADLE_PROPERTY_RESOLVER_TREE");

	private static final Key<CachedValue<GradlePropertyResolver>> FILE = Key
			.create("biz.paluch.dap.gradle.CACHED_GRADLE_PROPERTY_RESOLVER_FILE");

	private static final GradlePropertyResolver ABSENT = new GradlePropertyResolver(Map.of());

	private final Map<String, Property> propertyElements;

	public GradlePropertyResolver(
			Map<String, Property> propertyElements) {
		this.propertyElements = propertyElements;
	}

	/**
	 * Return a resolver anchored at {@code file}, or an empty resolver when
	 * {@code file} is {@literal null}. File-backed instances are cached on
	 * {@code file} (shared for the same PSI file).
	 */
	public static GradlePropertyResolver create(PsiFile file) {

		Project project = file.getProject();
		return CachedValuesManager.getManager(project).getCachedValue(file, TREE,
				new TreeProvider(project, file), false);
	}

	/**
	 * Return a resolver containing only properties from the given file.
	 */
	public static GradlePropertyResolver forFile(PsiFile file) {

		Project project = file.getProject();
		return CachedValuesManager.getManager(project).getCachedValue(file, FILE,
				new FileProvider(project, file), false);
	}

	private static GradlePropertyResolver parseTree(PsiFile file) {

		VirtualFile virtualFile = file.getVirtualFile();
		BetterPsiManager psiManager = BetterPsiManager.getInstance(file.getProject());

		if (!BetterPsiManager.isValid(virtualFile)) {
			return ABSENT;
		}

		VirtualFile root = GradleUtils.findProjectRoot(file);
		VirtualFile scriptDir = virtualFile.getParent();
		if (scriptDir == null) {
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

		Map<String, Property> properties = new LinkedHashMap<>();
		for (VirtualFile directory : dirsLeafToRoot) {
			VirtualFile gradleProps = directory.findChild(GradleUtils.GRADLE_PROPERTIES);
			if (gradleProps != null) {
				PsiFile psiFile = psiManager.findFile(gradleProps);
				if (psiFile != null) {
					properties.putAll(GradlePropertiesParser.parseGradleProperties(psiFile));
				}
			}

			for (VirtualFile child : GradleUtils.findGradleScripts(directory)) {

				PsiFile psiFile = psiManager.findFile(child);
				if (psiFile == null) {
					continue;
				}
				if (GradleUtils.isGroovyDsl(psiFile)) {
					properties.putAll(GroovyDslExtParser.parseLocalVariables(psiFile));
					properties.putAll(GroovyDslExtParser.parseExtProperties(psiFile));
				} else if (GradleUtils.isKotlinDsl(psiFile) && GradleUtils.KOTLIN_AVAILABLE) {
					properties.putAll(KotlinDslExtraParser.parseExtraProperties(psiFile));
					properties.putAll(KotlinDslExtraParser.parseValProperties(psiFile));
				}
			}

		}

		return new GradlePropertyResolver(properties);
	}

	private static GradlePropertyResolver parseFile(PsiFile file) {

		Map<String, Property> properties = new LinkedHashMap<>();

		if (GradleUtils.isGroovyDsl(file)) {
			properties.putAll(GroovyDslExtParser.parseLocalVariables(file));
			properties.putAll(GroovyDslExtParser.parseExtProperties(file));
		} else if (GradleUtils.isKotlinDsl(file) && GradleUtils.KOTLIN_AVAILABLE) {
			properties.putAll(KotlinDslExtraParser.parseExtraProperties(file));
			properties.putAll(KotlinDslExtraParser.parseValProperties(file));
		} else if (GradleUtils.isGradlePropertiesFile(file)) {
			properties = GradlePropertiesParser.parseGradleProperties(file);
		} else if (GradleUtils.isVersionCatalog(file)) {
			properties = TomlParser.parseTomlVersions(file);
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

			if (BetterPsiManager.isInvalid(virtualFile)) {
				return CachedValueProvider.Result.create(ABSENT, PsiModificationTracker.MODIFICATION_COUNT);
			}

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
			if (!(o instanceof FileProvider that)) {
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

		PropertyValue element = getPropertyValue(key);
		if (element != null) {
			return element.getValue();
		}
		return null;
	}

	@Override
	public @Nullable PropertyValue getPropertyValue(String key) {
		Property property = propertyElements.get(key);
		if (property == null) {
			return null;
		}
		PsiElement literal = property.getValueLiteral();
		return literal != null ? new PropertyValue(property.getKey(), property.getValue(), literal) : null;
	}

	/**
	 * Finds a cached property binding whose value PSI matches or encloses
	 * {@code literal}.
	 */
	public @Nullable Property findBindingForValueLiteral(PsiElement literal) {

		for (Property binding : propertyElements.values()) {
			PsiElement psi = binding.getValueLiteral();
			if (psi == null) {
				continue;
			}
			if (psi.equals(literal) || PsiTreeUtil.isAncestor(psi, literal, false)
					|| literal.getManager().areElementsEquivalent(psi, literal)) {
				return binding;
			}

			TextRange lr = literal.getTextRange();
			TextRange pr = psi.getTextRange();
			if (lr != null && pr != null && pr.getStartOffset() <= lr.getStartOffset()
					&& lr.getEndOffset() <= pr.getEndOffset()) {
				return binding;
			}

		}
		return null;
	}

}
