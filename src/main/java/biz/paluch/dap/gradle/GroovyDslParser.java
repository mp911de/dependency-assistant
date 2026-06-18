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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.VersionedDependencySite;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jspecify.annotations.Nullable;

/**
 * Parser for individual dependency declarations in a Groovy DSL Gradle file.
 * Resolution collaborators and declaration-form strategies share the lifecycle
 * of one {@link GroovyDslFileParser}.
 *
 * @author Mark Paluch
 * @see ArtifactDeclaration
 */
class GroovyDslParser {

	private final PropertyResolver propertyResolver;

	private final VersionCatalogRegistry registry;

	private final List<ParsingStrategy<GroovyDeclarationCall, GrMethodCall>> strategies;

	GroovyDslParser(PropertyResolver propertyResolver, VersionCatalogRegistry registry) {
		this.propertyResolver = propertyResolver;
		this.registry = registry;
		this.strategies = List.of(new VersionCatalogStrategy(), new PluginStrategy(), new MapStyleStrategy(),
				new DependencyNotationStrategy());
	}

	/**
	 * Parse a Groovy DSL declaration from the given call.
	 * <p>Supports declarations such as: <pre class="code">
	 * implementation 'org.junit.jupiter:junit-jupiter:5.11.0'
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * implementation('org.junit.jupiter:junit-jupiter') { version { prefer '5.11.0' } }
	 * id 'org.springframework.boot' version '3.3.2'
	 * implementation libs.spring.core
	 * </pre>
	 * @param call the configuration call to parse.
	 * @return the parsed declaration, or {@literal null} when the call is not
	 * supported.
	 */
	@Nullable
	ArtifactDeclaration parse(GrMethodCall call) {

		GroovyDeclarationCall declarationCall = GroovyDeclarationCall.from(call);
		if (declarationCall == null) {
			return null;
		}

		for (ParsingStrategy<GroovyDeclarationCall, GrMethodCall> strategy : strategies) {
			if (strategy.supports(declarationCall)) {
				ArtifactDeclaration declaration = declarationCall.parse(strategy::parse);
				if (declaration != null) {
					return declaration;
				}
			}
		}

		return null;
	}

	/**
	 * Return whether the call is a recognized Gradle dependency, platform, plugin,
	 * or version-catalog declaration, that is whether {@link #parse(GrMethodCall)}
	 * can classify it. Used by reverse element lookup to find the construct that
	 * owns an element.
	 */
	static boolean isDeclarationCall(GrMethodCall call) {
		return GroovyDeclarationCall.from(call) != null;
	}

	@Nullable
	TomlReference findCatalogReference(GrMethodCall call) {

		GrExpression accessor = GroovyDslUtils.getFirstGroovyCatalogArgumentExpression(call);
		if (accessor == null) {
			return null;
		}

		return TomlReference.from(GroovyDslUtils.getVersionCatalogSegments(accessor), registry.catalogPaths().keySet());
	}

	private ArtifactDeclaration dependency(DependencySite site) {
		ArtifactReference artifactReference = ArtifactReferenceUtils.resolve(site, () -> propertyResolver);
		return artifactReference.getDeclaration();
	}

	/**
	 * Groovy DSL configuration call classified for declaration parsing.
	 */
	static class GroovyDeclarationCall implements ConfigurationContext {

		private final GrMethodCall call;

		private final DeclarationSource source;

		private final String configurationName;

		private final boolean catalogConsumer;

		private GroovyDeclarationCall(GrMethodCall call, DeclarationSource source, String configurationName,
				boolean catalogConsumer) {
			this.call = call;
			this.source = source;
			this.configurationName = configurationName;
			this.catalogConsumer = catalogConsumer;
		}

		static @Nullable GroovyDeclarationCall from(@Nullable GrMethodCall call) {

			if (call == null) {
				return null;
			}

			String configurationName = GroovyDslUtils.getGroovyMethodName(call);
			if (StringUtils.isEmpty(configurationName)) {
				return null;
			}

			boolean catalogConsumer = GroovyDslUtils.isGroovyCatalogConsumerCall(call);
			DeclarationSource source = declarationSource(call, configurationName);
			if (source == null && !catalogConsumer) {
				return null;
			}

			return new GroovyDeclarationCall(call, source != null ? source : DeclarationSource.dependency(),
					configurationName, catalogConsumer);
		}

