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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
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
	 */
	public GradleDependencyCollector(Project project) {
		this(project, Map.of());
	}

	/**
	 * Create a collector using properties already known for the project.
	 */
	public GradleDependencyCollector(Project project, Map<String, String> properties) {
		this.properties = properties;
		this.project = project;
		this.service = StateService.getInstance(project);
	}

	/**
	 * Collect artifact declarations from {@code buildFile}.
	 * <p>When {@code buildFile} is a Gradle build or settings script, the parser
	 * resolves visible Gradle properties and version-catalog accessors through the
	 * project root.
	 *
	 * @param buildFile the Gradle file.
	 * @return a populated {@link DependencyCollector}, guaranteed to be not .
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
	 * @param buildFile the Gradle file.
	 * @param collector the collector to populate in place, must not be .
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
			TomlParser.parseVersionCatalog(psiFile).forEach(declaration -> registerCatalog(collector, declaration));
		} else if (GradleUtils.isGradlePropertiesFile(file)) {
			GradlePropertiesParser.collectGradleProperties(service.getCache(), psiFile, collector);
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
	 * Register the given artifact declaration with the dependency collector.
	 */
	void register(DependencyCollector collector, ArtifactDeclaration declaration) {

		VersionSource versionSource = declaration.getVersionSource();
		boolean concreteDeclaration = !(versionSource instanceof DeclaredVersion declared)
				|| GradleRichVersion.parse(declared.getVersion()).isPresent();
		DeclarationSource declarationSource = getDeclarationSource(declaration);

		if (declaration.isVersionDefined() && concreteDeclaration && !versionSource.isPrefix()
				&& !(versionSource instanceof VersionSource.VersionCatalog)) {
			collector.registerUsage(declaration.getArtifactId(), declaration.getVersion(),
					declarationSource, versionSource);
		}

		collector.registerDeclaration(declaration.getArtifactId(), declarationSource,
				versionSource);
	}

	private DeclarationSource getDeclarationSource(ArtifactDeclaration declaration) {

		DeclarationSource declarationSource = declaration.getDeclarationSource();

		if (declarationSource instanceof DeclarationSource.Bom && declaration.isVersionDefined()) {

			Map<ArtifactId, ArtifactVersion> bom = BomUtil.resolveBom(service.getCache(), project,
					PackageIdentity.of(declaration.getArtifactId(), PackageSystem.MAVEN), declaration.getVersion());
			return DeclarationSource.bom(bom);
		}

		return declarationSource;
	}

	void registerCatalog(DependencyCollector collector, ArtifactDeclaration declaration) {

		if (declaration.isVersionDefined()) {
			collector.registerUsage(declaration.getArtifactId(), declaration.getVersion(),
					declaration.getDeclarationSource(), declaration.getVersionSource());
		}
		collector.registerDeclaration(declaration.getArtifactId(), declaration.getDeclarationSource(),
				declaration.getVersionSource());
	}

}
