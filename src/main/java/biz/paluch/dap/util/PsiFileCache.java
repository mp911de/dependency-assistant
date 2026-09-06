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
 * <p>The provider class identifies the computation within each file. The first
 * provider is retained for recomputation. Later instances of the same class do
 * not replace it.
 * <p>Providers must read current state from the file and declared dependencies
 * instead of capturing request-specific state. Use distinct provider classes
 * for different computations or invalidation policies.
 *
 * @author Mark Paluch
 * @see CachedValuesManager
 */
public abstract class PsiFileCache {

	/**
	 * Cache a computation that depends only on the owning file.
	 * <p>Changes to other files do not invalidate the value.
	 */
	public static <F extends PsiFile, T> T get(F file,
			Function<? super F, ? extends T> provider) {
		CachedValuesManager manager = CachedValuesManager.getManager(file.getProject());
		Key<CachedValue<T>> key = manager.getKeyForClass(provider.getClass());
		return manager.getCachedValue(file, key, () -> CachedValueProvider.Result
				.createSingleDependency(provider.apply(file), file), false);
	}

	/**
	 * Cache a computation that depends on project PSI and roots.
	 * <p>Any project PSI edit, root change, or additional dependency change
	 * invalidates the value.
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
