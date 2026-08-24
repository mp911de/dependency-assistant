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

package biz.paluch.dap.gradle;

import java.util.Map;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.artifact.VersionSource.DeclaredVersion;
import biz.paluch.dap.maven.BomUtil;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
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

	private final Project project;

	private final StateService service;

	/**
	 * Create a collector with no predefined Gradle properties.
	 * @param project the IntelliJ project owning the Gradle file.
	 */
	public GradleDependencyCollector(Project project) {
		this(project, Map.of());
	}

	/**
	 * Create a collector using project properties as a fallback after properties
	 * discovered from the file tree.
	 * @param project the IntelliJ project owning the Gradle file.
	 * @param properties the fallback project properties.
	 */
	public GradleDependencyCollector(Project project, Map<String, String> properties) {
		this.properties = properties;
		this.project = project;
		this.service = StateService.getInstance(project);
	}

	/**
	 * Collect artifact declarations from {@code buildFile} into the provided
	 * {@code collector}.
	 * <p>Script anchors resolve project-root Gradle properties and version-catalog
	 * accessors without treating unused catalog entries as dependency usages.
	 *
	 * @param buildFile the Gradle-related file to parse.
	 * @param collector the collector to populate in place.
	 */
	public void collect(PsiFile buildFile, DependencyCollector collector) {
		doCollect(buildFile, collector);
	}

	/**
	 * Collect declarations from the given Gradle-related PSI file into
	 * {@code collector}.
	 * @param psiFile the Gradle-related file to parse.
	 * @param collector the collector to populate in place.
	 */
	protected void doCollect(PsiFile psiFile, DependencyCollector collector) {

		VirtualFile file = psiFile.getVirtualFile();
		if (GradleUtils.isVersionCatalog(file)) {
			TomlParser.parseVersionCatalog(psiFile).forEach(declaration -> registerCatalog(collector, declaration));
		} else if (GradleUtils.isGradlePropertiesFile(file)) {
			GradlePropertiesParser.collectGradleProperties(psiFile, collector);
		} else if (GradleUtils.isKotlinDsl(file) && GradleUtils.KOTLIN_AVAILABLE) {
			PropertyResolver propertyResolver = GradlePropertyResolver.create(psiFile).withFallback(properties::get);
			KotlinDslFileParser parser = new KotlinDslFileParser(psiFile, propertyResolver);
			collector.addProperties(parser.getExtraPropertyNames());
			parser.parseDeclarations().forEach(declaration -> register(collector, declaration));
		} else {
			PropertyResolver propertyResolver = GradlePropertyResolver.create(psiFile).withFallback(properties::get);
			GroovyDslFileParser parser = new GroovyDslFileParser(psiFile, propertyResolver);
			collector.addProperties(parser.getDeclaredPropertyNames());
			parser.parseDeclarations().forEach(declaration -> register(collector, declaration));
		}
	}

	/**
	 * Register an artifact declaration with the dependency collector.
	 *
	 * <p>A concrete, non-prefix, non-catalog version is registered as a usage. The
	 * declaration and any BOM metadata are registered for every version shape.
	 * @param collector the collector to populate.
	 * @param declaration the artifact declaration to register.
	 */
	void register(DependencyCollector collector, ArtifactDeclaration declaration) {

		VersionSource versionSource = declaration.getVersionSource();
		boolean concreteDeclaration = !(versionSource instanceof DeclaredVersion declared)
				|| GradleRichVersion.parse(declared.getVersion()).isPresent();
		DeclarationSource declarationSource = declaration.getDeclarationSource();

		if (declaration.isVersioned() && concreteDeclaration && !versionSource.isPrefix()
				&& !(versionSource instanceof VersionSource.VersionCatalog)) {
			collector.registerUsage(declaration.getArtifactId(), declaration.getVersion(),
					declarationSource, versionSource);
		}

		collector.registerDeclaration(declaration.getArtifactId(), declarationSource,
				versionSource);

		BomUtil.registerBillOfMaterials(service.getCache(), project, declaration, collector);
	}

	/**
	 * Register a version-catalog declaration as a declaration and, when versioned,
	 * as a usage.
	 * @param collector the collector to populate.
	 * @param declaration the catalog declaration to register.
	 */
	void registerCatalog(DependencyCollector collector, ArtifactDeclaration declaration) {

		if (declaration.isVersioned()) {
			collector.registerUsage(declaration.getArtifactId(), declaration.getVersion(),
					declaration.getDeclarationSource(), declaration.getVersionSource());
		}
		collector.registerDeclaration(declaration.getArtifactId(), declaration.getDeclarationSource(),
				declaration.getVersionSource());
	}

}
