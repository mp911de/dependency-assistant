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

import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.kotlin.psi.*;
import org.jspecify.annotations.Nullable;

/**
 * Kotlin DSL PSI locator for parser-backed artifact declarations.
 *
 * @author Mark Paluch
 */
class KotlinArtifactReferenceLocator implements ArtifactReferenceLocator<KtElement> {

	private final PropertyResolver propertyResolver;

	private final VersionCatalogRegistry registry;

	private final @Nullable ProjectState projectState;

	private @Nullable PsiFile cachedFile;

	private @Nullable KotlinDslFileParser cachedParser;

	KotlinArtifactReferenceLocator(PropertyResolver propertyResolver, VersionCatalogRegistry registry,
			@Nullable ProjectState projectState) {
		this.propertyResolver = propertyResolver;
		this.registry = registry;
		this.projectState = projectState;
	}

	@Override
	public ArtifactReference locate(KtElement element) {

		ArtifactReference catalogReference = locateCatalogReference(element);
		if (catalogReference.isResolved()) {
			return catalogReference;
		}

		if (element instanceof KtStringTemplateExpression propertyCandidate) {
			KtBinaryExpression propertyExpression = findPropertyExpression(propertyCandidate);
			if (KotlinDslExtraParser.isExtra(propertyExpression)) {
				return locateExtraProperty(propertyExpression, propertyCandidate);
			}
		}

		if (element instanceof KtBlockStringTemplateEntry propertyCandidate) {
			KtCallExpression dependencyExpression = findDependencyExpression(propertyCandidate);
			KtLiterals literals = KtLiterals.from(propertyCandidate);
			if (dependencyExpression != null && literals.hasProperty()) {
				return locatePropertyUsage(literals.getProperty(), dependencyExpression);
			}

			if (dependencyExpression != null) {
				return locateDeclaration(dependencyExpression);
			}
		}

		if (element instanceof KtStringTemplateEntry versionCandidate) {

			KtProperty property = findProperty(versionCandidate);
			if (property != null && StringUtils.hasText(property.getName())) {
				return locatePropertyDeclaration(property.getName(), property);
			}

			if (isLeadingTemplateEntry(versionCandidate)) {
				KtStringTemplateExpression expression = (KtStringTemplateExpression) versionCandidate.getParent();

				KtCallExpression declaration = findDependencyExpression(expression);
				return declaration != null ? locateDeclaration(declaration) : ArtifactReference.unresolved();
			}

			KtBinaryExpression propertyExpression = findPropertyExpression(versionCandidate);
			if (KotlinDslExtraParser.isExtra(propertyExpression)) {
				return locateExtraProperty(propertyExpression, versionCandidate);
			}

			// Block-template entries already resolved their dependency call in the
			// KtBlockStringTemplateEntry branch above; only simple entries reach here.
			if (!(versionCandidate instanceof KtBlockStringTemplateEntry)) {
				KtCallExpression declaration = findDependencyExpression(versionCandidate);
				if (declaration != null) {
					return locateDeclaration(declaration);
				}
			}
		}

		if (element instanceof KtNameReferenceExpression propertyCandidate
				&& element.getParent() instanceof ValueArgument) {
			if (GradleUtils.isDependencySection(propertyCandidate.getReferencedName())) {
				return ArtifactReference.unresolved();
			}

			KtCallExpression declaration = findDependencyExpression(propertyCandidate);
			if (declaration != null) {
				return locatePropertyUsage(propertyCandidate.getReferencedName(), declaration);
			}
		}

		return ArtifactReference.unresolved();
	}

	private ArtifactReference locateExtraProperty(@Nullable KtBinaryExpression propertyExpression,
			KtElement versionEntry) {

		if (propertyExpression == null) {
			return ArtifactReference.unresolved();
		}

		String propertyName = findProperty(propertyExpression);
		if (!StringUtils.hasText(propertyName)) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReferenceUtils.resolve(propertyName, versionEntry.getText(), propertyExpression, versionEntry,
				projectState);
	}

	private ArtifactReference locatePropertyDeclaration(String propertyName, KtElement declaration) {

		Property propertyValue = parserFor(declaration).getPropertyValue(propertyName);
		if (propertyValue == null) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReferenceUtils.resolve(propertyValue.getKey(), propertyValue.getValue(), declaration,
				propertyValue.getValueLiteral(), projectState);
	}

	private ArtifactReference locatePropertyUsage(String propertyName, KtCallExpression declaration) {

		KotlinDslFileParser parser = parserFor(declaration);
		if (StringUtils.isEmpty(propertyName) || !parser.containsProperty(propertyName)) {
			return ArtifactReference.unresolved();
		}

		return reference(parser.parse(declaration));
	}