		private static @Nullable DeclarationSource declarationSource(GrMethodCall call, String configurationName) {

			boolean platform = GradleUtils.isPlatformSection(configurationName);
			boolean dependency = GradleUtils.isDependencySection(configurationName);
			boolean plugin = GradleUtils.isPlugin(configurationName) && GroovyDslUtils.isInsidePluginsBlock(call);

			if (plugin) {
				return DeclarationSource.plugin();
			}

			if (platform || dependency) {
				return platform ? DeclarationSource.managed() : DeclarationSource.dependency();
			}

			return null;
		}

		GrMethodCall getCall() {
			return call;
		}

		boolean isCatalogConsumer() {
			return catalogConsumer;
		}

		@Override
		public DeclarationSource getDeclarationSource() {
			return source;
		}

		@Override
		public String getConfigurationName() {
			return configurationName;
		}

		@Nullable
		ArtifactDeclaration parse(
				BiFunction<GrMethodCall, DeclarationSource, @Nullable ArtifactDeclaration> parser) {
			return parser.apply(call, source);
		}

	}

	private class VersionCatalogStrategy implements ParsingStrategy<GroovyDeclarationCall, GrMethodCall> {

		@Override
		public boolean supports(GroovyDeclarationCall call) {
			return call.isCatalogConsumer();
		}

		@Override
		public @Nullable ArtifactDeclaration parse(GrMethodCall call, DeclarationSource declarationSource) {

			TomlReference reference = findCatalogReference(call);
			if (reference == null) {
				return null;
			}

			ArtifactReference resolved = registry.resolve(reference);
			return resolved.isResolved() ? resolved.getDeclaration().at(call) : null;
		}

	}

	private class PluginStrategy implements ParsingStrategy<GroovyDeclarationCall, GrMethodCall> {

		@Override
		public boolean supports(GroovyDeclarationCall call) {
			return call.isPlugin();
		}

		@Override
		public @Nullable ArtifactDeclaration parse(GrMethodCall call, DeclarationSource declarationSource) {

			DependencySite site = GroovyPluginDependencySite.fromMethodCall(call, propertyResolver);
			return site != null ? dependency(site) : null;
		}

	}

	private class MapStyleStrategy implements ParsingStrategy<GroovyDeclarationCall, GrMethodCall> {

		@Override
		public boolean supports(GroovyDeclarationCall call) {
			return call.isDependency() && isMapStyleDeclarationCandidate(call.getCall());
		}

		@Override
		public @Nullable ArtifactDeclaration parse(GrMethodCall call, DeclarationSource declarationSource) {

			DependencySite site = parseMapDeclaration(call, declarationSource, propertyResolver);
			return site != null ? dependency(site) : null;
		}

	}

	private class DependencyNotationStrategy implements ParsingStrategy<GroovyDeclarationCall, GrMethodCall> {

		@Override
		public boolean supports(GroovyDeclarationCall call) {
			return call.isDependency();
		}

		@Override
		public @Nullable ArtifactDeclaration parse(GrMethodCall call, DeclarationSource declarationSource) {

			DependencySite site = parseDependency(call, declarationSource, propertyResolver);
			return site != null ? dependency(site) : null;
		}

	}

	/**
	 * Return whether the literal can represent a Groovy string value.
	 */
	static boolean isStringLiteral(@Nullable GrLiteral literal) {
		return literal != null && literal.getValue() instanceof String;
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
		return namedArgument != null && GradleUtils.VERSION.equals(namedArgument.getLabelName())
				&& isExpressionOfNamedArgument(literal, namedArgument)
				&& findNamedArgumentDependencyCall(namedArgument) != null;
	}

	/**
	 * Return whether the reference is a {@code version: property} value in
	 * map-style dependency notation.
	 */
	static boolean isVersionNamedArgumentReference(GrReferenceExpression reference) {

		GrNamedArgument namedArgument = PsiTreeUtil.getParentOfType(reference, GrNamedArgument.class);
		return namedArgument != null && GradleUtils.VERSION.equals(namedArgument.getLabelName())
				&& isExpressionOfNamedArgument(reference, namedArgument)
				&& findNamedArgumentDependencyCall(namedArgument) != null;
	}

