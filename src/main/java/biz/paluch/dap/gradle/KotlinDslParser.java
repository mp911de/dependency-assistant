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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.*;
import org.jspecify.annotations.Nullable;

/**
 * Parser for Kotlin DSL Gradle build files ({@code build.gradle.kts} and
 * {@code settings.gradle.kts}).
 * <p>Extracted declarations are accumulated into a {@link DependencyCollector}
 * using the common Gradle dependency model so that version lookup and update
 * code can operate on Kotlin DSL semantics instead of raw PSI traversal.
 *
 * @author Mark Paluch
 */
class KotlinDslParser extends GradleParser {

	KotlinDslParser(DependencyCollector collector, PropertyResolver propertyResolver) {
		super(collector, propertyResolver);
	}

	// -------------------------------------------------------------------------
	// Kotlin DSL
	// -------------------------------------------------------------------------

	/**
	 * Parses a Kotlin {@code build.gradle.kts} or {@code settings.gradle.kts} file.
	 */
	public void parseKotlinScript(PsiFile file) {

		Map<String, Property> extra = KotlinDslExtraParser.parseExtraProperties(file);
		Map<String, Property> properties = new LinkedHashMap<>(extra);
		properties.putAll(KotlinDslExtraParser.parseValProperties(file));

		PropertyResolver propertyResolver = PropertyResolver.fromMap(properties).withFallback(getPropertyResolver());

		getCollector().addProperties(extra.keySet());

		SyntaxTraverser.psiTraverser(file).filter(KtCallElement.class)
				.forEach(call -> handleKotlinCall(call, propertyResolver));
	}

	private void handleKotlinCall(KtCallElement call, PropertyResolver propertyResolver) {

		String methodName = KotlinDslUtils.getKotlinCallName(call);
		if (methodName == null) {
			return;
		}

		boolean isDependencyConfig = GradleUtils.isDependencySection(methodName);
		boolean isPlatform = GradleUtils.isPlatformSection(methodName);
		boolean isPlugin = KotlinDslUtils.isInsidePluginsBlock(call);

		if (!isDependencyConfig && !isPlatform && !isPlugin) {
			return;
		}

		DependencySite site = parseDependencySite(call, isPlugin, isPlatform, propertyResolver);
		if (site != null) {
			register(site, propertyResolver);
		}
	}

	/**
	 * Parse a Kotlin DSL declaration from the given call.
	 * <p>Supports declarations such as: <pre class="code">
	 * implementation("org.junit.jupiter:junit-jupiter:5.11.0")
	 * implementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.11.0")
	 * implementation("org.junit.jupiter:junit-jupiter") { version { prefer("5.11.0") } }
	 * id("org.springframework.boot") version "3.3.2"
	 * </pre>
	 *
	 * @param call the call to parse.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the dependency site, or {@literal null} if the call is not a
	 * supported declaration.
	 */
	public static @Nullable DependencySite parseDependencySite(KtCallElement call, PropertyResolver propertyResolver) {

		String methodName = KotlinDslUtils.getKotlinCallName(call);
		if (methodName == null) {
			return null;
		}

		boolean isDependencyConfig = GradleUtils.isDependencySection(methodName);
		boolean isPlatform = GradleUtils.isPlatformSection(methodName);
		boolean isPlugin = KotlinDslUtils.isInsidePluginsBlock(call);

		if (!isDependencyConfig && !isPlatform && !isPlugin) {
			return null;
		}
		return parseDependencySite(call, isPlugin, isPlatform, propertyResolver);
	}

	/**
	 * Return whether the literal is the coordinate argument in a direct Kotlin DSL
	 * dependency or platform notation call.
	 */
	static boolean isDirectDependencyNotationLiteral(KtStringTemplateExpression literal) {

		KtValueArgument valueArgument = PsiTreeUtil.getParentOfType(literal, KtValueArgument.class);
		if (valueArgument != null && valueArgument.getArgumentName() != null) {
			return false;
		}

		KtCallExpression call = PsiTreeUtil.getParentOfType(literal, KtCallExpression.class);
		return call != null && isDependencyOrPlatformCall(call) && isArgumentOfCall(literal, call)
				&& call.getLambdaArguments().isEmpty();
	}

