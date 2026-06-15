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
 * Collects dependency coordinates from a Gradle file using the appropriate
 * parser for Groovy DSL, Kotlin DSL, {@code gradle.properties}, or
 * {@code *.versions.toml} version catalogs.
 * <p>When the anchor file is a Gradle build or settings script, the collector
 * resolves visible Gradle properties and version-catalog accessors through the
 * project root so only actual script usages are collected.
 *
 * @author Mark Paluch
 */
class GradleDependencyCollector {

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
		this.service = StateService.getInstance(project);
		this.properties = properties;
	}

	/**
	 * Collect artifact declarations from {@code buildFile}.
	 * <p>When {@code buildFile} is a Gradle build or settings script, the parser
	 * resolves visible Gradle properties and version-catalog accessors through the
	 * project root.
	 *
	 * @param buildFile the Gradle file, must not be {@literal null}.
	 * @return a populated {@link DependencyCollector}, guaranteed to be not
	 * {@literal null}.
	 */
	public DependencyCollector collect(PsiFile buildFile) {

		DependencyCollector collector = new DependencyCollector();
		collect(buildFile, collector);
		return collector;
	}

	/**
	 * Collect artifact declarations from {@code buildFile} into the provided
	 * {@code collector}.
	 * <p>Script anchors resolve project-root Gradle properties and version-catalog
	 * accessors without treating unused catalog entries as dependency usages.
	 *
	 * @param buildFile the Gradle file, must not be {@literal null}.
	 * @param collector the collector to populate in place, must not be
	 * {@literal null}.
	 */
	public void collect(PsiFile buildFile, DependencyCollector collector) {
		doCollect(buildFile, collector);
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
			VersionCatalogRegistry registry = VersionCatalogRegistry.from(psiFile);
			PropertyResolver propertyResolver = GradlePropertyResolver.create(psiFile).withFallback(properties::get);
			KotlinDslParser parser = new KotlinDslParser(collector, propertyResolver, registry);
			parser.parseKotlinScript(psiFile);
		} else {
			VersionCatalogRegistry registry = VersionCatalogRegistry.from(psiFile);
			PropertyResolver propertyResolver = GradlePropertyResolver.create(psiFile).withFallback(properties::get);
			GradleParser parser = new GradleParser(collector, propertyResolver, registry);
			parser.parseGroovyDsl(psiFile);
		}
	}

}
