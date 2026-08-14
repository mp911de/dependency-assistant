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

package biz.paluch.dap.maven;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;

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
	 * @param cache the cache consulted to resolve Bill of Materials members.
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

		MavenParser parser = new MavenParser(propertyResolver);
		Project project = psiFile.getProject();

		if (MavenUtils.isMavenPomFile(psiFile) && psiFile instanceof XmlFile xmlFile) {
			collector.addProperties(MavenPomSupport.parseProperties(xmlFile).keySet());
			parser.parsePomFile(xmlFile).forEach(declaration -> register(project, collector, declaration));
		}

		if (MavenUtils.isMavenExtensionsFile(psiFile) && psiFile instanceof XmlFile xmlFile) {
			parser.parseExtensionsFile(xmlFile).forEach(declaration -> register(project, collector, declaration));
		}
	}

	private void register(Project project, DependencyCollector collector, ArtifactDeclaration declaration) {

		VersionSource versionSource = declaration.getVersionSource();
		if (declaration.isVersioned()
				&& (!(versionSource instanceof VersionSource.VersionProperty)
						|| declaration.isVersionDefinedInSameFile())) {
			collector.registerUsage(declaration.getArtifactId(), declaration.getVersion(),
					declaration.getDeclarationSource(), versionSource);
		}

		if (versionSource.isDefined() || !declaration.getDeclarationSource().isPlugin()) {
			collector.registerDeclaration(declaration.getArtifactId(), declaration.getDeclarationSource(),
					versionSource);
		}

		BomUtil.registerBillOfMaterials(cache, project, declaration, collector);
	}

}
