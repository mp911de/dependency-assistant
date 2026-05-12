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
import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.support.VersionedDependencySite;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jspecify.annotations.Nullable;

/**
 * Parser that extracts Gradle dependency declarations from Groovy DSL
 * ({@code build.gradle}) files.
 * <p>Results are accumulated into a {@link DependencyCollector} using the same
 * coordinate model as the Maven parser, making the version-resolution and UI
 * layers build-tool-agnostic.
 *
 * @author Mark Paluch
 */
class GradleParser extends GradleParserSupport {

	private final PropertyResolver global;

	GradleParser(DependencyCollector collector, Map<String, String> properties) {
		super(collector);
		this.global = properties::get;
	}

	GradleParser(DependencyCollector collector, PropertyResolver propertyResolver) {
		super(collector);
		this.global = propertyResolver;
	}

	/**
	 * Return whether the call can contain a supported dependency or plugin
	 * declaration.
	 */
	public static boolean isDependencyCallCandidate(GrMethodCall call) {

		String methodName = GroovyDslUtils.getGroovyMethodName(call);

		return GradleUtils.isPlatformSection(methodName) || GradleUtils.isDependencySection(methodName)
				|| GroovyDslUtils.isInsidePluginsBlock(call);
	}

	/**
	 * Return whether the literal can represent a Groovy string value.
	 */
	static boolean isStringLiteral(GrLiteral literal) {
		return literal.getValue() instanceof String;
	}

	/**
	 * Return whether the literal is the coordinate argument in a direct dependency
	 * notation call.
	 */
	static boolean isDirectDependencyNotationLiteral(GrLiteral literal) {

		if (literal.getParent() instanceof GrNamedArgument) {
			return false;
		}

		GrMethodCall call = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		if (call != null && isDependencyOrPlatformCall(call) && isArgumentOfCall(literal, call)
				&& call.getClosureArguments().length == 0) {
			return true;
		}

		return findCommandPlatformDependencyCall(literal) != null;
	}

	/**
	 * Return whether the literal is a {@code version: '...'} value in map-style
	 * dependency notation.
	 */
	static boolean isVersionNamedArgumentLiteral(GrLiteral literal) {

		GrNamedArgument namedArgument = PsiTreeUtil.getParentOfType(literal, GrNamedArgument.class);
		return namedArgument != null && isVersionNamedArgument(namedArgument)
				&& isExpressionOfNamedArgument(literal, namedArgument)
				&& isNamedArgumentOfDependencyCall(namedArgument);
	}

	/**
	 * Return whether the reference is a {@code version: property} value in
	 * map-style dependency notation.
	 */
	static boolean isVersionNamedArgumentReference(GrReferenceExpression reference) {

		GrNamedArgument namedArgument = PsiTreeUtil.getParentOfType(reference, GrNamedArgument.class);
		return namedArgument != null && isVersionNamedArgument(namedArgument)
				&& isExpressionOfNamedArgument(reference, namedArgument)
				&& isNamedArgumentOfDependencyCall(namedArgument);
	}

