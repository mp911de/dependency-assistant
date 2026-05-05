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

import java.util.LinkedHashMap;
import java.util.Map;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtLambdaArgument;
import org.jetbrains.kotlin.psi.KtLambdaExpression;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.KtValueArgumentList;
import org.jetbrains.kotlin.psi.ValueArgument;
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

	KotlinDslParser(DependencyCollector collector, Map<String, String> properties) {
		super(collector, properties);
	}

	KotlinDslParser(DependencyCollector collector, PropertyResolver propertyResolver) {
		super(collector, propertyResolver);
	}

	/**
	 * Create a {@link DependencySite} from parsed Kotlin DSL dependency data.
	 * <p>The supplied {@code declaration} and {@code version} elements are reused
	 * as the PSI anchors for the resulting site.
	 */
	public static @Nullable DependencySite getDependencySite(KtCallElement declaration,
			PsiElement version, String gav, @Nullable PropertyExpression versionExpression) {

		if (version instanceof KtCallElement ktCall && GradleUtils.isPlugin(KotlinDslUtils.getKotlinCallName(ktCall))
				&& KotlinDslUtils.isInsidePluginsBlock(version) && !gav.contains(":")
				&& versionExpression != null) {
			GradleDependency dependency = GradleDependency.of(GradlePlugin.of(gav), versionExpression);
			return dependency.toDependencySite(declaration, version);
		}

		GradleDependency dependency = GradleDependency.parse(gav);
		if (dependency == null) {
			return null;
		}

		if (dependency.getVersionSource().isDefined()) {
			return dependency.toDependencySite(declaration, version);
		}

		if (versionExpression == null
				|| (!versionExpression.isProperty() && !StringUtils.hasText(versionExpression.toString()))) {
			return null;
		}

		return dependency.withVersion(versionExpression).toDependencySite(declaration, version);
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

		DeclarationSource declarationSource = isPlugin ? DeclarationSource.plugin()
				: isPlatform ? DeclarationSource.managed() : DeclarationSource.dependency();
		DependencySite site = parseDependencySite(call, isPlugin, isPlatform, propertyResolver);
		if (site != null) {
			register(site, declarationSource, propertyResolver);
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
	 * @return the dependency site, or {@code null} if the call is not a supported
	 * declaration.
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

		if (!KotlinDslUtils.isInsideBlock(call, "dependencies"::equals) && !isPlatform) {
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
	 * @return the dependency site, or {@code null} if the call is not a supported
	 * declaration.
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
			PluginId id = KotlinPluginIds.fromBinary(call,
					PsiTreeUtil.getParentOfType(call, KtBinaryExpression.class), scriptProperties);
			return id != null ? id.toDependencySite() : null;
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
			return getDependencySite(call, directNotation, string, null);
		}

		KtVersion ktVersion = KtVersion.fromDependency(call);
		if (ktVersion == null || !ktVersion.containsVersion()) {
			return null;
		}

		PropertyExpression versionExpression;
		PsiElement versionElement = ktVersion.getVersionElement();
		if (ktVersion.hasProperty()) {
			String property = ktVersion.getProperty();
			versionExpression = PropertyExpression.property(property);
			PropertyValue version = scriptProperties.getPropertyValue(property);
			if (version != null) {
				versionElement = version.getValueLiteral();
			}
		} else {
			versionExpression = PropertyExpression.from(ktVersion.getVersion());
		}

		return getDependencySite(call, versionElement != null ? versionElement : call,
				KtLiterals.from(call.getValueArgumentList()).toString(), versionExpression);
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
	 * <p>Returns a {@link NamedDependencyDeclaration} when a usable version can be
	 * extracted, or {@code null} otherwise.
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
				if (text != null && text.split(":").length == 2) {
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
				if ("version".equals(KotlinDslUtils.getKotlinCallName(inner))) {
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
		String versionProperty = null;
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
		if (gavText == null) {
			return null;
		}

		String[] parts = gavText.split(":");
		String group = parts[0];
		String artifact = parts[1];

		return new NamedDependencyDeclaration(call.getContainingFile(), null, group, artifact, versionProperty, version,
				call, versionLiteralElement);
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
						propertyResolver);
				if (dependency != null && dependency.getVersionSource().isDefined()) {
					return template;
				}
			}

			if (expression instanceof KtCallElement nested
					&& GradleUtils.isPlatformSection(KotlinDslUtils.getKotlinCallName(nested))) {
				KtStringTemplateExpression nestedTemplate = findInlineDependencyLiteral(nested, propertyResolver);
				if (nestedTemplate != null) {
					return nestedTemplate;
				}
			}
		}

		return null;
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

			if ("group".equals(name)) {
				group = strVal;
			} else if ("name".equals(name)) {
				artifact = strVal;
			} else if ("version".equals(name)) {
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
				call, versionLiteral);
	}

}