	/**
	 * Return whether the literal is an argument to a version-block constraint call.
	 */
	static boolean isVersionBlockLiteral(GrLiteral literal, String constraintName) {

		GrMethodCall constraintCall = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		return constraintCall != null && constraintName.equals(GroovyDslUtils.getGroovyMethodName(constraintCall))
				&& isArgumentOfCall(literal, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the reference is an argument to a version-block constraint
	 * call.
	 */
	static boolean isVersionBlockReference(GrReferenceExpression reference, String constraintName) {

		GrMethodCall constraintCall = PsiTreeUtil.getParentOfType(reference, GrMethodCall.class);
		return constraintCall != null && constraintName.equals(GroovyDslUtils.getGroovyMethodName(constraintCall))
				&& isArgumentOfCall(reference, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the literal is an argument to any supported version-block
	 * constraint call ({@code prefer} or {@code strictly}).
	 * <p>Consolidates the per-constraint checks into a single ancestor walk so
	 * per-element reverse resolution does not recompute the enclosing dependency
	 * call once per constraint name.
	 */
	private static boolean isVersionBlockLiteral(GrLiteral literal) {

		GrMethodCall constraintCall = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		return constraintCall != null
				&& GradleVersionConstraint.isConstraint(GroovyDslUtils.getGroovyMethodName(constraintCall))
				&& isArgumentOfCall(literal, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the reference is an argument to any supported version-block
	 * constraint call ({@code prefer} or {@code strictly}).
	 */
	private static boolean isVersionBlockReference(GrReferenceExpression reference) {

		GrMethodCall constraintCall = PsiTreeUtil.getParentOfType(reference, GrMethodCall.class);
		return constraintCall != null
				&& GradleVersionConstraint.isConstraint(GroovyDslUtils.getGroovyMethodName(constraintCall))
				&& isArgumentOfCall(reference, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the literal is the version argument in a Groovy plugin
	 * declaration.
	 */
	static boolean isPluginVersionLiteral(GrLiteral literal) {

		GrMethodCall call = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		return call != null && findPluginIdCallForVersionCall(call) != null && isArgumentOfCall(literal, call);
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
		return file != null && referencedVersionPropertyNames(file).contains(assignment.getKey());
	}

	/**
	 * Return the property names referenced by a supported declaration anywhere in
	 * the file. The result is cached and recomputed when the PSI changes, so the
	 * repeated completion-position checks do not each re-traverse the file.
	 */
	private static Set<String> referencedVersionPropertyNames(PsiFile file) {
		return CachedValuesManager.getProjectPsiDependentCache(file,
				psiFile -> {

					Set<String> names = new HashSet<>();

					for (GrReferenceExpression reference : SyntaxTraverser.psiTraverser(psiFile)
							.filter(GrReferenceExpression.class)) {
						if (isVersionPropertyReference(reference)
								&& StringUtils.hasText(reference.getReferenceName())) {
							names.add(reference.getReferenceName());
						}
					}

					return names;
				});
	}

	/**
	 * Walks up from a {@code prefer} or {@code strictly} call to the enclosing
	 * dependency method call, returning the outer {@link GrMethodCall} when the
	 * full version-block structure is present, or {@literal null} otherwise.
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

	private static boolean isExpressionOfNamedArgument(PsiElement element, GrNamedArgument namedArgument) {

		PsiElement expression = namedArgument.getExpression();
		return expression != null && (expression == element || PsiTreeUtil.isAncestor(expression, element, false));
	}

	private static @Nullable GrMethodCall findNamedArgumentDependencyCall(GrNamedArgument namedArgument) {

		GrMethodCall call = PsiTreeUtil.getParentOfType(namedArgument, GrMethodCall.class);
		return call != null && isDependencyOrPlatformCall(call) ? call : null;
	}

	private static boolean isVersionPropertyReference(GrReferenceExpression reference) {
		return isVersionNamedArgumentReference(reference)
				|| isVersionBlockReference(reference)
				|| isReferenceInsideSupportedVersionLiteral(reference);
	}

	private static boolean isReferenceInsideSupportedVersionLiteral(GrReferenceExpression reference) {

		GrLiteral literal = PsiTreeUtil.getParentOfType(reference, GrLiteral.class);
		return literal != null && isSupportedVersionLiteral(literal);
	}

	private static boolean isSupportedVersionLiteral(GrLiteral literal) {
		return isDirectDependencyNotationLiteral(literal)
				|| isVersionNamedArgumentLiteral(literal)
				|| isVersionBlockLiteral(literal)
				|| isPluginVersionLiteral(literal);
	}

	/**
	 * Return whether the element occupies a recognized version position, either a
	 * version-definition literal or a property reference used as a version, that a
	 * reverse lookup can resolve to a declaration.
	 * @param element the PSI element under inspection.
	 * @return {@literal true} if the element is a supported version position;
	 * {@literal false} otherwise.
	 */
	static boolean isVersionPosition(PsiElement element) {

		if (element instanceof GrLiteral literal) {
			return isSupportedVersionLiteral(literal);
		}

		if (element instanceof GrReferenceExpression reference) {
			return isVersionNamedArgumentReference(reference)
					|| isVersionBlockReference(reference);
		}

		return false;
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
	 * Parse a direct Groovy string-notation dependency declaration.
	 * <pre class="code">
	 * implementation 'org.junit.jupiter:junit-jupiter:5.11.0'
	 * implementation platform('org.springframework.boot:spring-boot-dependencies:3.3.2')
	 * </pre>
	 *
	 * @param call the method call to inspect.
	 * @param declarationSource the structural origin (direct, managed, plugin, or
	 * profile-scoped) of the declaration.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the parsed dependency, or {@literal null} if the call does not
	 * contain a supported direct declaration.
	 */
	private static @Nullable DependencySite parseDependency(GrMethodCall call, DeclarationSource declarationSource,
			PropertyResolver propertyResolver) {

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

				GradleDependency dependency = GradleDependency.parse(rawText,
						declarationSource, propertyResolver);
				if (dependency != null) {
					return dependency.toDependencySite(call, literal);
				}
			}
		}

		PsiElement commandPlatformString = findCommandPlatformString(call);
		if (commandPlatformString == null) {
			return null;
		}

		String gav = getQuotedCommandArgument(commandPlatformString.getText());
		if (StringUtils.isEmpty(gav)) {
			return null;
		}

		GradleDependency dependency = GradleDependency.parse(gav, declarationSource, propertyResolver);
		return dependency != null ? dependency.toDependencySite(call, commandPlatformString) : null;
	}

	/**
	 * Return whether the given call is a map-style declaration candidate.
	 * <pre class="code">
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * </pre>
	 */
	private static boolean isMapStyleDeclarationCandidate(GrMethodCall call) {
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
	 * @param declarationSource the structural origin (direct, managed, plugin, or
	 * profile-scoped) of the declaration.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the parsed declaration, possibly incomplete.
	 */
	private static @Nullable DependencySite parseMapDeclaration(GrMethodCall call, DeclarationSource declarationSource,
			PropertyResolver propertyResolver) {
		return parseMapDependency(call, call.getNamedArguments(), declarationSource, propertyResolver);
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
		if (StringUtils.isEmpty(gavText) || !GradleArtifactId.isValid(gavText)) {
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
			versionSource = VersionSource.from(versionValue.version);
		}

		DependencySite dependencySite = DependencySite.of(artifactId, versionSource, DeclarationSource.dependency(),
				call);
		if (versionSource.isProperty()) {
			Property propertyValue = propertyResolver
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
				return VersionedDependencySite.of(artifactId, optionalVersion.get(), versionSource,
						DeclarationSource.dependency(), call, versionValue.versionElement());
			}
		}

		return DependencySite.of(artifactId, versionSource, DeclarationSource.dependency(), call);
	}

	/**
	 * Parse a Groovy map-style dependency declaration. <pre class="code">
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * </pre>
	 *
	 * @param call the owning method call.
	 * @param named the named arguments to parse.
	 * @param declarationSource the structural origin (direct, managed, plugin, or
	 * profile-scoped) of the declaration.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the parsed declaration, possibly incomplete.
	 */
	static @Nullable DependencySite parseMapDependency(GrMethodCall call, GrNamedArgument[] named,
			DeclarationSource declarationSource, PropertyResolver propertyResolver) {

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
						Property element = propertyResolver.getPropertyValue(refName);
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
							Property element = propertyResolver.getPropertyValue(refName);
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

		GradleDependency dependency = GradleDependency.fromNamed(group, artifact, versionProperty, version,
				declarationSource, propertyResolver);
		return dependency != null && versionLiteral != null ? dependency.toDependencySite(call, versionLiteral) : null;
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
				|| !hasPlatformArgument(call)) {
			return null;
		}

		if (call.getParent() instanceof GrReferenceExpression referenceExpression
				&& getQuotedCommandArgument(referenceExpression.getText()) != null) {
			return referenceExpression;
		}

		return null;
	}

	private static boolean hasPlatformArgument(GrMethodCall call) {

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

		GrClosableBlock[] closures = call.getClosureArguments();
		return closures.length > 0 ? closures[0] : null;
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

			Property resolved = propertyResolver.getPropertyValue(propertyName);
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

}
