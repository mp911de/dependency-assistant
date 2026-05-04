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

import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jspecify.annotations.Nullable;
import org.toml.lang.psi.TomlLiteral;

/**
 * Gradle implementation of {@link VersionUpgradeLookupSupport}.
 *
 * <p>Supports version lookups in Groovy and Kotlin build scripts,
 * {@code gradle.properties}, and {@code libs.versions.toml}. Version catalog
 * accessors are resolved back to the catalog entry that owns the version.
 *
 * @author Mark Paluch
 */
class VersionUpgradeLookupService extends VersionUpgradeLookupSupport {

	private final GradleProjectContext buildContext;

	private final boolean candidate;

	private final @Nullable ProjectState projectState;

	private final PsiFile file;

	private final TomlArtifactResolver tomlResolver;

	private final GradlePropertyResolver propertyResolver;

	private final VersionCatalogRegistry registry;

	private final GroovyLookupSiteLocator groovySiteLocator;

	private final KotlinLookupSiteLocator kotlinSiteLocator;

	private final TomlLookupSiteLocator tomlSiteLocator;

	private final GradleLookupSiteResolver lookupSiteResolver;

	/**
	 * Create a new {@code VersionUpgradeLookupService}.
	 * @param file the Gradle-related file to inspect.
	 */
	public VersionUpgradeLookupService(PsiFile file) {
		this(file.getProject(), file);
	}

	/**
	 * Create a new {@code VersionUpgradeLookupService}.
	 * @param project the IntelliJ project.
	 * @param file the Gradle-related file to inspect.
	 */
	public VersionUpgradeLookupService(Project project, PsiFile file) {
		this(project, file, GradleProjectContext.of(project, file));
	}

	private VersionUpgradeLookupService(Project project, PsiFile file, GradleProjectContext context) {

		super(project, context);

		this.file = file;
		this.buildContext = context;
		this.candidate = GradleUtils.isGradleFile(file);

		StateService service = StateService.getInstance(project);
		this.projectState = buildContext.isAvailable() ? service.getProjectState(buildContext.getProjectId()) : null;
		this.propertyResolver = GradlePropertyResolver.create(file);
		this.registry = VersionCatalogRegistry.from(file);
		this.tomlResolver = new TomlArtifactResolver(project, file, projectState, this.registry);
		this.groovySiteLocator = new GroovyLookupSiteLocator(this.propertyResolver, this.registry);
		this.kotlinSiteLocator = new KotlinLookupSiteLocator(this.propertyResolver, this.registry);
		this.tomlSiteLocator = new TomlLookupSiteLocator();
		this.lookupSiteResolver = new GradleLookupSiteResolver(this.propertyResolver, this.projectState,
				this.tomlResolver);
	}

	/**
	 * Return the cached lookup service for the file containing the given element.
	 * @param element the PSI element that belongs to a Gradle-related file.
	 */
	public static VersionUpgradeLookupService create(PsiElement element) {
		return CachedValuesManager.getProjectPsiDependentCache(element.getContainingFile(),
				VersionUpgradeLookupService::new);
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

		if (!candidate || buildContext.isAbsent()) {
			return ArtifactReference.unresolved();
		}

		VirtualFile vf = this.file.getVirtualFile();
		LookupSite lookupSite = findLookupSite(element, vf);
		return lookupSite.isPresent() ? lookupSiteResolver.resolve(lookupSite) : ArtifactReference.unresolved();
	}

	/**
	 * Find the Gradle lookup site represented by the given element.
	 * @param element the PSI element under inspection.
	 * @param vf the backing virtual file.
	 */
	public LookupSite findLookupSite(PsiElement element, VirtualFile vf) {

		if (GradleUtils.isVersionCatalog(vf) && element instanceof TomlLiteral literal) {
			return tomlSiteLocator.locate(literal);
		}

		if (GradleUtils.isGradlePropertiesFile(vf) && element instanceof PropertyValueImpl propertyValue) {
			return locateGradlePropertySite(propertyValue);
		}

		if (GradleUtils.isGroovyDsl(vf) && element instanceof GroovyPsiElement groovyElement) {
			return groovySiteLocator.locate(groovyElement);
		}

		if (GradleUtils.KOTLIN_AVAILABLE && element instanceof KtElement ktElement) {
			return kotlinSiteLocator.locate(ktElement);
		}

		return LookupSite.absent();
	}

	private LookupSite locateGradlePropertySite(PropertyValueImpl element) {

		Property property = PsiTreeUtil.getParentOfType(element, Property.class);
		if (property == null || StringUtils.isEmpty(property.getKey()) || projectState == null) {
			return LookupSite.absent();
		}

		if (StringUtils.hasText(property.getName()) && StringUtils.hasText(property.getValue())) {
			return LookupSite.ofProperty(property.getName(), property.getValue(), property, element);
		}

		return LookupSite.absent();
	}

}