	/**
	 * Return whether the literal is an argument to a version-block constraint call.
	 */
	static boolean isVersionBlockLiteral(GrLiteral literal, String constraintName) {

		GrMethodCall constraintCall = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		return constraintCall != null && isVersionConstraintCall(constraintCall, constraintName)
				&& isArgumentOfCall(literal, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the reference is an argument to a version-block constraint
	 * call.
	 */
	static boolean isVersionBlockReference(GrReferenceExpression reference, String constraintName) {

		GrMethodCall constraintCall = PsiTreeUtil.getParentOfType(reference, GrMethodCall.class);
		return constraintCall != null && isVersionConstraintCall(constraintCall, constraintName)
				&& isArgumentOfCall(reference, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the literal is the version argument in a Groovy plugin
	 * declaration.
	 */
	static boolean isPluginVersionLiteral(GrLiteral literal) {

		GrMethodCall call = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		return call != null && isPluginVersionCall(call) && isArgumentOfCall(literal, call);
	}

	/**
	 * Return whether the literal is a Groovy local/ext property that backs a
	 * supported dependency version reference.
	 */
	static boolean isBackingVersionPropertyLiteral(GrLiteral literal) {

		GroovyExtAssignment assignment = GroovyExtAssignment.from(literal);
		if (assignment == null) {
			return false;
		}

		PsiFile file = literal.getContainingFile();
		if (file == null) {
			return false;
		}

		for (GrReferenceExpression reference : SyntaxTraverser.psiTraverser(file)
				.filter(GrReferenceExpression.class)) {
			if (assignment.getKey().equals(reference.getReferenceName()) && isVersionPropertyReference(reference)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Return the dependency call that owns a version named-argument value.
	 */
	static @Nullable GrMethodCall findVersionNamedArgumentDependencyCall(PsiElement element) {

		GrNamedArgument namedArgument = PsiTreeUtil.getParentOfType(element, GrNamedArgument.class);
		if (namedArgument == null || !isVersionNamedArgument(namedArgument)
				|| !isExpressionOfNamedArgument(element, namedArgument)) {
			return null;
		}

		return findNamedArgumentDependencyCall(namedArgument);
	}

	/**
	 * Walks up from a {@code prefer} or {@code strictly} call to the enclosing
	 * dependency method call, returning the outer {@link GrMethodCall} when the
	 * full version-block structure is present, or {@code null} otherwise.
	 */
	static @Nullable GrMethodCall findVersionBlockDependencyCall(GrMethodCall preferOrStrictlyCall) {

		GrClosableBlock versionClosure = PsiTreeUtil.getParentOfType(preferOrStrictlyCall, GrClosableBlock.class);
		if (versionClosure == null) {
			return null;
		}

		GrMethodCall versionCall = PsiTreeUtil.getParentOfType(versionClosure, GrMethodCall.class);
		if (versionCall == null || !GradleUtils.VERSION.equals(GroovyDslUtils.getGroovyMethodName(versionCall))) {
			return null;
		}

		GrClosableBlock depClosure = PsiTreeUtil.getParentOfType(versionCall, GrClosableBlock.class);
		if (depClosure == null) {
			return null;
		}

		GrMethodCall depCall = PsiTreeUtil.getParentOfType(depClosure, GrMethodCall.class);
		if (depCall == null || !isDependencyOrPlatformCall(depCall)) {
			return null;
		}

		return depCall;
	}

	static @Nullable GrMethodCall findCommandPlatformDependencyCall(PsiElement element) {

		PsiElement stringElement = findCommandPlatformString(element);
		if (stringElement == null) {
			return null;
		}

		GrMethodCall call = PsiTreeUtil.findChildOfType(stringElement, GrMethodCall.class);
		return call != null && findCommandPlatformString(call) == stringElement ? call : null;
	}

	static @Nullable PsiElement findCommandPlatformString(PsiElement element) {

		if (element instanceof GrMethodCall call) {
			return findCommandPlatformString(call);
		}

		PsiElement candidate = element instanceof GrReferenceExpression ? element
				: PsiTreeUtil.getParentOfType(element, GrReferenceExpression.class, false);
		if (!(candidate instanceof GrReferenceExpression referenceExpression)) {
			return null;
		}

		GrMethodCall call = PsiTreeUtil.findChildOfType(referenceExpression, GrMethodCall.class);
		return call != null && findCommandPlatformString(call) == candidate ? candidate : null;
	}

	static @Nullable String getCommandPlatformStringText(PsiElement element) {

		PsiElement stringElement = findCommandPlatformString(element);
		return stringElement != null ? getQuotedCommandArgument(stringElement.getText()) : null;
	}

	/**
	 * Return the plugin {@code id(...)} call if {@code call} is the chained
	 * {@code version(...)} call in a Groovy plugin declaration.
	 */
	static @Nullable GrMethodCall findPluginIdCallForVersionCall(GrMethodCall call) {

		if (!(call.getInvokedExpression() instanceof GrReferenceExpression versionReference)) {
			return null;
		}

		if (!GradleUtils.VERSION.equals(versionReference.getReferenceName())) {
			return null;
		}

		if (!(versionReference.getQualifierExpression() instanceof GrMethodCall idCall)) {
			return null;
		}

		if (!GradleUtils.isPlugin(GroovyDslUtils.getGroovyMethodName(idCall))
				|| !GroovyDslUtils.isInsidePluginsBlock(idCall)) {
			return null;
		}

		return idCall;
	}

	private static boolean isVersionNamedArgument(GrNamedArgument namedArgument) {
		return GradleUtils.VERSION.equals(namedArgument.getLabelName());
	}

	private static boolean isExpressionOfNamedArgument(PsiElement element, GrNamedArgument namedArgument) {

		PsiElement expression = namedArgument.getExpression();
		return expression != null && (expression == element || PsiTreeUtil.isAncestor(expression, element, false));
	}

	private static @Nullable GrMethodCall findNamedArgumentDependencyCall(GrNamedArgument namedArgument) {

		GrMethodCall call = PsiTreeUtil.getParentOfType(namedArgument, GrMethodCall.class);
		return call != null && isDependencyOrPlatformCall(call) ? call : null;
	}

	private static boolean isNamedArgumentOfDependencyCall(GrNamedArgument namedArgument) {
		return findNamedArgumentDependencyCall(namedArgument) != null;
	}

	private static boolean isVersionConstraintCall(GrMethodCall call, String constraintName) {
		return constraintName.equals(GroovyDslUtils.getGroovyMethodName(call));
	}

	private static boolean isPluginVersionCall(GrMethodCall call) {
		return findPluginIdCallForVersionCall(call) != null;
	}

	private static boolean isVersionPropertyReference(GrReferenceExpression reference) {
		return isVersionNamedArgumentReference(reference)
				|| isVersionBlockReference(reference, GradleVersionConstraint.PREFER)
				|| isVersionBlockReference(reference, GradleVersionConstraint.STRICTLY)
				|| isReferenceInsideSupportedVersionLiteral(reference);
	}

	private static boolean isReferenceInsideSupportedVersionLiteral(GrReferenceExpression reference) {

		GrLiteral literal = PsiTreeUtil.getParentOfType(reference, GrLiteral.class);
		return literal != null && isSupportedVersionLiteral(literal);
	}

	private static boolean isSupportedVersionLiteral(GrLiteral literal) {
		return isDirectDependencyNotationLiteral(literal)
				|| isVersionNamedArgumentLiteral(literal)
				|| isVersionBlockLiteral(literal, GradleVersionConstraint.PREFER)
				|| isVersionBlockLiteral(literal, GradleVersionConstraint.STRICTLY)
				|| isPluginVersionLiteral(literal);
	}

	private static boolean isDependencyOrPlatformCall(GrMethodCall call) {

		String methodName = GroovyDslUtils.getGroovyMethodName(call);
		return GradleUtils.isDependencySection(methodName) || GradleUtils.isPlatformSection(methodName);
	}

	private static boolean isArgumentOfCall(PsiElement element, GrMethodCall call) {

		for (PsiElement argument : call.getArgumentList().getAllArguments()) {
			if (argument == element || PsiTreeUtil.isAncestor(argument, element, false)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Return the property resolver used as the parser fallback.
	 */
	protected PropertyResolver getPropertyResolver() {
		return global;
	}

	// -------------------------------------------------------------------------
	// Groovy DSL
	// -------------------------------------------------------------------------

	/**
	 * Parses a Groovy {@code build.gradle} or {@code settings.gradle} file and
	 * accumulates all resolvable dependency and plugin declarations into
	 * {@code collector}.
	 */
	public void parseGroovyDsl(PsiFile file) {

		Map<String, PropertyValue> extProperties = GroovyDslExtParser.parseExtProperties(file);
		Map<String, PropertyValue> properties = new LinkedHashMap<>(extProperties);
		properties.putAll(GroovyDslExtParser.parseLocalVariables(file));

		PropertyResolver propertyResolver = PropertyResolver.fromMap(properties)
				.withFallback(global);

		getCollector().addProperties(properties.keySet());

		SyntaxTraverser.psiTraverser(file).filter(GrMethodCall.class)
				.forEach(call -> handleGroovyCall(call, propertyResolver));
	}

	private void handleGroovyCall(GrMethodCall call, PropertyResolver propertyResolver) {

		String methodName = GroovyDslUtils.getGroovyMethodName(call);
		boolean isPlatform = GradleUtils.isPlatformSection(methodName);
		boolean isDependencyConfig = GradleUtils.isDependencySection(methodName);
		boolean isPlugin = GradleUtils.isPlugin(methodName) && GroovyDslUtils.isInsidePluginsBlock(call);

		if (isPlugin) {
			DependencySite pluginSite = GroovyPluginDependencySite.fromMethodCall(call, propertyResolver);
			if (pluginSite != null) {
				register(pluginSite, DeclarationSource.plugin(), propertyResolver);
			}
			return;
		}

		if (!isDependencyConfig && !isPlatform) {
			return;
		}

		DeclarationSource declarationSource = isPlatform ? DeclarationSource.managed()
				: DeclarationSource.dependency();
		DependencySite site = null;
		if (isMapStyleDeclarationCandidate(call)) {
			site = parseMapDeclaration(call, propertyResolver);
		}

		if (site == null) {
			site = parseDependency(call, propertyResolver);
		}

		if (site != null) {
			register(site, declarationSource, propertyResolver);
		}
	}

	/**
	 * Parse a direct Groovy string-notation dependency declaration.
	 * <pre class="code">
	 * implementation 'org.junit.jupiter:junit-jupiter:5.11.0'
	 * implementation platform('org.springframework.boot:spring-boot-dependencies:3.3.2')
	 * </pre>
	 * @param call the method call to inspect.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the parsed dependency, or {@code null} if the call does not contain a
	 * supported direct declaration.
	 */
	public static @Nullable DependencySite parseDependency(GrMethodCall call, PropertyResolver propertyResolver) {

		PsiElement[] args = call.getArgumentList().getAllArguments();
		for (PsiElement arg : args) {

			// Unwrap platform()/enforcedPlatform() wrapper
			if (arg instanceof GrMethodCall innerCall
					&& GradleUtils.PLATFORM_FUNCTIONS.contains(GroovyDslUtils.getGroovyMethodName(innerCall))) {
				PsiElement[] innerArgs = innerCall.getArgumentList().getAllArguments();
				arg = innerArgs.length > 0 ? innerArgs[0] : null;
			}

			if (arg instanceof GrLiteral literal) {

				String rawText = GroovyDslUtils.getText(literal);
				DependencySite site = parseVersionBlockDependency(call, propertyResolver);
				if (site != null) {
					return site;
				}

				GradleDependency dependency = GradleDependency.parse(rawText, propertyResolver);
				if (dependency != null) {
					return dependency.toDependencySite(call, literal);
				}
			}
		}

		PsiElement commandPlatformString = findCommandPlatformString(call);
		if (commandPlatformString != null) {
			GradleDependency dependency = GradleDependency.parse(
					getQuotedCommandArgument(commandPlatformString.getText()), propertyResolver);
			if (dependency != null) {
				return dependency.toDependencySite(call, commandPlatformString);
			}
		}

		return null;
	}

	/**
	 * Return whether the given call is a map-style declaration candidate.
	 * <pre class="code">
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * </pre>
	 */
	public static boolean isMapStyleDeclarationCandidate(GrMethodCall call) {
		GrNamedArgument[] namedArguments = call.getNamedArguments();
		return namedArguments.length > 1;
	}

	/**
	 * Parse Groovy dependency declarations that use version-block or map-style
	 * notation.
	 * <p>Supports declarations such as: <pre class="code">
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * implementation('org.junit.jupiter:junit-jupiter') { version { prefer '5.11.0' } }
	 * </pre>
	 *
	 * @param call the method call to parse.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the parsed declaration, possibly incomplete.
	 */
	static @Nullable DependencySite parseMapDeclaration(GrMethodCall call, PropertyResolver propertyResolver) {
		NamedDependencyDeclaration declaration = parseMapDependency(call, call.getNamedArguments(), propertyResolver);
		return declaration.isComplete() ? declaration.toDependencySite(propertyResolver) : null;
	}

	/**
	 * Parse a Groovy version-block dependency declaration.
	 * <p><pre class="code">
	 * implementation('group:artifact') {
	 *     version {
	 *         strictly '[1.7, 1.8['
	 *         prefer '1.7.25'
	 *     }
	 * }
	 * </pre>
	 * <p>When {@code propertyResolver} is provided, bare variable references such
	 * as {@code prefer varName} are resolved through it.
	 *
	 * @param call the method call to parse.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the parsed declaration, possibly incomplete if no usable version can
	 * be extracted.
	 */
	static @Nullable DependencySite parseVersionBlockDependency(GrMethodCall call, PropertyResolver propertyResolver) {

		GrLiteral gavLiteral = findCoordinateLiteral(call);
		GrClosableBlock depClosure = getFirstClosure(call);

		if (gavLiteral == null) {
			return null;
		}

		if (depClosure == null) {
			return null;
		}

		GrMethodCall versionCall = findNestedMethodCall(depClosure, GradleUtils.VERSION);

		if (versionCall == null) {
			return null;
		}

		GrClosableBlock versionClosure = getFirstClosure(versionCall);
		if (versionClosure == null) {
			return null;
		}

		GroovyVersionValue prefer = findConstraintValue(versionClosure, GradleVersionConstraint.PREFER,
				propertyResolver);
		GroovyVersionValue strictly = findConstraintValue(versionClosure, GradleVersionConstraint.STRICTLY,
				propertyResolver);
		GroovyVersionValue versionValue = prefer.isPresent() ? prefer : strictly;

		if (!versionValue.isPresent()) {
			return null;
		}

		if (strictly.isPresent() && !prefer.isPresent() && GradleUtils.isVersionRange(strictly.version())) {
			return null;
		}

		String gavText = GroovyDslUtils.getText(gavLiteral);
		if (StringUtils.isEmpty(gavText)) {
			return null;
		}

		ArtifactId artifactId = GradleArtifactId.from(gavText).resolve(propertyResolver);
		VersionSource versionSource;
		if (StringUtils.hasText(versionValue.versionProperty) || StringUtils.hasText(versionValue.version)) {
			Expression expression = StringUtils.hasText(versionValue.versionProperty)
					? Expression.property(versionValue.versionProperty)
					: Expression.from(versionValue.version);

			versionSource = expression.asVersionSource();
		} else {
			versionSource = StringUtils.hasText(versionValue.version) ? VersionSource.declared(versionValue.version)
					: VersionSource.none();
		}

		DependencySite dependencySite = DependencySite.of(artifactId, versionSource, call);
		if (versionSource.isProperty()) {
			PropertyValue propertyValue = propertyResolver
					.getPropertyValue(((VersionSource.VersionProperty) versionSource).getProperty());

			if (propertyValue != null) {
				Optional<ArtifactVersion> optionalVersion = ArtifactVersion.from(versionValue.version());
				if (optionalVersion.isPresent()) {
					return dependencySite.withVersion(optionalVersion.get(), propertyValue.getValueLiteral());
				}
			}
		}

		if (versionValue.isPresent()) {
			Optional<ArtifactVersion> optionalVersion = ArtifactVersion.from(versionValue.version());
			if (optionalVersion.isPresent()) {
				return VersionedDependencySite.of(artifactId, optionalVersion.get(), versionSource, call,
						versionValue.versionElement());
			}
		}

		return DependencySite.of(artifactId, versionSource, call);
	}

	/**
	 * Parse a Groovy map-style dependency declaration. <pre class="code">
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * </pre>
	 *
	 * @param call the owning method call.
	 * @param named the named arguments to parse.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the parsed declaration, possibly incomplete.
	 */
	static NamedDependencyDeclaration parseMapDependency(GrMethodCall call, GrNamedArgument[] named,
			PropertyResolver propertyResolver) {

		String group = null;
		String artifact = null;
		String version = null;
		String versionProperty = null;
		PsiElement versionLiteral = null;

		for (GrNamedArgument arg : named) {
			String key = arg.getLabelName();
			PsiElement val = arg.getExpression();
			String strVal = val instanceof GrLiteral lit ? GroovyDslUtils.getText(lit) : null;

			if (GradleUtils.GROUP.equals(key)) {
				group = !StringUtils.isEmpty(strVal) ? propertyResolver.resolvePlaceholders(strVal) : strVal;
			} else if (GradleUtils.NAME.equals(key)) {
				artifact = !StringUtils.isEmpty(strVal) ? propertyResolver.resolvePlaceholders(strVal) : strVal;
			} else if (GradleUtils.VERSION.equals(key)) {

				if (val instanceof GrReferenceExpression ref) {
					String refName = GroovyDslUtils.getRequiredText(ref);
					versionProperty = refName;
					if (StringUtils.hasText(refName)) {
						PropertyValue element = propertyResolver.getPropertyValue(refName);
						if (element != null) {
							version = element.getValue();
							versionLiteral = element.getValueLiteral();
						}
					}
				} else if (val instanceof GrString gstr) {
					GrStringInjection[] injections = gstr.getInjections();
					if (injections.length == 1) {
						GrReferenceExpression ref = PsiTreeUtil.findChildOfType(injections[0],
								GrReferenceExpression.class);
						if (ref != null) {
							String refName = ref.getReferenceName();
							versionProperty = refName;
							PropertyValue element = propertyResolver.getPropertyValue(refName);
							if (element != null) {
								version = element.getValue();
								versionLiteral = element.getValueLiteral();
							}
						}
					}
				} else if (!StringUtils.isEmpty(strVal)) {
					version = strVal;
					versionLiteral = val;
				}
			}
		}

		return new NamedDependencyDeclaration(call.getContainingFile(), null, group, artifact,
				versionProperty, version, call, versionLiteral);
	}

	private static @Nullable GrLiteral findCoordinateLiteral(GrMethodCall call) {

		for (PsiElement argument : call.getArgumentList().getAllArguments()) {
			if (!(argument instanceof GrLiteral literal)) {
				continue;
			}

			String text = GroovyDslUtils.getText(literal);
			if (GradleArtifactId.isValid(text)) {
				return literal;
			}
		}

		return null;
	}

	private static @Nullable PsiElement findCommandPlatformString(GrMethodCall call) {

		if (!GradleUtils.isDependencySection(GroovyDslUtils.getGroovyMethodName(call))
				|| !hasPlatformCommandArgument(call)) {
			return null;
		}

		if (call.getParent() instanceof GrReferenceExpression referenceExpression
				&& getQuotedCommandArgument(referenceExpression.getText()) != null) {
			return referenceExpression;
		}

		return null;
	}

	private static boolean hasPlatformCommandArgument(GrMethodCall call) {

		for (GrReferenceExpression reference : SyntaxTraverser.psiTraverser(call)
				.filter(GrReferenceExpression.class)) {
			if (reference != call.getInvokedExpression()
					&& GradleUtils.isPlatformSection(reference.getReferenceName())) {
				return true;
			}
		}

		return false;
	}

	private static @Nullable String getQuotedCommandArgument(String text) {
		int singleQuote = text.indexOf('\'');
		int doubleQuote = text.indexOf('"');
		int start;
		if (singleQuote == -1) {
			start = doubleQuote;
		} else if (doubleQuote == -1) {
			start = singleQuote;
		} else {
			start = Math.min(singleQuote, doubleQuote);
		}

		if (start == -1) {
			return null;
		}

		char quote = text.charAt(start);
		int end = text.lastIndexOf(quote);
		return end > start ? text.substring(start + 1, end) : null;
	}

	private static @Nullable GrClosableBlock getFirstClosure(GrMethodCall call) {

		for (GrClosableBlock closure : call.getClosureArguments()) {
			return closure;
		}

		return null;
	}

	private static @Nullable GrMethodCall findNestedMethodCall(GrClosableBlock closure, String methodName) {

		for (GrMethodCall methodCall : SyntaxTraverser.psiTraverser(closure).filter(GrMethodCall.class)) {
			if (methodName.equals(GroovyDslUtils.getGroovyMethodName(methodCall))) {
				return methodCall;
			}
		}

		return null;
	}

	private static GroovyVersionValue findConstraintValue(GrClosableBlock versionClosure, String constraint,
			@Nullable PropertyResolver propertyResolver) {

		for (GrMethodCall methodCall : SyntaxTraverser.psiTraverser(versionClosure).filter(GrMethodCall.class)) {
			if (!constraint.equals(GroovyDslUtils.getGroovyMethodName(methodCall))) {
				continue;
			}

			PsiElement argument = getFirstArgument(methodCall);
			if (argument instanceof GrLiteral literal) {
				String version = GroovyDslUtils.getText(literal);
				return StringUtils.hasText(version) ? new GroovyVersionValue(null, version, literal)
						: GroovyVersionValue.absent();
			}

			if (!(argument instanceof GrReferenceExpression referenceExpression) || propertyResolver == null) {
				return GroovyVersionValue.absent();
			}

			String propertyName = referenceExpression.getReferenceName();
			if (!StringUtils.hasText(propertyName)) {
				return GroovyVersionValue.absent();
			}

			PropertyValue resolved = propertyResolver.getPropertyValue(propertyName);
			if (resolved == null || !StringUtils.hasText(resolved.getValue())) {
				return GroovyVersionValue.absent();
			}

			return new GroovyVersionValue(propertyName, resolved.getValue(), resolved.getValueLiteral());
		}

		return GroovyVersionValue.absent();
	}

	private static @Nullable PsiElement getFirstArgument(GrMethodCall call) {

		PsiElement[] arguments = call.getArgumentList().getAllArguments();
		return arguments.length > 0 ? arguments[0] : null;
	}

	private record GroovyVersionValue(@Nullable String versionProperty, @Nullable String version,
			@Nullable PsiElement versionElement) {

		static GroovyVersionValue absent() {
			return new GroovyVersionValue(null, null, null);
		}

		boolean isPresent() {
			return StringUtils.hasText(version) && versionElement != null;
		}

	}

	// -------------------------------------------------------------------------
	// Gradle Properties
	// -------------------------------------------------------------------------
	/**
	 * Parse a {@code gradle.properties} file for project properties that back known
	 * dependency versions.
	 */
	public void parseGradleProperties(Cache cache, PsiFile file) {

		Map<String, String> properties = GradlePropertiesParser.getGradleProperties(file);
		getCollector().addProperties(properties.keySet());

		cache.doWithProperties(property -> {
			if (property.hasArtifacts()) {
				String value = properties.get(property.name());
				if (StringUtils.isEmpty(value)) {
					return;
				}

				ArtifactVersion.from(value).ifPresent(version -> {
					for (CachedArtifact artifact : property.artifacts()) {
						getCollector().registerUsage(artifact.toArtifactId(), version,
								DeclarationSource.managed(),
								VersionSource.property(property.name()));
					}
				});
			}
		});
	}

}
