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
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectProperty;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.Property;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry;
import org.jetbrains.kotlin.psi.KtStringTemplateEntry;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import org.toml.lang.psi.TomlFile;
import org.toml.lang.psi.TomlInlineTable;
import org.toml.lang.psi.TomlKey;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;
import org.toml.lang.psi.TomlTable;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementVisitor;
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
 * <li>Kotlin {@code extra["key"]} version value: plain string, triple-quoted string, {@code buildString { append("…")
 * }}, or the receiver literal in {@code "….also { extra["key"] = it }}</li>
 * <li>Property value in {@code gradle.properties} that maps to a known artifact version</li>
 * <li>Version literal in a {@code libs.versions.toml} {@code [versions]} table</li>
 * <li>Version-catalog accessors in Groovy or Kotlin: {@code alias(libs.plugins.…)}, {@code id(libs.plugins.…)} (inside
 * {@code plugins { }}), and dependency configurations such as {@code implementation(libs.…)} /
 * {@code platform(libs.…)}, resolved via {@code gradle/libs.versions.toml}</li>
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

	@Override
	public biz.paluch.dap.support.UpgradeSuggestion suggestUpgrades(PsiElement element) {
		return suggestUpgrades(resolveArtifactReference(element));
	}

	public @Nullable Dependency findDependency(PsiElement element) {

		ArtifactReference result = resolveArtifactReference(element);

		if (!result.isResolved() || projectState == null) {
			return null;
		}

		return projectState.findDependency(result.getArtifactId());
	}

	private biz.paluch.dap.support.UpgradeSuggestion suggestUpgrades(ArtifactReference artifactReference) {

		if (!artifactReference.isResolved()) {
			return biz.paluch.dap.support.UpgradeSuggestion.none();
		}

		ArtifactDeclaration declaration = artifactReference.getDeclaration();
		if (!declaration.hasVersionSource() || !declaration.isVersionDefined()) {
			return biz.paluch.dap.support.UpgradeSuggestion.none();
		}

		List<Release> options = cache.getReleases(declaration.getArtifactId(), false);
		if (options.isEmpty()) {
			return biz.paluch.dap.support.UpgradeSuggestion.none();
		}

		UpgradeSuggestion upgradeSuggestion = VersionUpgradeLookupSupport.determineUpgrade(declaration.getVersion(),
				options);
		if (upgradeSuggestion == null) {
			return biz.paluch.dap.support.UpgradeSuggestion.none();
		}

		return biz.paluch.dap.support.UpgradeSuggestion.of(upgradeSuggestion.strategy(), upgradeSuggestion.bestOption(),
				artifactReference);
	}

	protected ArtifactReference resolveArtifactReference(PsiElement element) {

		if (!candidate || !buildContext.isAvailable()) {
			return ArtifactReference.unresolved();
		}

		VirtualFile file = this.file.getVirtualFile();

		if (GradleUtils.isVersionCatalog(file) && element instanceof TomlLiteral literal) {
			return resolveReference(literal);
		}

		if (GradleUtils.isGradlePropertiesFile(file) && element instanceof PropertyValueImpl propertyValue) {
			return resolveReference(propertyValue);
		}

		if (GradleUtils.isGroovyDsl(file)) {
			if (GroovyDslUtils.isRedundantGroovyCatalogHighlightAnchor(element)) {
				return ArtifactReference.unresolved();
			}
			return resolveGroovyArtifactReference(element);
		}

		if (GradleUtils.KOTLIN_AVAILABLE) {

			if (KotlinDslUtils.isRedundantKotlinVersionHighlightAnchor(element)) {
				return ArtifactReference.unresolved();
			}
			return resolveKotlinArtifactReference(element);
		}

		return ArtifactReference.unresolved();
	}

	protected ArtifactReference resolveGroovyArtifactReference(PsiElement element) {

		ArtifactReference fromCatalog = resolveGroovyVersionCatalogReference(element);
		if (fromCatalog.isResolved()) {
			return fromCatalog;
		}
		if (element instanceof GrLiteral groovyLiteral) {
			ArtifactReference fromGav = resolveReference(GroovyDslUtils.findGroovyVersionElement(groovyLiteral),
					GrMethodCall.class);
			if (fromGav.isResolved()) {
				return fromGav;
			}
			return resolveExtProperty(groovyLiteral);
		}
		return ArtifactReference.unresolved();
	}

	private ArtifactReference resolveReference(TomlLiteral literal) {

		TomlKeyValue kv = findParentTomlKeyValue(literal);
		if (kv == null) {
			return ArtifactReference.unresolved();
		}

		if (isInsideTable(literal, "versions"::equals)) {

			String versionKey = kv.getKey().getText().trim();
			String version = stripTomlQuotes(literal.getText());
			if (version == null || projectState == null) {
				return ArtifactReference.unresolved();
			}

			ProjectProperty projectProperty = projectState.findProjectProperty(versionKey);
			if (projectProperty == null || projectProperty.property().artifacts().isEmpty()) {
				return ArtifactReference.unresolved();
			}

			CachedArtifact first = projectProperty.property().artifacts().iterator().next();
			return ArtifactReference.from(it -> {
				it.artifact(first.toArtifactId()).declarationElement(kv).versionSource(VersionSource.property(versionKey));
				ArtifactVersion.from(version).ifPresent(it::version);
				it.versionLiteral(literal);
			});
		}

		if (isInsideTable(literal, it -> it.equals("libraries") || it.equals("plugins"))
				&& kv.getParent() instanceof TomlInlineTable inlineTable) {

			PsiElement keyPsi = literal.getParent() != null ? literal.getParent().getFirstChild() : null;
			if (keyPsi == null || !"version".equals(keyPsi.getText())) {
				return ArtifactReference.unresolved();
			}

			GradleParser.GradleDependency dependency = TomlParser.parseTomlEntry(inlineTable, GradleParser::parseArtifactId);
			TomlKeyValue libraryOrPluginKv = PsiTreeUtil.getParentOfType(inlineTable, TomlKeyValue.class);
			if (libraryOrPluginKv == null) {
				return ArtifactReference.unresolved();
			}

			if (dependency instanceof GradleParser.PropertyManagedDependency managed) {
				String resolved = buildContext.getPropertyValue(managed.property());
				TomlLiteral versionsLiteral = findTomlVersionsTableLiteral(managed.property());
				PsiElement versionPsi = versionsLiteral != null ? versionsLiteral : findPropertyValuePsi(managed.property());
				return ArtifactReference.from(it -> {
					it.artifact(managed.getId()).declarationElement(libraryOrPluginKv).versionSource(managed.getVersionSource());
					if (StringUtils.hasText(resolved)) {
						ArtifactVersion.from(resolved).ifPresent(it::version);
					}
					if (versionPsi != null) {
						it.versionLiteral(versionPsi);
					}
				});
			}

			if (dependency instanceof GradleParser.SimpleDependency simple) {
				return ArtifactReference.from(it -> {
					it.artifact(simple.getId()).declarationElement(libraryOrPluginKv).versionSource(simple.getVersionSource());
					ArtifactVersion.from(simple.version()).ifPresent(it::version);
					it.versionLiteral(literal);
				});
			}
		}

		return ArtifactReference.unresolved();
	}

	private ArtifactReference resolveReference(PropertyValueImpl element) {

		IProperty property = findParentProperty(element);
		if (property == null || !StringUtils.hasText(property.getKey()) || projectState == null) {
			return ArtifactReference.unresolved();
		}

		ArtifactId artifactId = projectState.findArtifactByPropertyName(property.getKey());
		String version = property.getValue();
		if (artifactId == null || !StringUtils.hasText(version)) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReference.from(it -> {
			it.artifact(artifactId).declarationElement(property.getPsiElement())
					.versionSource(VersionSource.property(property.getKey()));
			ArtifactVersion.from(version).ifPresent(it::version);
			it.versionLiteral(element);
		});
	}

	private ArtifactReference resolveReference(GroovyDslUtils.@Nullable VersionLocation location,
			Class<? extends PsiElement> callType) {

		if (location == null) {
			return ArtifactReference.unresolved();
		}

		PsiElement declarationCall = PsiTreeUtil.getParentOfType(location.element(), callType);
		if (declarationCall == null) {
			return ArtifactReference.unresolved();
		}

		GradleParser.GradleDependency gd = GradleParser.toGradleDependency(location);

		if (gd instanceof GradleParser.PropertyManagedDependency managed) {
			PsiElement versionPsi = findPropertyValuePsi(managed.property());
			return ArtifactReference.from(it -> {
				it.artifact(managed.getId()).declarationElement(declarationCall).versionSource(managed.getVersionSource());
				if (versionPsi != null) {
					ArtifactVersion.from(versionPsi.getText()).ifPresent(it::version);
					it.versionLiteral(versionPsi);
				}
			});
		}

		if (gd instanceof GradleParser.SimpleDependency simple) {
			return ArtifactReference.from(it -> {
				it.artifact(simple.getId()).declarationElement(declarationCall).versionSource(simple.getVersionSource());
				ArtifactVersion.from(simple.version()).ifPresent(it::version);
				it.versionLiteral(location.element());
			});
		}

		return ArtifactReference.unresolved();
	}

	private ArtifactReference resolveExtProperty(GrLiteral literal) {

		if (projectState == null) {
			return ArtifactReference.unresolved();
		}

		GroovyDslUtils.PropertyVersionLocation propLoc = GroovyDslUtils.findGroovyExtPropertyVersionElement(literal);
		if (propLoc == null) {
			return ArtifactReference.unresolved();
		}

		ProjectProperty prop = projectState.findProjectProperty(propLoc.propertyKey());
		if (prop == null) {
			return ArtifactReference.unresolved();
		}

		for (CachedArtifact artifact : prop.property().artifacts()) {
			ArtifactId id = artifact.toArtifactId();
			String version = propLoc.propertyValue();
			return ArtifactReference.from(it -> {
				it.artifact(id).declarationElement(literal).versionSource(VersionSource.property(propLoc.propertyKey()));
				ArtifactVersion.from(version).ifPresent(it::version);
				it.versionLiteral(literal);
			});
		}

		return ArtifactReference.unresolved();
	}

	protected ArtifactReference resolveKotlinArtifactReference(PsiElement element) {

		ArtifactReference fromCatalog = resolveKotlinVersionCatalogReference(element);
		if (fromCatalog.isResolved()) {
			return fromCatalog;
		}

		if (element instanceof KtBlockStringTemplateEntry propertyCandidate) {
			KtCallExpression dependencyExpression = KotlinDslUtils.findDependencyExpression(propertyCandidate);
			if (dependencyExpression != null) {
				return resolveReference(KotlinDslUtils.findKotlinVersionElement(dependencyExpression, propertyCandidate),
						KtCallExpression.class);
			}
		}

		KtLiteralStringTemplateEntry literalEntry = element instanceof KtLiteralStringTemplateEntry le ? le : null;
		if (literalEntry == null && element instanceof LeafPsiElement
				&& element.getParent() instanceof KtLiteralStringTemplateEntry le) {
			literalEntry = le;
		}
		if (literalEntry != null) {
			KtCallExpression dependencyExpression = KotlinDslUtils.findDependencyExpression(literalEntry);
			if (dependencyExpression != null) {
				return resolveReference(KotlinDslUtils.findKotlinVersionElement(dependencyExpression, literalEntry),
						KtCallExpression.class);
			}
			KtBinaryExpression propertyExpression = KotlinDslUtils.findPropertyExpression(literalEntry);
			if (propertyExpression != null) {
				return resolveExtraProperty(propertyExpression, literalEntry);
			}
		}

		return ArtifactReference.unresolved();
	}

	private ArtifactReference resolveKotlinVersionCatalogReference(PsiElement element) {

		KtCallExpression catalogCall = KotlinDslUtils.findEnclosingCatalogAccessorCall(element);
		if (catalogCall == null) {
			return ArtifactReference.unresolved();
		}
		KtExpression arg = KotlinDslUtils.getFirstCatalogValueArgument(catalogCall);
		if (arg == null) {
			return ArtifactReference.unresolved();
		}
		List<String> segments = KotlinDslUtils.collectKotlinCatalogDotSegments(arg);
		if (segments == null) {
			return ArtifactReference.unresolved();
		}
		return resolveCatalogReferenceFromSegments(segments, catalogCall);
	}

	private ArtifactReference resolveGroovyVersionCatalogReference(PsiElement element) {

		GrMethodCall catalogCall = GroovyDslUtils.findEnclosingGroovyCatalogAccessorCall(element);
		if (catalogCall == null) {
			return ArtifactReference.unresolved();
		}
		GrExpression arg = GroovyDslUtils.getFirstGroovyCatalogArgumentExpression(catalogCall);
		if (arg == null) {
			return ArtifactReference.unresolved();
		}
		List<String> segments = GroovyDslUtils.collectGroovyCatalogReferenceSegments(arg);
		if (segments == null) {
			return ArtifactReference.unresolved();
		}
		return resolveCatalogReferenceFromSegments(segments, catalogCall);
	}

	/**
	 * Resolves a {@code libs.…} catalog accessor through {@code gradle/libs.versions.toml}.
	 */
	private ArtifactReference resolveCatalogReferenceFromSegments(List<String> segments, PsiElement declarationElement) {

		GradleVersionCatalogAliasSupport.CatalogTableKey key = GradleVersionCatalogAliasSupport.toCatalogTableKey(segments);
		if (key == null) {
			return ArtifactReference.unresolved();
		}
		PsiFile catalogPsi = TomlParser.findVersionCatalogToml(file.getProject(), file.getVirtualFile());
		if (!(catalogPsi instanceof TomlFile tomlFile)) {
			return ArtifactReference.unresolved();
		}
		TomlKeyValue entryKv = GradleVersionCatalogAliasSupport.findCatalogEntryKeyValue(tomlFile, key.tableName(),
				key.entryKey());
		if (entryKv == null || !(entryKv.getValue() instanceof TomlInlineTable inline)) {
			return ArtifactReference.unresolved();
		}
		GradleParser.GradleDependency dependency = TomlParser.parseTomlEntry(inline,
				GradleVersionCatalogAliasSupport.idFunctionForTable(key.tableName()));
		if (dependency == null) {
			return ArtifactReference.unresolved();
		}

		if (dependency instanceof GradleParser.PropertyManagedDependency managed) {

			String resolved = null;
			if (buildContext.isAvailable()) {
				resolved = buildContext.getPropertyValue(managed.property());
			}

			if (!StringUtils.hasText(resolved)) {
				TomlLiteral versionsLit = GradleVersionCatalogAliasSupport.findVersionsTableLiteral(tomlFile,
						managed.property());
				if (versionsLit != null) {
					resolved = TomlParser.getText(versionsLit);
				}
			}

			TomlLiteral versionsLiteral = GradleVersionCatalogAliasSupport.findVersionsTableLiteral(tomlFile,
					managed.property());
			PsiElement versionPsi = versionsLiteral != null ? versionsLiteral : findPropertyValuePsi(managed.property());
			String finalResolved = resolved;
			return ArtifactReference.from(it -> {
				it.artifact(managed.getId()).declarationElement(declarationElement).versionSource(managed.getVersionSource());
				if (StringUtils.hasText(finalResolved)) {
					ArtifactVersion.from(finalResolved).ifPresent(it::version);
				}
				if (versionPsi != null) {
					it.versionLiteral(versionPsi);
				}
			});
		}

		if (dependency instanceof GradleParser.SimpleDependency simple) {
			TomlLiteral inlineVersion = GradleVersionCatalogAliasSupport.findInlineVersionLiteral(inline);
			return ArtifactReference.from(it -> {
				it.artifact(simple.getId()).declarationElement(declarationElement).versionSource(simple.getVersionSource());
				ArtifactVersion.from(simple.version()).ifPresent(it::version);
				if (inlineVersion != null) {
					it.versionLiteral(inlineVersion);
				}
			});
		}
		return ArtifactReference.unresolved();
	}

	private ArtifactReference resolveExtraProperty(KtBinaryExpression propertyExpression,
			KtStringTemplateEntry versionEntry) {

		if (projectState == null) {
			return ArtifactReference.unresolved();
		}

		String property = KotlinDslUtils.findProperty(propertyExpression);
		if (property == null) {
			return ArtifactReference.unresolved();
		}

		ProjectProperty projectProperty = projectState.findProjectProperty(property);
		if (projectProperty == null) {
			return ArtifactReference.unresolved();
		}

		String rawVersion = versionEntry.getText();

		for (CachedArtifact artifact : projectProperty.property().artifacts()) {
			return ArtifactReference.from(it -> {
				it.artifact(artifact.toArtifactId()).declarationElement(propertyExpression)
						.versionSource(VersionSource.property(property));
				ArtifactVersion.from(rawVersion).ifPresent(it::version);
				it.versionLiteral(versionEntry);
			});
		}

		return ArtifactReference.unresolved();
	}

	private @Nullable TomlLiteral findTomlVersionsTableLiteral(String propertyKey) {

		if (!(file instanceof TomlFile tomlFile)) {
			return null;
		}

		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(tomlFile, TomlTable.class)) {
			TomlKey headerKey = table.getHeader().getKey();
			if (headerKey == null || !"versions".equals(headerKey.getText().trim())) {
				continue;
			}
			for (TomlKeyValue vkv : PsiTreeUtil.getChildrenOfTypeAsList(table, TomlKeyValue.class)) {
				if (propertyKey.equals(vkv.getKey().getText().trim()) && vkv.getValue() instanceof TomlLiteral lit) {
					return lit;
				}
			}
		}
		return null;
	}

	private @Nullable PsiElement findPropertyValuePsi(String propertyName) {

		if (projectState == null) {
			return null;
		}

		ProjectProperty pp = projectState.findProjectProperty(propertyName, Property::isDeclared);
		if (pp == null) {
			return null;
		}

		String path = pp.id().buildFile();
		if (!StringUtils.hasText(path)) {
			return null;
		}

		VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(path);
		if (vf == null) {
			return null;
		}

		Project ideaProject = file.getProject();
		PsiFile psiFile = PsiManager.getInstance(ideaProject).findFile(vf);
		if (psiFile == null) {
			return null;
		}

		if (psiFile instanceof PropertiesFile props) {
			IProperty ip = props.findPropertyByKey(propertyName);
			if (ip != null) {
				return ip.getPsiElement().getLastChild();
			}
			return null;
		}

		if (GradleUtils.isGroovyDsl(vf)) {
			PsiElement[] found = { null };
			psiFile.accept(new PsiRecursiveElementVisitor() {
				@Override
				public void visitElement(PsiElement e) {
					super.visitElement(e);
					if (found[0] != null) {
						return;
					}
					if (e instanceof GrLiteral lit) {
						GroovyDslUtils.PropertyVersionLocation pl = GroovyDslUtils.findGroovyExtPropertyVersionElement(lit);
						if (pl != null && propertyName.equals(pl.propertyKey())) {
							found[0] = lit;
						}
					}
				}
			});
			return found[0];
		}

		if (GradleUtils.isKotlinDsl(vf) && GradleUtils.KOTLIN_AVAILABLE) {
			PsiElement kotlinExtra = KotlinDslExtraSupport.findExtraPropertyValuePsi(psiFile, propertyName);
			if (kotlinExtra != null) {
				return kotlinExtra;
			}
		}

		return null;
	}

	private static @Nullable IProperty findParentProperty(PsiElement element) {
		return PsiTreeUtil.getParentOfType(element, com.intellij.lang.properties.psi.Property.class);
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
