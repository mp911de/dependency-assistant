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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
 * Initialize Gradle extension point.
 * 
 * @author Mark Paluch
 */
public class GradleInitService implements ProjectActivity {

	private static final Logger LOG = Logger.getInstance(GradleInitService.class);

	@Override
	public @Nullable Object execute(Project project, Continuation<? super Unit> continuation) {
		try {
			ɑΩ();
		} catch (Exception e) {
			LOG.error("Failed to initialize Gradle extension point", e);
		}

		return null;
	}

	/**
	 * I AM THE ALPHA AND THE OMEGA OF GRADLE ☝️. LET THERE BE ORDER AMONGST
	 * PLUGINS.
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

			CompletionContributorEP maven = cachedExtensions.get(lower);
			CompletionContributorEP me = cachedExtensions.get(higher);
			cachedExtensions.set(lower, me);
			cachedExtensions.set(higher, maven);
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

			Object maven = adapters.get(lower);
			Object me = adapters.get(higher);
			adapters.set(lower, me);
			adapters.set(higher, maven);
			ReflectionUtil.setField(point.getClass(), point, List.class, "adapters", adapters);
		});

		if (cachedExtensionsAsArray == null) {
			MagicDetector arrayMagic = MagicDetector.from(Arrays.asList(point.getExtensions()),
					it -> it.implementationClass);

			if (arrayMagic.requiresMagic()) {
				LOG.debug(
						"Gradle extension point is not initialized yet or something didn't work out as expected. Groovy completions might not show up as expected. Ask Jetbrains that org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion.MavenDependenciesGradleCompletionContributor does not stop contributions from other contributors and that it gets an identifier assigned so that Dependency Assistant can properly add completions.");
			} else {
				LOG.debug("🪄🕴️😉");
			}
		} else {
			MagicDetector arrayMagic = MagicDetector.from(Arrays.asList(cachedExtensionsAsArray),
					it -> it.implementationClass);

			arrayMagic.swapIfNeeded((lower, higher) -> {

				CompletionContributorEP maven = cachedExtensionsAsArray[lower];
				CompletionContributorEP me = cachedExtensionsAsArray[higher];
				cachedExtensionsAsArray[lower] = me;
				cachedExtensionsAsArray[higher] = maven;
				cachedExtensions.set(higher, maven);
			});
		}
	}

	private static class MagicDetector {

		private final int mavenIndex;

		private final int myIndex;

		public MagicDetector(int mavenIndex, int myIndex) {
			this.mavenIndex = mavenIndex;
			this.myIndex = myIndex;
		}

		/**
		 * Create a magic detector from the given collection.
		 * @param collection
		 * @param classNameExtractor
		 * @return
		 * @param <T>
		 */
		public static <T> MagicDetector from(Collection<T> collection, Function<T, String> classNameExtractor) {

			int mavenIndex = -1;
			int myIndex = -1;

			int i = 0;

			for (T element : collection) {

				String className = classNameExtractor.apply(element);

				if (mavenIndex == -1 && className.contains(
						"gradle.integrations.maven.codeInsight.completion.MavenDependenciesGradleCompletionContributor")) {
					mavenIndex = i;
				}

				if (myIndex == -1 && className.contains(GroovyCompletionContributor.class.getName())) {
					myIndex = i;
				}

				i++;
			}

			return new MagicDetector(mavenIndex, myIndex);
		}

		public boolean requiresMagic() {
			return mavenIndex != -1 && myIndex != -1 && mavenIndex < myIndex;
		}

		/**
		 * Swap the elements at the given indices (from, to) if magic is required.
		 */
		public void swapIfNeeded(BiConsumer<Integer, Integer> consumer) {
			if (requiresMagic()) {
				consumer.accept(mavenIndex, myIndex);
			}
		}

	}

}
