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

import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.UpgradeSuggestion;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jspecify.annotations.Nullable;
import org.toml.lang.psi.TomlLiteral;

/**
 * Gradle implementation of {@link VersionUpgradeLookupSupport}. Determines
 * whether the PSI element at the caret represents a version value in a Gradle
 * dependency declaration and returns an {@link UpgradeSuggestion} if a newer
 * version is available.
 * <p>Supported locations:
 * <ul>
 * <li>Version part of a Groovy string-notation GAV literal
 * ({@code 'group:artifact:version'})</li>
 * <li>Version part of a Kotlin DSL string template GAV literal
 * ({@code "group:artifact:version"})</li>
 * <li>Kotlin {@code extra["key"]} version value: plain string, triple-quoted
 * string, {@code buildString { append("…") }}, or the receiver literal in
 * {@code "….also { extra["key"] = it }}</li>
 * <li>Property value in {@code gradle.properties} that maps to a known artifact
 * version</li>
 * <li>Version literal in a {@code libs.versions.toml} {@code [versions]}
 * table</li>
 * <li>Version-catalog accessors in Groovy or Kotlin:
 * {@code alias(libs.plugins.…)}, {@code id(libs.plugins.…)} (inside
 * {@code plugins { }}), and dependency configurations such as
 * {@code implementation(libs.…)} / {@code platform(libs.…)}, resolved via
 * {@code gradle/libs.versions.toml}</li>
 * </ul>
 *
 * @author Mark Paluch
 */
class VersionUpgradeLookupService extends VersionUpgradeLookupSupport {

	private final GradleProjectContext buildContext;

	private final boolean candidate;

	private final @Nullable ProjectState projectState;

	private final Cache cache;

	private final PsiFile file;

	private final TomlArtifactResolver tomlResolver;

	private final GradlePropertyResolver propertyResolver;

	private final VersionCatalogRegistry registry;

	private final GroovyLookupSiteLocator groovySiteLocator;

	private final KotlinLookupSiteLocator kotlinSiteLocator;

	private final TomlLookupSiteLocator tomlSiteLocator;

	private final GradleLookupSiteResolver lookupSiteResolver;

	public VersionUpgradeLookupService(Project project, PsiFile file) {
		this(project, file, GradleProjectContext.of(project, file));
	}

	private VersionUpgradeLookupService(Project project, PsiFile file, GradleProjectContext ctx) {

		super(project, ctx);

		this.file = file;
		this.buildContext = ctx;
		this.candidate = GradleUtils.isGradleFile(file);

		DependencyAssistantService service = DependencyAssistantService.getInstance(project);
		this.cache = service.getCache();
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

	@Override
	public UpgradeSuggestion suggestUpgrades(PsiElement element) {
		return suggestUpgrades(this.cache, resolveArtifactReference(element));
	}

	public @Nullable Dependency findDependency(PsiElement element) {
		ArtifactReference result = resolveArtifactReference(element);
		if (!result.isResolved() || projectState == null) {
			return null;
		}
		return projectState.findDependency(result.getArtifactId());
	}

	protected ArtifactReference resolveArtifactReference(PsiElement element) {

		if (!candidate || !buildContext.isAvailable()) {
			return ArtifactReference.unresolved();
		}

		VirtualFile vf = this.file.getVirtualFile();

		if (GradleUtils.isVersionCatalog(vf) && element instanceof TomlLiteral literal) {
			return lookupSiteResolver.resolve(tomlSiteLocator.locate(literal));
		}

		if (GradleUtils.isGradlePropertiesFile(vf) && element instanceof PropertyValueImpl propertyValue) {
			return lookupSiteResolver.resolve(locateGradlePropertySite(propertyValue));
		}

		if (GradleUtils.isGroovyDsl(vf) && element instanceof GroovyPsiElement groovyElement) {
			return lookupSiteResolver.resolve(groovySiteLocator.locate(groovyElement));
		}

		if (GradleUtils.KOTLIN_AVAILABLE && element instanceof KtElement ktElement) {
			return lookupSiteResolver.resolve(kotlinSiteLocator.locate(ktElement));
		}

		return ArtifactReference.unresolved();
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
