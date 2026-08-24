/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;

/**
 * Per-file cache for PSI-based computations.
 *
 * <p>{@link #get(PsiFile, Function)} is invalidated only by edits to the owning
 * file. {@link #withProjectRoot(PsiFile, Function, ModificationTracker...)} is
 * invalidated by any project PSI edit or project-root change.
 *
 * <p>Each provider class identifies one cached computation per file. The first
 * provider instance for that class and file is retained for recomputation.
 * later instances of the same class do not replace it. Providers must therefore
 * derive current state from the supplied file and declared dependencies instead
 * of capturing request-specific or mutable project context. Use distinct
 * provider classes for computations with different values or invalidation
 * policies.
 *
 * @author Mark Paluch
 * @see CachedValuesManager
 */
public abstract class PsiFileCache {

	/**
	 * Return the value cached for {@code file}, computing it when absent or after
	 * the file changes.
	 * @param <F> the PSI file type.
	 * @param <T> the value type.
	 * @param file the file that owns and invalidates the cached value.
	 * @param provider the file-derived computation. Its class identifies the cache
	 * entry.
	 * @return the value shared for the provider and file until that file changes.
	 */
	public static <F extends PsiFile, T> T get(F file,
			Function<? super F, ? extends T> provider) {
		CachedValuesManager manager = CachedValuesManager.getManager(file.getProject());
		Key<CachedValue<T>> key = manager.getKeyForClass(provider.getClass());
		return manager.getCachedValue(file, key, () -> CachedValueProvider.Result
				.createSingleDependency(provider.apply(file), file), false);
	}

	/**
	 * Return the value cached on {@code file}, computing it when absent, after any
	 * project PSI edit, or after a project-root change.
	 * @param <F> the PSI file type.
	 * @param <T> the value type.
	 * @param file the file that owns the cached value.
	 * @param provider the computation based on the current file and project model.
	 * Its class identifies the cache entry.
	 * @param additionalDependencies additional project-model modification sources.
	 * @return the value shared for the provider and file while project PSI and
	 * roots, and the additional dependencies remain unchanged.
	 */
	public static <F extends PsiFile, T> T withProjectRoot(F file,
			Function<? super F, ? extends T> provider, ModificationTracker... additionalDependencies) {

		CachedValuesManager manager = CachedValuesManager.getManager(file.getProject());
		Key<CachedValue<T>> key = manager.getKeyForClass(provider.getClass());
		Collection<Object> dependencies = new ArrayList<>();
		dependencies.add(PsiModificationTracker.MODIFICATION_COUNT);
		dependencies.add(ProjectRootModificationTracker.getInstance(file.getProject()));
		if (additionalDependencies.length > 0) {
			dependencies.addAll(Arrays.asList(additionalDependencies));
		}
		return manager.getCachedValue(file, key,
				() -> CachedValueProvider.Result.create(provider.apply(file), dependencies), false);
	}

}
