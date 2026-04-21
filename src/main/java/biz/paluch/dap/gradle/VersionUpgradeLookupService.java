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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GradleParserSupport.NamedDependencyDeclaration;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectProperty;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.PsiPropertyValueElement;
import biz.paluch.dap.support.UpgradeSuggestion;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
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
			return tomlResolver.resolveTomlLiteral(literal);
		}

		if (GradleUtils.isGradlePropertiesFile(vf) && element instanceof PropertyValueImpl propertyValue) {
			return resolveReference(propertyValue);
		}

		if (GradleUtils.isGroovyDsl(vf)) {
			return resolveGroovyArtifactReference(element);
		}

		if (GradleUtils.KOTLIN_AVAILABLE && element instanceof KtElement ktElement) {
			return resolveKotlinArtifactReference(ktElement);
		}

		return ArtifactReference.unresolved();
	}

	protected ArtifactReference resolveGroovyArtifactReference(PsiElement element) {

		ArtifactReference fromCatalog = resolveGroovyVersionCatalogReference(element);

		if (fromCatalog.isResolved()) {
			return fromCatalog;
		}

		if (element instanceof GrLiteral groovyLiteral) {
			DependencyAndVersionLocation location = GroovyDslUtils.findGroovyVersionElement(groovyLiteral,
					propertyResolver);
			GrMethodCall declarationCall = location != null
					? PsiTreeUtil.getParentOfType(location.version(), GrMethodCall.class)
					: null;
			ArtifactReference fromGav = resolveReference(location, declarationCall);
			if (fromGav.isResolved()) {
				return fromGav;
			}
			return resolveExtProperty(groovyLiteral);
		}

		GrReferenceExpression refExpr = element instanceof GrReferenceExpression ref ? ref
				: PsiTreeUtil.getParentOfType(element, GrReferenceExpression.class);
		if (refExpr != null && PsiTreeUtil.getParentOfType(refExpr, GrStringInjection.class) == null) {
			return resolveGroovyVariableReference(refExpr);
		}

		return ArtifactReference.unresolved();
	}

	private ArtifactReference resolveGroovyVariableReference(GrReferenceExpression refExpr) {

		String refName = refExpr.getReferenceName();
		if (StringUtils.isEmpty(refName)) {
			return ArtifactReference.unresolved();
		}

		PsiPropertyValueElement resolved = propertyResolver.getElement(refName);
		if (resolved == null) {
			return ArtifactReference.unresolved();
		}

		NamedDependencyDeclaration declaration = resolveGroovyVariableDeclaration(refExpr);
		if (declaration == null || !declaration.isComplete()) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReference.from(it -> {
			ArtifactId id = ArtifactId.of(declaration.group(), declaration.artifact());
			it.artifact(id).declarationElement(declaration.declaration())
					.versionSource(VersionSource.property(refName));
			ArtifactVersion.from(resolved.propertyValue()).ifPresent(it::version);
			it.versionLiteral(resolved.element());
		});
	}

	private @Nullable NamedDependencyDeclaration resolveGroovyVariableDeclaration(GrReferenceExpression refExpr) {

		GrMethodCall enclosingCall = PsiTreeUtil.getParentOfType(refExpr, GrMethodCall.class);
		if (enclosingCall != null
				&& GradleVersionConstraint.isConstraint(GroovyDslUtils.getGroovyMethodName(enclosingCall))) {
			GrMethodCall dependencyCall = GroovyDslUtils.findVersionBlockDependencyCall(enclosingCall);
			if (dependencyCall != null) {
				return GradleParser.parseVersionBlockDependency(dependencyCall, propertyResolver);
			}
		}

		GrNamedArgument namedArgument = PsiTreeUtil.getParentOfType(refExpr, GrNamedArgument.class);
		if (namedArgument != null && namedArgument.getExpression() == refExpr
				&& "version".equals(namedArgument.getLabelName())) {
			GrMethodCall declarationCall = PsiTreeUtil.getParentOfType(namedArgument, GrMethodCall.class);
			if (declarationCall != null) {
				GrNamedArgument[] namedArguments = declarationCall.getNamedArguments();
				if (namedArguments.length >= 2) {
					return GradleParser.parseMapDependency(declarationCall, namedArguments, propertyResolver);
				}
			}
		}

		return null;
	}

	private ArtifactReference resolveReference(PropertyValueImpl element) {

		IProperty property = findParentProperty(element);
		if (property == null || StringUtils.isEmpty(property.getKey()) || projectState == null) {
			return ArtifactReference.unresolved();
		}

		ArtifactId artifactId = projectState.findArtifactByPropertyName(property.getKey());
		String version = property.getValue();
		if (artifactId == null || StringUtils.isEmpty(version)) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReference.from(it -> {
			it.artifact(artifactId).declarationElement(property.getPsiElement())
					.versionSource(VersionSource.property(property.getKey()));
			ArtifactVersion.from(version).ifPresent(it::version);
			it.versionLiteral(element);
		});
	}

	private ArtifactReference resolveReference(@Nullable DependencyAndVersionLocation location,
			@Nullable PsiElement declarationCall) {

		if (location == null || declarationCall == null) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReferenceUtils.resolve(location, declarationCall, propertyResolver);
	}

	private ArtifactReference resolveExtProperty(GrLiteral literal) {

		if (projectState == null) {
			return ArtifactReference.unresolved();
		}

		PsiPropertyValueElement propLoc = GroovyDslUtils.findGroovyExtPropertyVersionElement(literal);
		if (propLoc == null) {
			return ArtifactReference.unresolved();
		}

		ProjectProperty prop = projectState.findProjectProperty(propLoc.propertyKey());
		if (prop == null) {
			return ArtifactReference.unresolved();
		}

		return resolvePropertyReference(prop, propLoc.propertyKey(), literal, propLoc.propertyValue(), literal);
	}

	protected ArtifactReference resolveKotlinArtifactReference(KtElement element) {

		ArtifactReference fromCatalog = resolveKotlinVersionCatalogReference(element);
		if (fromCatalog.isResolved()) {
			return fromCatalog;
		}

		// value in extra["key"] = …
		if (element instanceof KtStringTemplateExpression propertyCandidate) {
			KtBinaryExpression propertyExpression = KotlinDslUtils.findPropertyExpression(propertyCandidate);
			if (KotlinDslExtraParser.isExtra(propertyExpression)) {
				return resolveExtraProperty(propertyExpression, element);
			}
		}

		if (element instanceof KtBlockStringTemplateEntry propertyCandidate) {

			KtCallExpression dependencyExpression = KotlinDslUtils.findDependencyExpression(propertyCandidate);
			KtLiterals literals = KtLiterals.from(propertyCandidate);
			if (dependencyExpression != null && literals.hasProperty()) {
				return resolveProperty(literals.getProperty(), dependencyExpression);
			}

			if (dependencyExpression != null) {
				return resolveReference(KotlinDslParser.findKotlinVersionElement(dependencyExpression,
						propertyResolver), dependencyExpression);
			}
		}

		if (element instanceof KtStringTemplateEntry versionCandidate) {

			KtProperty property = KotlinDslUtils.findProperty(versionCandidate);
			if (property != null && StringUtils.hasText(property.getName())) {
				return resolveProperty(property.getName(), property);
			}

			if (versionCandidate.getParent() instanceof KtStringTemplateExpression expr) {
				PsiElement[] children = expr.getChildren();
				// prevent identifying org.junit:junit-bom: from org.junit:junit-bom:$junit
				if (children.length > 1 && children[0] == element) {
					return ArtifactReference.unresolved();
				}
			}

			KtBinaryExpression propertyExpression = KotlinDslUtils.findPropertyExpression(versionCandidate);
			if (KotlinDslExtraParser.isExtra(propertyExpression)) {
				return resolveExtraProperty(propertyExpression, element);
			}

			KtCallExpression declaration = KotlinDslUtils.findDependencyExpression(versionCandidate);
			if (declaration != null) {

				DependencyAndVersionLocation location = KotlinDslParser
						.findDependencyAndVersionLocationFromMapDeclaration(declaration,
								versionCandidate, propertyResolver);

				if (location != null) {
					return resolveReference(location, declaration);
				}

				return resolveReference(KotlinDslParser.findKotlinVersionElement(declaration,
						propertyResolver), declaration);
			}
		}

		// Map-style
		// implementation(group = "org.junit", name = "junit-bom", version = junit)
		if (element instanceof KtNameReferenceExpression propertyCandidate
				&& element.getParent() instanceof ValueArgument) {
			if (GradleUtils.isDependencySection(propertyCandidate.getReferencedName())) {
				return ArtifactReference.unresolved();
			}

			KtCallExpression declaration = KotlinDslUtils.findDependencyExpression(propertyCandidate);
			if (declaration != null && element.getParent().getParent().getParent() instanceof KtCallElement call) {
				if (GradleVersionConstraint.isConstraint(KotlinDslUtils.getKotlinCallName(call))) {
					return resolveReference(KotlinDslParser.findKotlinVersionElement(declaration,
							propertyResolver), declaration);
				}
			}

			if (declaration != null) {
				return resolveReference(KotlinDslParser.findDependencyAndVersionLocationFromMapDeclaration(declaration,
						propertyCandidate, propertyResolver), declaration);
			}
		}

		return ArtifactReference.unresolved();
	}

	private ArtifactReference resolveKotlinVersionCatalogReference(PsiElement element) {

		KtCallExpression catalogCall = KotlinDslUtils.findEnclosingCatalogAccessorCall(element, registry);
		if (catalogCall == null) {
			return ArtifactReference.unresolved();
		}
		KtExpression arg = KotlinDslUtils.getFirstValueArgument(catalogCall);
		if (arg == null) {
			return ArtifactReference.unresolved();
		}
		List<String> segments = KotlinDslUtils.collectKotlinCatalogDotSegments(arg);
		return resolveCatalogReference(TomlReference.from(segments, registry.catalogPaths().keySet()), catalogCall);
	}

	private ArtifactReference resolveGroovyVersionCatalogReference(PsiElement element) {

		GrMethodCall catalogCall = GroovyDslUtils.findEnclosingGroovyCatalogAccessorCall(element, registry);
		if (catalogCall == null) {
			return ArtifactReference.unresolved();
		}
		GrExpression arg = GroovyDslUtils.getFirstGroovyCatalogArgumentExpression(catalogCall);
		if (arg == null) {
			return ArtifactReference.unresolved();
		}
		return resolveCatalogReference(GroovyDslUtils.getTomlReference(arg, registry.catalogPaths().keySet()),
				catalogCall);
	}

	private ArtifactReference resolveExtraProperty(KtBinaryExpression propertyExpression,
			KtElement versionEntry) {

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

		return resolvePropertyReference(projectProperty, property, propertyExpression, versionEntry.getText(),
				versionEntry);
	}

	private ArtifactReference resolveProperty(String property, KtExpression declaration) {

		if (projectState == null) {
			return ArtifactReference.unresolved();
		}

		PsiPropertyValueElement element = propertyResolver.getElement(property);
		ProjectProperty projectProperty = projectState.findProjectProperty(property);
		if (projectProperty == null || element == null) {
			return ArtifactReference.unresolved();
		}

		return resolvePropertyReference(projectProperty, property, declaration, element.propertyValue(),
				element.element());
	}

	private ArtifactReference resolveCatalogReference(@Nullable TomlReference reference,
			@Nullable PsiElement declaration) {

		if (reference == null || declaration == null) {
			return ArtifactReference.unresolved();
		}

		return tomlResolver.resolveReference(reference, declaration);
	}

	private ArtifactReference resolvePropertyReference(ProjectProperty property, String propertyName,
			PsiElement declaration, String rawVersion, PsiElement versionLiteral) {

		CachedArtifact artifact = getFirstArtifact(property);
		if (artifact == null) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReference.from(it -> {
			it.artifact(artifact.toArtifactId()).declarationElement(declaration)
					.versionSource(VersionSource.property(propertyName));
			ArtifactVersion.from(rawVersion).ifPresent(it::version);
			it.versionLiteral(versionLiteral);
		});
	}

	private static @Nullable CachedArtifact getFirstArtifact(ProjectProperty property) {

		List<CachedArtifact> artifacts = property.property().artifacts();
		return artifacts.isEmpty() ? null : artifacts.getFirst();
	}

	private static @Nullable IProperty findParentProperty(PsiElement element) {
		return PsiTreeUtil.getParentOfType(element, com.intellij.lang.properties.psi.Property.class);
	}

}
