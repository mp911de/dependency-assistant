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

import java.util.LinkedHashMap;
import java.util.Map;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.PropertyResolver;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Collects dependency coordinates from Gradle build files associated with the
 * project root that contains the given anchor file.
 * <p>The collection process:
 * <ol>
 * <li>Loads {@code gradle.properties} from the project root to build a property
 * map.</li>
 * <li>Parses the anchor file itself using the appropriate parser (Groovy,
 * Kotlin, Properties, or TOML).</li>
 * <li>If the anchor file is not a {@code build.gradle(.kts)}, also scans for
 * {@code gradle/libs.versions.toml} if it exists.</li>
 * </ol>
 *
 * @author Mark Paluch
 */
class GradleDependencyCollector {

	private final Project project;

	private final Map<String, String> properties;

	private final StateService service;

	/**
	 * Create a collector with no predefined Gradle properties.
	 */
	public GradleDependencyCollector(Project project) {
		this(project, Map.of());
	}

	/**
	 * Create a collector using properties already known for the project.
	 */
	public GradleDependencyCollector(Project project, Map<String, String> properties) {
		this.project = project;
		this.service = StateService.getInstance(project);
		this.properties = properties;
	}

	/**
	 * Collects artifact declarations from {@code buildFile} (and any sibling files
	 * such as {@code gradle.properties} or {@code libs.versions.toml} in the same
	 * directory tree).
	 *
	 * @param buildFile the Gradle file.
	 * @return a populated {@link DependencyCollector}.
	 */
	public DependencyCollector collect(PsiFile buildFile) {

		DependencyCollector collector = new DependencyCollector();
		doCollect(buildFile, collector);
		return collector;
	}

	/**
	 * Collect declarations from the given Gradle-related PSI file into
	 * {@code collector}.
	 */
	protected void doCollect(PsiFile psiFile, DependencyCollector collector) {

		VirtualFile file = psiFile.getVirtualFile();
		if (GradleUtils.isVersionCatalog(file)) {
			TomlParser parser = new TomlParser(collector, new LinkedHashMap<>(properties));
			parser.parseVersionCatalog(psiFile);
		} else if (GradleUtils.isGradlePropertiesFile(file)) {
			GradleParser parser = new GradleParser(collector, new LinkedHashMap<>(properties));
			parser.parseGradleProperties(service.getCache(), psiFile);
		} else if (GradleUtils.isKotlinDsl(file) && GradleUtils.KOTLIN_AVAILABLE) {
			PropertyResolver propertyResolver = GradlePropertyResolver.create(psiFile).withFallback(properties::get);
			KotlinDslParser parser = new KotlinDslParser(collector, propertyResolver);
			parser.parseKotlinScript(psiFile);
		} else {
			PropertyResolver propertyResolver = GradlePropertyResolver.create(psiFile).withFallback(properties::get);
			GradleParser parser = new GradleParser(collector, propertyResolver);
			parser.parseGroovyDsl(psiFile);
		}
	}

}
