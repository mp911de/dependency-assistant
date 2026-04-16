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
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
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
		this.tomlResolver = new TomlArtifactResolver(project, file,
				projectState);
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
			ArtifactReference fromGav = resolveReference(
					GroovyDslUtils.findGroovyVersionElement(groovyLiteral, propertyResolver),
					GrMethodCall.class);
			if (fromGav.isResolved()) {
				return fromGav;
			}
			return resolveExtProperty(groovyLiteral);
		}
		return ArtifactReference.unresolved();
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
			Class<? extends PsiElement> callType) {

		if (location == null) {
			return ArtifactReference.unresolved();
		}

		PsiElement declarationCall = PsiTreeUtil.getParentOfType(location.version(), callType);
		if (declarationCall == null) {
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

		for (CachedArtifact artifact : prop.property().artifacts()) {
			ArtifactId id = artifact.toArtifactId();
			String version = propLoc.propertyValue();
			return ArtifactReference.from(it -> {
				it.artifact(id).declarationElement(literal)
						.versionSource(VersionSource.property(propLoc.propertyKey()));
				ArtifactVersion.from(version).ifPresent(it::version);
				it.versionLiteral(literal);
			});
		}

		return ArtifactReference.unresolved();
	}

	protected ArtifactReference resolveKotlinArtifactReference(PsiElement element) {

		ArtifactReference fromCatalog = resolveKotlinTomlReferenceReference(element);
		if (fromCatalog.isResolved()) {
			return fromCatalog;
		}

		if (element instanceof KtBlockStringTemplateEntry propertyCandidate) {
			KtCallExpression dependencyExpression = KotlinDslUtils.findDependencyExpression(propertyCandidate);
			if (dependencyExpression != null) {
				return resolveReference(
						KotlinDslUtils.findKotlinVersionElement(dependencyExpression, propertyCandidate,
								propertyResolver),
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
				return resolveReference(
						KotlinDslUtils.findKotlinVersionElement(dependencyExpression, literalEntry,
								propertyResolver),
						KtCallExpression.class);
			}
			KtBinaryExpression propertyExpression = KotlinDslUtils.findPropertyExpression(literalEntry);
			if (propertyExpression != null) {
				return resolveExtraProperty(propertyExpression, literalEntry);
			}
		}

		return ArtifactReference.unresolved();
	}

	private ArtifactReference resolveKotlinTomlReferenceReference(PsiElement element) {

		KtCallExpression catalogCall = KotlinDslUtils.findEnclosingCatalogAccessorCall(element);
		if (catalogCall == null) {
			return ArtifactReference.unresolved();
		}
		KtExpression arg = KotlinDslUtils.getFirstCatalogValueArgument(catalogCall);
		if (arg == null) {
			return ArtifactReference.unresolved();
		}
		List<String> segments = KotlinDslUtils.collectKotlinCatalogDotSegments(arg);
		TomlReference reference = TomlReference.from(segments);

		if (reference == null) {
			return ArtifactReference.unresolved();
		}

		return tomlResolver.resolveReference(reference, catalogCall);
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
		TomlReference reference = GroovyDslUtils.getTomlReference(arg);
		if (reference == null) {
			return ArtifactReference.unresolved();
		}
		return tomlResolver.resolveReference(reference, catalogCall);
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

	private static @Nullable IProperty findParentProperty(PsiElement element) {
		return PsiTreeUtil.getParentOfType(element, com.intellij.lang.properties.psi.Property.class);
	}

}