	/**
	 * Return whether the literal is a {@code version = "..."} value in map-style
	 * dependency notation.
	 */
	static boolean isVersionNamedArgumentLiteral(KtStringTemplateExpression literal) {

		KtValueArgument namedArgument = PsiTreeUtil.getParentOfType(literal, KtValueArgument.class);
		return namedArgument != null && isVersionNamedArgument(namedArgument)
				&& isExpressionOfValueArgument(literal, namedArgument)
				&& isNamedArgumentOfDependencyCall(namedArgument);
	}

	/**
	 * Return whether the reference is a {@code version = property} value in
	 * map-style dependency notation.
	 */
	static boolean isVersionNamedArgumentReference(KtNameReferenceExpression reference) {

		KtValueArgument namedArgument = PsiTreeUtil.getParentOfType(reference, KtValueArgument.class);
		return namedArgument != null && isVersionNamedArgument(namedArgument)
				&& isExpressionOfValueArgument(reference, namedArgument)
				&& isNamedArgumentOfDependencyCall(namedArgument);
	}

	/**
	 * Return whether the literal is an argument to a Kotlin version-block
	 * constraint call.
	 */
	static boolean isVersionBlockLiteral(KtStringTemplateExpression literal, String constraintName) {

		KtCallExpression constraintCall = PsiTreeUtil.getParentOfType(literal, KtCallExpression.class);
		return constraintCall != null && constraintName.equals(KotlinDslUtils.getKotlinCallName(constraintCall))
				&& isArgumentOfCall(literal, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the reference is an argument to a Kotlin version-block
	 * constraint call.
	 */
	static boolean isVersionBlockReference(KtNameReferenceExpression reference, String constraintName) {

		KtCallExpression constraintCall = PsiTreeUtil.getParentOfType(reference, KtCallExpression.class);
		return constraintCall != null && constraintName.equals(KotlinDslUtils.getKotlinCallName(constraintCall))
				&& isArgumentOfCall(reference, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the literal is the version operand in a Kotlin plugin
	 * declaration.
	 */
	static boolean isPluginVersionLiteral(KtStringTemplateExpression literal) {

		KtBinaryExpression binary = PsiTreeUtil.getParentOfType(literal, KtBinaryExpression.class);
		if (binary == null || !isRightSideOfBinary(literal, binary)) {
			return false;
		}

		return findPluginIdCallForVersionBinary(binary) != null;
	}

	/**
	 * Return whether the literal declares a Kotlin property or extra property that
	 * backs a supported dependency version reference.
	 */
	static boolean isBackingVersionPropertyLiteral(KtStringTemplateExpression literal) {

		String propertyName = findBackingVersionPropertyName(literal);
		if (!StringUtils.hasText(propertyName)) {
			return false;
		}

		PsiFile file = literal.getContainingFile();
		if (file == null) {
			return false;
		}

		return isReferencedBySupportedVersionSite(file, propertyName);
	}

	/**
	 * Walk up from a {@code prefer(...)} or {@code strictly(...)} call to the
	 * enclosing dependency call.
	 */
	static @Nullable KtCallExpression findVersionBlockDependencyCall(KtCallExpression preferOrStrictlyCall) {

		KtLambdaExpression versionLambda = PsiTreeUtil.getParentOfType(preferOrStrictlyCall,
				KtLambdaExpression.class);
		if (versionLambda == null) {
			return null;
		}

		KtCallExpression versionCall = PsiTreeUtil.getParentOfType(versionLambda, KtCallExpression.class);
		if (versionCall == null || !GradleUtils.VERSION.equals(KotlinDslUtils.getKotlinCallName(versionCall))) {
			return null;
		}

		KtLambdaExpression dependencyLambda = PsiTreeUtil.getParentOfType(versionCall, KtLambdaExpression.class);
		if (dependencyLambda == null) {
			return null;
		}

		KtCallExpression dependencyCall = PsiTreeUtil.getParentOfType(dependencyLambda, KtCallExpression.class);
		if (dependencyCall == null || !isDependencyOrPlatformCall(dependencyCall)) {
			return null;
		}

		return dependencyCall;
	}

	private static @Nullable KtCallElement findPluginIdCallForVersionBinary(KtBinaryExpression binary) {

		if (!GradleUtils.VERSION.equals(binary.getOperationReference().getReferencedName())) {
			return null;
		}

		KtExpression left = binary.getLeft();
		if (left instanceof KtCallElement call && GradleUtils.isPlugin(KotlinDslUtils.getKotlinCallName(call))
				&& KotlinDslUtils.isInsidePluginsBlock(call)) {
			return call;
		}

		if (left != null) {
			KtCallElement call = PsiTreeUtil.findChildOfType(left, KtCallElement.class);
			if (call != null && GradleUtils.isPlugin(KotlinDslUtils.getKotlinCallName(call))
					&& KotlinDslUtils.isInsidePluginsBlock(call)) {
				return call;
			}
		}

		return null;
	}

	private static @Nullable String findBackingVersionPropertyName(KtStringTemplateExpression literal) {

		KtProperty property = PsiTreeUtil.getParentOfType(literal, KtProperty.class);
		if (property != null) {
			KtExpression initializer = property.getInitializer();
			if (initializer != null
					&& (initializer == literal || PsiTreeUtil.isAncestor(initializer, literal, false))) {
				return property.getName();
			}

			if (property.hasDelegateExpression()
					&& property.getDelegateExpression() instanceof KtCallExpression delegateCall
					&& "extra".equals(KotlinDslUtils.getKotlinCallName(delegateCall))
					&& isArgumentOfCall(literal, delegateCall)) {
				return property.getName();
			}
		}

		KtBinaryExpression binary = PsiTreeUtil.getParentOfType(literal, KtBinaryExpression.class);
		KotlinExtraAssignment assignment = KotlinExtraAssignment.from(binary);
		if (assignment == null) {
			assignment = KotlinExtraAssignment.fromAlsoReceiver(literal);
		}

		if (assignment != null && assignment.getValueLiteral() == literal) {
			return assignment.getKey();
		}

		return null;
	}

	private static boolean isReferencedBySupportedVersionSite(PsiFile file, String propertyName) {
		return referencedVersionPropertyNames(file).contains(propertyName);
	}

	/**
	 * Return the property names referenced by a supported version site anywhere in
	 * the file. The result is cached and recomputed when the PSI changes, so the
	 * repeated completion-position checks do not each re-traverse the file.
	 */
	private static Set<String> referencedVersionPropertyNames(PsiFile file) {
		return CachedValuesManager.getProjectPsiDependentCache(file,
				psiFile -> computeReferencedVersionPropertyNames(file));
	}

	private static Set<String> computeReferencedVersionPropertyNames(PsiFile file) {

		Set<String> names = new HashSet<>();

		for (KtNameReferenceExpression reference : SyntaxTraverser.psiTraverser(file)
				.filter(KtNameReferenceExpression.class)) {
			if (isVersionPropertyReference(reference)) {
				@Nullable
				String name = reference.getReferencedName();
				if (StringUtils.hasText(name)) {
					names.add(name);
				}
			}
		}

		for (KtArrayAccessExpression arrayAccess : SyntaxTraverser.psiTraverser(file)
				.filter(KtArrayAccessExpression.class)) {
			if (isReferenceInsideSupportedVersionLiteral(arrayAccess)) {
				@Nullable
				String name = getExtraPropertyKey(arrayAccess);
				if (StringUtils.hasText(name)) {
					names.add(name);
				}
			}
		}

		for (KtCallExpression call : SyntaxTraverser.psiTraverser(file).filter(KtCallExpression.class)) {
			if ("property".equals(KotlinDslUtils.getKotlinCallName(call))
					&& isReferenceInsideSupportedVersionLiteral(call)) {
				KtExpression argument = KotlinDslUtils.getFirstValueArgument(call);
				String name = argument != null ? KtLiterals.getText(argument) : null;
				if (StringUtils.hasText(name)) {
					names.add(name);
				}
			}
		}

		return names;
	}

	private static boolean isVersionPropertyReference(KtNameReferenceExpression reference) {
		return isVersionNamedArgumentReference(reference)
				|| isVersionBlockReference(reference, GradleVersionConstraint.PREFER)
				|| isVersionBlockReference(reference, GradleVersionConstraint.STRICTLY)
				|| isReferenceInsideSupportedVersionLiteral(reference);
	}

	private static boolean isReferenceInsideSupportedVersionLiteral(PsiElement reference) {

		KtStringTemplateExpression template = PsiTreeUtil.getParentOfType(reference, KtStringTemplateExpression.class);
		return template != null && isSupportedVersionLiteral(template);
	}

	private static boolean isSupportedVersionLiteral(KtStringTemplateExpression template) {
		return isDirectDependencyNotationLiteral(template)
				|| isVersionNamedArgumentLiteral(template)
				|| isVersionBlockLiteral(template, GradleVersionConstraint.PREFER)
				|| isVersionBlockLiteral(template, GradleVersionConstraint.STRICTLY)
				|| isPluginVersionLiteral(template);
	}

	private static @Nullable String getExtraPropertyKey(KtArrayAccessExpression arrayAccess) {

		if (!(arrayAccess.getArrayExpression() instanceof KtNameReferenceExpression nameReference)
				|| !"extra".equals(nameReference.getReferencedName())) {
			return null;
		}

		for (KtExpression indexExpression : arrayAccess.getIndexExpressions()) {
			String key = KtLiterals.getText(indexExpression);
			if (StringUtils.hasText(key)) {
				return key;
			}
		}

		return null;
	}

	private static boolean isVersionNamedArgument(ValueArgument namedArgument) {
		return namedArgument.getArgumentName() != null
				&& GradleUtils.VERSION.equals(namedArgument.getArgumentName().getAsName().asString());
	}

	private static boolean isExpressionOfValueArgument(PsiElement element, ValueArgument valueArgument) {

		KtExpression expression = valueArgument.getArgumentExpression();
		return expression != null && (expression == element || PsiTreeUtil.isAncestor(expression, element, false));
	}

	private static boolean isNamedArgumentOfDependencyCall(ValueArgument namedArgument) {

		if (!(namedArgument instanceof PsiElement element)) {
			return false;
		}

		KtCallExpression call = PsiTreeUtil.getParentOfType(element, KtCallExpression.class);
		return call != null && isDependencyOrPlatformCall(call);
	}

	private static boolean isDependencyOrPlatformCall(KtCallElement call) {

		String methodName = KotlinDslUtils.getKotlinCallName(call);
		return GradleUtils.isDependencySection(methodName) || GradleUtils.isPlatformSection(methodName);
	}

	private static boolean isArgumentOfCall(PsiElement element, KtCallElement call) {

		for (ValueArgument argument : call.getValueArguments()) {
			KtExpression expression = argument.getArgumentExpression();
			if (expression != null && (expression == element || PsiTreeUtil.isAncestor(expression, element, false))) {
				return true;
			}
		}

		return false;
	}

	private static boolean isRightSideOfBinary(PsiElement element, KtBinaryExpression binary) {

		KtExpression right = binary.getRight();
		return right != null && (right == element || PsiTreeUtil.isAncestor(right, element, false));
	}

	private static @Nullable DependencySite parseDependencySite(KtCallElement call, boolean isPlugin,
			boolean isPlatform, PropertyResolver propertyResolver) {

		if (isMapStyleDeclarationCandidate(call)) {

			// Named arguments: group = "g", name = "a", version = "v"
			NamedDependencyDeclaration entry = parseMapDeclaration(call, propertyResolver);
			if (entry.isComplete()) {
				return entry.toDependencySite(propertyResolver);
			}
		}

		DependencySite site = findDependencySite(call, propertyResolver);

		if (isPlugin) {
			return site;
		}

		if (!KotlinDslUtils.isInsideBlock(call, GradleUtils.DEPENDENCIES::equals) && !isPlatform) {
			return null;
		}

		if (site != null) {
			return site;
		}

		// Try version block: implementation("g:a") { version { prefer("1.0") } }
		NamedDependencyDeclaration versionBlockEntry = parseVersionBlockDeclaration(call, propertyResolver);
		if (versionBlockEntry != null && versionBlockEntry.isComplete()) {
			return versionBlockEntry.toDependencySite(propertyResolver);
		}

		return null;
	}

	/**
	 * Parse a supported Kotlin DSL dependency or plugin declaration.
	 * <p>Supports declarations such as: <pre class="code">
	 * implementation("org.junit.jupiter:junit-jupiter:5.11.0")
	 * implementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.11.0")
	 * implementation("org.junit.jupiter:junit-jupiter") { version { prefer("5.11.0") } }
	 * id("org.springframework.boot") version "3.3.2"
	 * </pre>
	 *
	 * @param call the call to parse.
	 * @param scriptProperties property resolver used for property-backed versions.
	 * @return the dependency site, or {@literal null} if the call is not a
	 * supported declaration.
	 */
	private static @Nullable DependencySite findDependencySite(KtCallElement call,
			PropertyResolver scriptProperties) {

		PsiFile file = call.getContainingFile();
		if (!GradleUtils.isKotlinDsl(file)) {
			return null;
		}

		String methodName = KotlinDslUtils.getKotlinCallName(call);
		if (StringUtils.isEmpty(methodName) || !KotlinDslUtils.isDependencyCall(call)) {
			return null;
		}

		if (GradleUtils.isPlugin(methodName) && KotlinDslUtils.isInsidePluginsBlock(call)) {
			return KotlinPluginDependencySite.fromBinary(call,
					PsiTreeUtil.getParentOfType(call, KtBinaryExpression.class), scriptProperties);
		}
		KtStringTemplateExpression directNotation = findInlineDependencyLiteral(call, scriptProperties);
		if (directNotation != null) {

			KtCallExpression callExpression = PsiTreeUtil.findChildOfType(call.getValueArgumentList(),
					KtCallExpression.class);

			// skip 'implementation(platform' here, we will come to it on the next iteration
			if (callExpression != null && KotlinDslUtils.isDependencyCall(callExpression)) {
				return null;
			}

			String string = KtLiterals.from(call.getValueArgumentList()).toString();
			return getDependencySite(string, call, null, directNotation);
		}

		KtVersion ktVersion = KtVersion.fromDependency(call);
		if (ktVersion == null || !ktVersion.containsVersion()) {
			return null;
		}

		PsiElement versionElement = ktVersion.getVersionElement();
		Expression expression = ktVersion.hasProperty() ? Expression.property(ktVersion.getProperty())
				: Expression.from(ktVersion.getVersion());

		return getDependencySite(KtLiterals.from(call.getValueArgumentList()).toString(), call,
				expression,
				versionElement);
	}

	/**
	 * Create a {@link DependencySite} from parsed Kotlin DSL dependency data.
	 * <p>The supplied {@code declaration} and {@code versionElement} elements are
	 * reused as the PSI anchors for the resulting site.
	 */
	public static @Nullable DependencySite getDependencySite(String gav,
			KtCallElement declaration, @Nullable Expression versionExpression, @Nullable PsiElement versionElement) {

		if (versionElement instanceof KtCallElement ktCall
				&& GradleUtils.isPlugin(KotlinDslUtils.getKotlinCallName(ktCall))
				&& KotlinDslUtils.isInsidePluginsBlock(versionElement)
				&& versionExpression != null) {

			if (!GradlePluginId.isValidPluginId(gav)) {
				return null;
			}

			GradleDependency dependency = GradleDependency.of(GradlePluginId.of(gav), versionExpression,
					DeclarationSource.plugin());
			return dependency.toDependencySite(declaration, versionElement);
		}

		DeclarationSource declarationSource = KotlinDslUtils.isPlatformSection(declaration)
				|| KotlinDslUtils.isInsidePlatformBlock(declaration)
						? DeclarationSource.managed()
						: DeclarationSource.dependency();
		GradleDependency dependency = GradleDependency.parse(gav, declarationSource);
		if (dependency == null) {
			return null;
		}

		if (versionExpression != null) {
			dependency = dependency.withVersion(versionExpression);
		}

		if (versionElement != null) {
			return dependency.toDependencySite(declaration, versionElement);
		}

		return dependency.toDependencySite(declaration);
	}


	/**
	 * Parses a version-block dependency of the form: <pre class="code">
	 * implementation("group:artifact") {
	 *     version {
	 *         strictly("[1.7, 1.8[")
	 *         prefer("1.7.25")
	 *     }
	 * }
	 * </pre>
	 * <p>When {@code propertyResolver} is provided, bare name references such as
	 * {@code prefer(junit)} are resolved through it.
	 * <p>Return a {@link NamedDependencyDeclaration} when a usable version can be
	 * extracted, or {@literal null} otherwise.
	 */
	@Nullable
	private static NamedDependencyDeclaration parseVersionBlockDeclaration(KtCallElement call,
			PropertyResolver propertyResolver) {

		KtStringTemplateExpression gavTemplate = null;
		KtLambdaExpression trailingLambda = null;

		for (ValueArgument arg : call.getValueArguments()) {
			if (arg instanceof KtLambdaArgument lambdaArgument
					&& lambdaArgument.getArgumentExpression() instanceof KtLambdaExpression lambda) {
				trailingLambda = lambda;
				continue;
			}
			if (gavTemplate == null && arg.getArgumentExpression() instanceof KtStringTemplateExpression expr) {
				String text = KtLiterals.from(expr).toString(propertyResolver);
				if (GradleArtifactId.isValid(text)) {
					gavTemplate = expr;
				}
			}
		}

		if (gavTemplate == null || trailingLambda == null) {
			return null;
		}

		KtCallExpression versionCall = null;
		if (trailingLambda.getBodyExpression() != null) {
			for (KtCallExpression inner : PsiTreeUtil.collectElementsOfType(trailingLambda.getBodyExpression(),
					KtCallExpression.class)) {
				if (GradleUtils.VERSION.equals(KotlinDslUtils.getKotlinCallName(inner))) {
					versionCall = inner;
					break;
				}
			}
		}

		if (versionCall == null) {
			return null;
		}

		KtLambdaExpression versionLambda = null;
		for (ValueArgument arg : versionCall.getValueArguments()) {
			if (arg instanceof KtLambdaArgument la && la.getArgumentExpression() instanceof KtLambdaExpression lam) {
				versionLambda = lam;
				break;
			}
		}

		if (versionLambda == null) {
			return null;
		}

		KtStringTemplateExpression preferTemplate = null;
		KtStringTemplateExpression strictlyTemplate = null;
		KtNameReferenceExpression preferNameRef = null;
		KtNameReferenceExpression strictlyNameRef = null;

		if (versionLambda.getBodyExpression() != null) {
			for (KtCallExpression inner : PsiTreeUtil.collectElementsOfType(versionLambda.getBodyExpression(),
					KtCallExpression.class)) {
				String name = KotlinDslUtils.getKotlinCallName(inner);
				for (ValueArgument va : inner.getValueArguments()) {
					KtExpression argExpr = va.getArgumentExpression();
					if (argExpr instanceof KtStringTemplateExpression st) {
						if (GradleVersionConstraint.PREFER.equals(name) && preferTemplate == null) {
							preferTemplate = st;
						} else if (GradleVersionConstraint.STRICTLY.equals(name) && strictlyTemplate == null) {
							strictlyTemplate = st;
						}
					} else if (argExpr instanceof KtNameReferenceExpression nameRef) {
						if (GradleVersionConstraint.PREFER.equals(name) && preferNameRef == null) {
							preferNameRef = nameRef;
						} else if (GradleVersionConstraint.STRICTLY.equals(name) && strictlyNameRef == null) {
							strictlyNameRef = nameRef;
						}
					}
				}
			}
		}

		PsiElement versionLiteralElement;
		String versionProperty;
		String version;

		if (preferTemplate != null) {
			KtLiterals literals = KtLiterals.from(preferTemplate);
			version = KtLiterals.from(preferTemplate).toString(propertyResolver);
			versionLiteralElement = preferTemplate;
			versionProperty = literals.hasProperty() ? literals.getProperty() : null;
		} else if (preferNameRef != null) {
			String refName = preferNameRef.getReferencedName();
			PropertyValue resolved = resolveVersionProperty(propertyResolver, refName);
			if (resolved == null) {
				return null;
			}
			version = resolved.getValue();
			versionLiteralElement = resolved.getValueLiteral();
			versionProperty = refName;
		} else if (strictlyTemplate != null) {
			KtLiterals literals = KtLiterals.from(strictlyTemplate);
			String strictlyText = KtLiterals.from(strictlyTemplate)
					.toString(propertyResolver);
			if (GradleUtils.isVersionRange(literals.toString())) {
				return null;
			}
			version = strictlyText;
			versionLiteralElement = strictlyTemplate;
			versionProperty = literals.hasProperty() ? literals.getProperty() : null;
		} else if (strictlyNameRef != null) {
			String refName = strictlyNameRef.getReferencedName();
			PropertyValue resolved = resolveVersionProperty(propertyResolver, refName);
			if (resolved == null) {
				return null;
			}
			version = resolved.getValue();
			versionLiteralElement = resolved.getValueLiteral();
			versionProperty = refName;
		} else {
			return null;
		}

		if (StringUtils.isEmpty(version)) {
			return null;
		}

		String gavText = KtLiterals.from(gavTemplate).toString(propertyResolver);
		if (StringUtils.isEmpty(gavText) || !GradleArtifactId.isValid(gavText)) {
			return null;
		}

		ArtifactId artifactId = GradleArtifactId.from(gavText);

		return new NamedDependencyDeclaration(call.getContainingFile(), null, artifactId.groupId(),
				artifactId.artifactId(), versionProperty, version, call, versionLiteralElement,
				DeclarationSource.dependency());
	}

	private static @Nullable PropertyValue resolveVersionProperty(PropertyResolver propertyResolver,
			String refName) {

		if (!StringUtils.hasText(refName)) {
			return null;
		}

		return propertyResolver.getPropertyValue(refName);
	}

	private static @Nullable KtStringTemplateExpression findInlineDependencyLiteral(KtCallElement call,
			PropertyResolver propertyResolver) {

		for (ValueArgument valueArgument : call.getValueArguments()) {
			KtExpression expression = valueArgument.getArgumentExpression();
			if (expression instanceof KtStringTemplateExpression template) {
				GradleDependency dependency = GradleDependency.parse(KtLiterals.from(template).toString(),
						DeclarationSource.dependency(), propertyResolver);
				if (dependency != null && dependency.getVersionSource().isDefined()) {
					return template;
				}
			}

			if (expression instanceof KtCallElement nested
					&& GradleUtils.isPlatformSection(KotlinDslUtils.getKotlinCallName(nested))) {
				return findInlineDependencyLiteral(nested, propertyResolver);
			}
		}

		return null;
	}

	static List<String> getSegments(KtDotQualifiedExpression dots) {
		return SyntaxTraverser.psiTraverser(dots)
				.expand(it -> !(it instanceof KtNameReferenceExpression))
				.filter(KtNameReferenceExpression.class)
				.map(it -> KtLiterals.from(it).toString())
				.toList();
	}

	/**
	 * Return whether the given call is a map-style declaration candidate.
	 * <pre class="code">
	 * implementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.11.0")
	 * </pre>
	 */
	public static boolean isMapStyleDeclarationCandidate(KtCallElement call) {

		KtValueArgumentList valueArgumentList = call.getValueArgumentList();
		if (valueArgumentList != null) {
			return valueArgumentList.getArguments().size() > 1;
		}

		return false;
	}

	/**
	 * Parse a Kotlin map-style dependency declaration. <pre class="code">
	 * implementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.11.0")
	 * </pre>
	 *
	 * @param call the call to parse.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the parsed declaration, possibly incomplete.
	 */
	private static NamedDependencyDeclaration parseMapDeclaration(KtCallElement call,
			PropertyResolver propertyResolver) {

		String group = null;
		String artifact = null;
		String version = null;
		String versionProperty = null;
		PsiElement versionLiteral = null;

		for (ValueArgument arg : call.getValueArguments()) {
			String name = arg.getArgumentName() != null ? arg.getArgumentName().getAsName().asString() : null;
			PsiElement expr = arg.getArgumentExpression();
			String strVal;
			strVal = expr instanceof KtStringTemplateExpression st ? KtLiterals.from(st)
					.toString(propertyResolver) : null;

			if (GradleUtils.GROUP.equals(name)) {
				group = strVal;
			} else if (GradleUtils.NAME.equals(name)) {
				artifact = strVal;
			} else if (GradleUtils.VERSION.equals(name)) {
				if (expr instanceof KtStringTemplateExpression st) {
					KtLiterals literals = KtLiterals.from(st);
					if (literals.hasProperty() && propertyResolver.containsProperty(literals.getProperty())) {
						version = propertyResolver.getProperty(literals.getProperty());
						versionLiteral = expr;
						versionProperty = literals.getProperty();
					} else {
						version = literals.getText();
						versionLiteral = expr;
					}
				} else if (expr instanceof KtNameReferenceExpression ref) {
					String refName = ref.getReferencedName();
					PropertyValue element = propertyResolver.getPropertyValue(refName);
					if (element != null) {
						version = element.getValue();
						versionLiteral = element.getValueLiteral();
						versionProperty = refName;
					}
				}
			}
		}

		return new NamedDependencyDeclaration(call.getContainingFile(), null, group, artifact, versionProperty, version,
				call, versionLiteral, DeclarationSource.dependency());
	}

}