	private ArtifactReference locateCatalogReference(KtElement element) {

		if (!(element instanceof KtDotQualifiedExpression dots)
				|| !(element.getParent() instanceof KtValueArgument arg)) {
			return ArtifactReference.unresolved();
		}

		KtCallExpression catalogCall = PsiTreeUtil.getParentOfType(element, KtCallExpression.class);
		if (!KotlinDslUtils.isCatalogConsumerCall(catalogCall)) {
			return ArtifactReference.unresolved();
		}

		KotlinDslFileParser parser = parserFor(element);
		TomlReference reference = parser.findCatalogReference(catalogCall);
		if (reference == null) {
			return ArtifactReference.unresolved();
		}

		return reference(parser.parse(catalogCall));
	}

	/**
	 * Use the forward parser so lookup and collection share the declaration model.
	 */
	private ArtifactReference locateDeclaration(KtCallExpression declaration) {
		return reference(parserFor(declaration).parse(declaration));
	}

	private static ArtifactReference reference(@Nullable ArtifactDeclaration declaration) {
		return declaration != null ? ArtifactReference.from(declaration) : ArtifactReference.unresolved();
	}

	private KotlinDslFileParser parserFor(PsiElement element) {

		PsiFile file = element.getContainingFile();
		if (cachedParser == null || cachedFile != file) {
			cachedFile = file;
			cachedParser = new KotlinDslFileParser(file, propertyResolver, registry);
		}
		return cachedParser;
	}

	/**
	 * Return whether the given PSI element is a version element suitable for
	 * highlighting or annotation.
	 */
	public static boolean isVersionElement(PsiElement element) {
		return !isLeadingTemplateEntry(element);
	}

	/**
	 * The leading entry in {@code "group:artifact:$version"} is a coordinate, not a
	 * version site.
	 */
	private static boolean isLeadingTemplateEntry(PsiElement element) {
		return element instanceof KtStringTemplateEntry
				&& element.getParent() instanceof KtStringTemplateExpression expression
				&& expression.getChildren().length > 1 && expression.getChildren()[0] == element;
	}

	/**
	 * Use the shared declaration grammar for lookup and completion.
	 * <p>Range-only constraints have no single navigable version.
	 */
	private static @Nullable KtCallExpression findDependencyExpression(PsiElement element) {

		if (PsiTreeUtil.getParentOfType(element, KtArrayAccessExpression.class) != null) {
			return null;
		}

		DeclarationStyle site = KotlinDeclarationStyleDetector.getInstance().detect(element);
		if (site.isAbsent() || !site.kind().isInlineInCall() || isVersionConstraintRange(site)) {
			return null;
		}

		return site.owningCall() instanceof KtCallExpression call ? call : null;
	}

	private static boolean isVersionConstraintRange(DeclarationStyle site) {
		return (site.kind() == DeclarationStyle.Kind.VERSION_BLOCK_PREFER || site.kind().isBoundingConstraint())
				&& site.versionElement() instanceof KtStringTemplateExpression template
				&& GradleUtils.isVersionRange(KtLiterals.from(template).toString());
	}

	/**
	 * Find the property owning a literal template entry, or {@literal null}.
	 */
	public static @Nullable KtProperty findProperty(KtElement element) {
		return element instanceof KtLiteralStringTemplateEntry
				? PsiTreeUtil.getParentOfType(element, KtProperty.class)
				: null;
	}

	/**
	 * Find an {@code extra["key"] = value} assignment, including an {@code also}
	 * receiver.
	 */
	public static @Nullable KtBinaryExpression findPropertyExpression(KtElement element) {

		if (element.getParent() instanceof KtContainerNode) {
			return null;
		}

		KtBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, KtBinaryExpression.class);
		KotlinExtraAssignment extra = null;
		if (binaryExpression != null) {
			extra = KotlinExtraAssignment.from(binaryExpression);
		}

		if (extra == null) {
			extra = KotlinExtraAssignment.fromAlsoReceiver(element);
		}

		return extra != null ? extra.getDeclaration() : null;
	}

	/**
	 * Extract the key of an {@code extra} assignment, or {@literal null}.
	 */
	@Contract("null -> null")
	public static @Nullable String findProperty(@Nullable KtBinaryExpression element) {

		KotlinExtraAssignment assignment = KotlinExtraAssignment.from(element);
		return assignment != null ? assignment.getKey() : null;
	}


}
