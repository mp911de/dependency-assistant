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

package biz.paluch.dap.github;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionContributorEP;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.impl.XmlExtensionAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.util.ReflectionUtil;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jspecify.annotations.Nullable;

/**
 * Ensures Dependency Assistant's GitHub ref completion contributor runs before
 * the bundled GitHub Actions contributor.
 *
 * <p>The IntelliJ extension-point caches are reordered reflectively because
 * both contributors declare first priority. Initialization failures are logged
 * without aborting project startup. Completion ordering may then remain
 * platform-defined.
 *
 * @author Mark Paluch
 */
public class GitHubInitService implements ProjectActivity {

	private static final Logger LOG = Logger.getInstance(GitHubInitService.class);

	@Override
	public @Nullable Object execute(Project project, Continuation<? super Unit> continuation) {

		try {
			ɑΩ();
		} catch (CancellationException e) {
			throw e;
		} catch (Exception e) {
			LOG.error("Failed to initialize GitHub extension point", e);
		}

		return null;
	}

	/**
	 * Reorder the known completion-contributor representations when required.
	 */
	private void ɑΩ() {

		ExtensionPoint<CompletionContributorEP> point = CompletionContributor.EP.getPoint();
		point.getExtensionList(); // initialize

		Object maybeCachedExtensions = ReflectionUtil.getField(point.getClass(), point, Object.class,
				"cachedExtensions");
		List<CompletionContributorEP> cachedExtensions = new ArrayList<>(
				maybeCachedExtensions == null ? List.of() : (Collection) maybeCachedExtensions);
		CompletionContributorEP[] cachedExtensionsAsArray = ReflectionUtil.getField(point.getClass(), point,
				CompletionContributorEP[].class, "cachedExtensionsAsArray");
		List<Object> adapters = new ArrayList<>(
				(Collection) ReflectionUtil.getField(point.getClass(), point, Object.class, "adapters"));

		MagicDetector extensionListMagic = MagicDetector.from(cachedExtensions, it -> it.implementationClass);

		extensionListMagic.swapIfNeeded((lower, higher) -> {

			CompletionContributorEP actions = cachedExtensions.get(lower);
			CompletionContributorEP me = cachedExtensions.get(higher);
			cachedExtensions.set(lower, me);
			cachedExtensions.set(higher, actions);
			ReflectionUtil.setField(point.getClass(), point, List.class, "cachedExtensions", cachedExtensions);
		});

		MagicDetector adaptersMagic = MagicDetector.from(adapters, it -> {

			if (it instanceof XmlExtensionAdapter xa) {

				Object extensionInstance = ReflectionUtil.getField(xa.getClass(), xa, Object.class,
						"extensionInstance");
				if (extensionInstance instanceof CompletionContributorEP ep) {
					return ep.implementationClass;
				}
			}
			return "";
		});

		adaptersMagic.swapIfNeeded((lower, higher) -> {

			Object actions = adapters.get(lower);
			Object me = adapters.get(higher);
			adapters.set(lower, me);
			adapters.set(higher, actions);
			ReflectionUtil.setField(point.getClass(), point, List.class, "adapters", adapters);
		});

		if (cachedExtensionsAsArray == null) {
			MagicDetector arrayMagic = MagicDetector.from(Arrays.asList(point.getExtensions()),
					it -> it.implementationClass);

			if (arrayMagic.requiresMagic()) {
				LOG.debug(
						"GitHub extension point is not initialized yet or something didn't work out as expected. GitHub version completions might not show up as expected");
			} else {
				LOG.debug("🪄🕴️😉");
			}
		} else {
			MagicDetector arrayMagic = MagicDetector.from(Arrays.asList(cachedExtensionsAsArray),
					it -> it.implementationClass);

			arrayMagic.swapIfNeeded((lower, higher) -> {

				CompletionContributorEP actions = cachedExtensionsAsArray[lower];
				CompletionContributorEP me = cachedExtensionsAsArray[higher];
				cachedExtensionsAsArray[lower] = me;
				cachedExtensionsAsArray[higher] = actions;
				cachedExtensions.set(higher, actions);
			});
		}
	}

	private static class MagicDetector {

		private final int actionsIndex;

		private final int myIndex;

		public MagicDetector(int actionsIndex, int myIndex) {
			this.actionsIndex = actionsIndex;
			this.myIndex = myIndex;
		}

		/**
		 * Locate the bundled and Dependency Assistant contributors in a collection.
		 *
		 * @param <T> the collection element type.
		 * @param collection the extension representation to inspect.
		 * @param classNameExtractor function yielding each implementation class name.
		 * @return the detected contributor positions.
		 */
		public static <T> MagicDetector from(Collection<T> collection, Function<T, String> classNameExtractor) {

			int actionsIndex = -1;
			int myIndex = -1;

			int i = 0;

			for (T element : collection) {

				String className = classNameExtractor.apply(element);

				if (actionsIndex == -1 && className.contains(
						"GitHubActionCompletionContributor")) {
					actionsIndex = i;
				}

				if (myIndex == -1 && className.contains(GitHubWorkflowCompletionContributor.class.getName())) {
					myIndex = i;
				}

				i++;
			}

			return new MagicDetector(actionsIndex, myIndex);
		}

		public boolean requiresMagic() {
			return actionsIndex != -1 && myIndex != -1 && actionsIndex < myIndex;
		}

		/**
		 * Swap the detected contributors when the bundled contributor comes first.
		 *
		 * @param consumer operation that swaps the two detected positions.
		 */
		public void swapIfNeeded(BiConsumer<Integer, Integer> consumer) {
			if (requiresMagic()) {
				consumer.accept(actionsIndex, myIndex);
			}
		}

	}

}
