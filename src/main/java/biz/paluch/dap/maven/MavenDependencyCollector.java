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

package biz.paluch.dap.maven;

import java.util.Map;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

/**
 * Collects dependency coordinates from Maven POM and extensions build files.
 *
 * @author Mark Paluch
 */
class MavenDependencyCollector {

	private final Cache cache;

	/**
	 * Create a collector using the given {@link Project}.
	 *
	 * @param project the associated project.
	 */
	public MavenDependencyCollector(Project project) {
		this(StateService.getInstance(project).getCache());
	}

	/**
	 * Create a collector using the given cache.
	 *
	 * @param cache the cache used while parsing POM files.
	 */
	public MavenDependencyCollector(Cache cache) {
		this.cache = cache;
	}

	/**
	 * Collects artifact declarations from {@code buildFile}.
	 */
	public DependencyCollector collect(PackageSystem packageSystem, PsiFile buildFile,
			MavenPomProperties propertyResolver) {

		DependencyCollector collector = new DependencyCollector(packageSystem);
		doCollect(buildFile, propertyResolver, collector);
		return collector;
	}

	/**
	 * Collect declarations from the given Maven PSI file into {@code collector}.
	 */
	protected void doCollect(PsiFile psiFile, MavenPomProperties propertyResolver, DependencyCollector collector) {

		MavenParser parser = new MavenParser(cache, propertyResolver);

		if (MavenUtils.isMavenPomFile(psiFile) && psiFile instanceof XmlFile xmlFile) {
			Map<String, PropertyValue> properties = MavenPomSupport.parseProperties(xmlFile);
			collector.addProperties(properties.keySet());
			parser.parsePomFile(xmlFile).forEach(declaration -> register(collector, declaration));
			registerCachedPropertyArtifacts(properties, propertyResolver, collector);
		}

		if (MavenUtils.isMavenExtensionsFile(psiFile) && psiFile instanceof XmlFile xmlFile) {
			parser.parseExtensionsFile(xmlFile).forEach(declaration -> register(collector, declaration));
		}
	}

	private void register(DependencyCollector collector, ArtifactDeclaration declaration) {

		VersionSource versionSource = declaration.getVersionSource();
		if (declaration.isVersionDefined()
				&& (!(versionSource instanceof VersionSource.VersionProperty)
						|| declaration.isVersionDefinedInSameFile())) {
			collector.registerUsage(declaration.getArtifactId(), declaration.getVersion(),
					declaration.getDeclarationSource(), versionSource);
		}

		if (versionSource.isDefined() || !declaration.getDeclarationSource().isPlugin()) {
			collector.registerDeclaration(declaration.getArtifactId(), declaration.getDeclarationSource(),
					versionSource);
		}
	}

	private void registerCachedPropertyArtifacts(Map<String, PropertyValue> declaredProperties,
			MavenPomProperties propertyResolver, DependencyCollector collector) {

		cache.doWithProperties(property -> {
			if (!property.hasArtifacts()) {
				return;
			}

			for (Property declaration : declaredProperties.values()) {
				if (!property.name().equals(declaration.getKey())
						|| !(declaration.getValueLiteral() instanceof XmlTag declarationTag)) {
					continue;
				}

				String value = propertyResolver.forDeclaration(declarationTag).getProperty(property.name());
				if (StringUtils.isEmpty(value)) {
					continue;
				}

				String profileId = MavenPomProperties.profileId(declaration);
				VersionSource source = profileId != null
						? VersionSource.profileProperty(profileId, property.name())
						: VersionSource.property(property.name());
				biz.paluch.dap.artifact.ArtifactVersion.from(value).ifPresent(version -> {
					for (CachedArtifact artifact : property.artifacts()) {
						collector.registerUsage(artifact.toArtifactId(), version, DeclarationSource.managed(), source);
					}
				});
			}
		});
	}

}
