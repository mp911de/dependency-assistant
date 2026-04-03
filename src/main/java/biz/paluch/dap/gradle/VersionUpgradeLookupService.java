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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectProperty;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.Property;
import biz.paluch.dap.support.PsiUtils;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry;
import org.jetbrains.kotlin.psi.KtStringTemplateEntry;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import org.toml.lang.psi.TomlInlineTable;
import org.toml.lang.psi.TomlKey;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;
import org.toml.lang.psi.TomlTable;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Gradle-equivalent of {@link VersionUpgradeLookupSupport}. Determines whether the PSI element at the caret represents
 * a version value in a Gradle dependency declaration and returns an
 * {@link VersionUpgradeLookupSupport.UpgradeSuggestion} if a newer version is available.
 * <p>
 * Supported locations:
 * <ul>
 * <li>Version part of a Groovy string-notation GAV literal ({@code 'group:artifact:version'})</li>
 * <li>Version part of a Kotlin DSL string template GAV literal ({@code "group:artifact:version"})</li>
 * <li>Property value in {@code gradle.properties} that maps to a known artifact version</li>
 * <li>Version literal in a {@code libs.versions.toml} {@code [versions]} table</li>
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

	public VersionUpgradeLookupService(Project project, PsiFile file) {
		super(project, GradleProjectContext.of(project, file));

		GradlePsiListener.getInstance(project);

		this.file = file;
		this.buildContext = GradleProjectContext.of(project, file);
		this.candidate = GradleUtils.isGradleFile(file);

		DependencyAssistantService service = DependencyAssistantService.getInstance(project);
		this.cache = service.getCache();
		this.projectState = buildContext.isAvailable() ? service.getProjectState(buildContext.getProjectId()) : null;
	}

	/**
	 * Resolves an upgrade suggestion for the given PSI element, or returns {@code null} when the element is not at a
	 * version position or no newer version is available.
	 */
	public VersionUpgradeLookupSupport.@Nullable UpgradeSuggestion determineUpgrade(PsiElement element) {

		UpgradeAvailable availableUpgrade = findAvailableUpgrade(element);
		if (availableUpgrade == null || !availableUpgrade.metadata().localVersionDeclared()) {
			return null;
		}

		return availableUpgrade.suggestion();
	}

	@Override
	public @Nullable UpgradeAvailable findAvailableUpgrade(PsiElement element) {

		DependencyLookupResult result = lookupDependency(element);

		if (result == null || projectState == null || result.version() == null) {
			return null;
		}

		List<Release> options = cache.getReleases(result.artifactId(), false);
		if (options.isEmpty()) {
			return null;
		}

		UpgradeSuggestion upgradeSuggestion = VersionUpgradeLookupSupport.determineUpgrade(result.version(), options);

		if (upgradeSuggestion == null) {
			return null;
		}

		return new UpgradeAvailable(upgradeSuggestion, result);
	}

	public @Nullable Dependency findDependency(PsiElement element) {

		DependencyLookupResult result = lookupDependency(element);

		if (result == null || projectState == null) {
			return null;
		}

		return projectState.findDependency(result.artifactId());
	}

	/**
	 * Resolves an upgrade suggestion for the given PSI element, or returns {@code null} when the element is not at a
	 * version position or no newer version is available.
	 */
	public @Nullable DependencyLookupResult lookupDependency(PsiElement element) {

		if (!candidate || !buildContext.isAvailable()) {
			return null;
		}

		VirtualFile file = this.file.getVirtualFile();

		if (GradleUtils.isVersionCatalog(file)) {
			return resolveTomlVersionElement(element);
		}

		if (GradleUtils.isGradlePropertiesFile(file)) {
			return resolvePropertyVersionElement(element);
		}

		if (GradleUtils.isGroovyDsl(file) && element instanceof GrLiteral groovyTarget) {
			DependencyLookupResult dep = resolveDependency(GroovyDslUtils.findGroovyVersionElement(groovyTarget));
			if (dep != null) {
				return dep;
			}
			return resolveGroovyExtPropertyElement(element);
		}

		if (GradleUtils.KOTLIN_AVAILABLE) {
			return resolveKotlinDslDependency(element);
		}

		return null;
	}

	private @Nullable DependencyLookupResult resolveTomlVersionElement(PsiElement element) {

		// [versions] table value

		if (!(element instanceof TomlLiteral)) {
			return null;
		}

		TomlKeyValue kv = findParentTomlKeyValue(element);
		if (kv == null) {
			return null;
		}

		// Check if the KV is inside a [versions] table (parent is TomlTable with header [versions])
		if (isInsideTable(element, "versions"::equals)) {

			String versionKey = kv.getKey().getText().trim();
			String version = stripTomlQuotes(element.getText());
			if (version == null) {
				return null;
			}

			// Look for an artifact that references this version key
			if (projectState == null) {
				return null;
			}

			ProjectProperty projectProperty = projectState.findProjectProperty(versionKey);
			if (projectProperty == null) {
				return null;
			}

			return resolveDependency(projectProperty, version, false);
		}

		if (isInsideTable(element, it -> it.equals("libraries") || it.equals("plugins"))
				&& kv.getParent() instanceof TomlInlineTable table) {

			String text = element.getParent().getFirstChild().getText();
			if (!text.equals("version")) {
				return null;
			}

			GradleParser.GradleDependency dependency = TomlParser.parseTomlEntry(table, GradleParser::parseArtifactId);
			return resolveDependency(dependency);
		}

		return null;
	}

	private @Nullable DependencyLookupResult resolveDependency(GradleParser.@Nullable GradleDependency dependency) {

		if (dependency instanceof GradleParser.PropertyManagedDependency managed) {
			return new DependencyLookupResult(managed.getId(), null, true, null);
		}

		if (dependency instanceof GradleParser.SimpleDependency simple) {
			return resolveDependency(simple.getId(), simple.version(), true, null);
		}

		return null;
	}

	private @Nullable DependencyLookupResult resolvePropertyVersionElement(PsiElement element) {

		if (!(file instanceof PropertiesFile propsFile) || !(element instanceof PropertyValueImpl leaf)) {
			return null;
		}

		// Check if this element is inside a property value
		IProperty property = findParentProperty(element);
		if (property == null || !StringUtils.hasText(property.getKey())) {
			return null;
		}

		// Check if this property maps to a known artifact in the cache
		if (projectState == null) {
			return null;
		}

		ArtifactId artifactId = projectState.findArtifactByPropertyName(property.getKey());
		String version = property.getValue();
		if (artifactId == null || !StringUtils.hasText(version)) {
			return null;
		}

		return resolveDependency(artifactId, version, true, null);
	}

	private @Nullable DependencyLookupResult resolveDependency(GroovyDslUtils.@Nullable VersionLocation versionLocation) {

		if (versionLocation == null) {
			return null;
		}

		String version = versionLocation.rawVersion();
		if (versionLocation.isPropertyReference()) {
			version = buildContext.getPropertyValue(versionLocation.rawVersion());
			boolean localProperty = buildContext.isLocalProperty(versionLocation.rawVersion());
			ProjectProperty projectProperty = projectState.findProjectProperty(versionLocation.rawVersion(),
					Property::isDeclared);

			if (version != null && projectProperty != null) {
				return resolveDependency(projectProperty, version, localProperty);
			}
		}

		if (version == null) {
			return null;
		}

		return resolveDependency(versionLocation.artifactId(), version, true, null);
	}

	private @Nullable DependencyLookupResult resolveKotlinDslDependency(PsiElement element) {

		if (element instanceof KtBlockStringTemplateEntry propertyCandidate) {

			KtCallExpression dependencyExpression = KotlinDslUtils.findDependencyExpression(propertyCandidate);
			if (dependencyExpression != null) {
				return resolveDependency(KotlinDslUtils.findKotlinVersionElement(dependencyExpression, propertyCandidate));
			}
		}

		if (element instanceof KtLiteralStringTemplateEntry versionCandidate) {

			KtCallExpression dependencyExpression = KotlinDslUtils.findDependencyExpression(versionCandidate);
			if (dependencyExpression != null) {
				return resolveDependency(KotlinDslUtils.findKotlinVersionElement(dependencyExpression, versionCandidate));
			}
		}

		if (element instanceof LeafPsiElement lp
				&& element.getParent() instanceof KtLiteralStringTemplateEntry versionCandidate) {

			KtBinaryExpression propertyExpression = KotlinDslUtils.findPropertyExpression(versionCandidate);
			if (propertyExpression != null) {
				return resolveDependency(propertyExpression, versionCandidate);
			}
		}

		return null;
	}

	private @Nullable DependencyLookupResult resolveDependency(KtBinaryExpression propertyExpression,
			KtStringTemplateEntry version) {

		String property = KotlinDslUtils.findProperty(propertyExpression);
		if (property == null || projectState == null) {
			return null;
		}

		ProjectProperty projectProperty = projectState.findProjectProperty(property);
		if (projectProperty != null) {
			return resolveDependency(projectProperty, version.getText(), true);
		}

		return null;
	}

	private @Nullable Dependency resolveDependency(ArtifactId artifactId, String version) {

		ArtifactVersion current;
		try {
			current = ArtifactVersion.of(version);
			return new Dependency(artifactId, current);
		} catch (Exception e) {
			// Fall back to the cached version for this artifact
			if (projectState != null) {
				return projectState.findDependency(artifactId);
			}
		}
		return null;
	}

	private @Nullable DependencyLookupResult resolveDependency(ProjectProperty property, String version,
			boolean localVersionDeclared) {

		for (CachedArtifact artifact : property.property().artifacts()) {
			DependencyLookupResult result = resolveDependency(artifact.toArtifactId(), version, localVersionDeclared,
					property.id().buildFile());
			if (result != null) {
				return result;
			}
		}

		return null;
	}

	private @Nullable DependencyLookupResult resolveDependency(ArtifactId artifactId, String version,
			boolean localVersionDeclared, @Nullable String versionLocation) {

		return ArtifactVersion.from(version).map(it -> {
			return new DependencyLookupResult(artifactId, ArtifactVersion.of(version), localVersionDeclared, versionLocation);
		}).orElseGet(() -> {

			// Fall back to the cached version for this artifact
			if (projectState != null) {
				Dependency dependency = projectState.findDependency(artifactId);
				if (dependency != null) {
					return new DependencyLookupResult(dependency.getArtifactId(), dependency.getCurrentVersion(),
							localVersionDeclared, versionLocation);
				}
			}
			return null;
		});
	}

	private VersionUpgradeLookupSupport.@Nullable UpgradeSuggestion buildSuggestion(Dependency dependency) {

		List<Release> options = cache.getReleases(dependency.getArtifactId(), false);
		if (options.isEmpty()) {
			return null;
		}

		return VersionUpgradeLookupSupport.determineUpgrade(dependency.getCurrentVersion(), options);
	}

	/**
	 * Resolves a {@link Dependency} when the caret sits inside the value literal of a Groovy {@code ext} property
	 * declaration. Delegates to {@link GradleUtils#findGroovyExtPropertyVersionElement} which recognises all three forms:
	 * {@code ext { set('key', 'v') }}, {@code ext { key = 'v' }}, and {@code ext.key = 'v'}. Returns {@code null} when
	 * the position is not recognised as an ext property or when no artifact is associated with the property key.
	 */
	private @Nullable DependencyLookupResult resolveGroovyExtPropertyElement(PsiElement element) {

		if (projectState == null) {
			return null;
		}

		GroovyDslUtils.PropertyVersionLocation propLoc = GroovyDslUtils.findGroovyExtPropertyVersionElement(element);
		if (propLoc == null || propLoc.propertyKey() == null || propLoc.propertyValue() == null) {
			return null;
		}

		ProjectProperty prop = projectState.findProjectProperty(propLoc.propertyKey());
		if (prop == null) {
			return null;
		}

		return resolveDependency(prop, propLoc.propertyValue(), true);
	}

	private static @Nullable IProperty findParentProperty(PsiElement element) {
		return PsiUtils.findParentOfType(element, IProperty.class);
	}

	private static @Nullable TomlKeyValue findParentTomlKeyValue(PsiElement element) {
		return PsiTreeUtil.getParentOfType(element, TomlKeyValue.class);
	}

	private static boolean isInsideTable(PsiElement element, Predicate<String> predicate) {
		TomlTable table = PsiTreeUtil.getParentOfType(element, TomlTable.class);
		if (table == null) {
			return false;
		}
		TomlKey key = table.getHeader().getKey();
		return key != null && predicate.test(key.getText().trim());
	}

	private static @Nullable String stripTomlQuotes(@Nullable String text) {
		if (text == null) {
			return null;
		}
		if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
			return text.length() > 2 ? text.substring(1, text.length() - 1) : "";
		}
		return text;
	}


}
