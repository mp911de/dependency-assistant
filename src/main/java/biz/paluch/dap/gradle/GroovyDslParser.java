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
import java.util.Optional;
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
import com.intellij.psi.SyntaxTraverser;
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

			boolean platform = GradleUtils.isPlatformSection(configurationName)
					|| GroovyDslUtils.isInsidePlatformBlock(call);
			boolean dependency = GradleUtils.isDependencySection(configurationName);
			boolean plugin = GradleUtils.isPlugin(configurationName) && GroovyDslUtils.isInsidePluginsBlock(call);

			if (plugin) {
				return DeclarationSource.plugin();
			}

			if (platform || dependency) {
				return platform ? DeclarationSource.bom() : DeclarationSource.dependency();
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
			if (!resolved.isResolved()) {
				return null;
			}

			// A platform(libs.x) consumer refines the catalog entry to a BOM import.
			return declarationSource instanceof DeclarationSource.Bom
					? resolved.getDeclaration().at(call, declarationSource)
					: resolved.getDeclaration().at(call);
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

			DependencySite site = parseMapDeclaration(call, declarationSource);
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

			DependencySite site = parseDependency(call, declarationSource);
			return site != null ? dependency(site) : null;
		}

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
	 * @return the parsed dependency, or {@literal null} if the call does not
	 * contain a supported direct declaration.
	 */
	private @Nullable DependencySite parseDependency(GrMethodCall call, DeclarationSource declarationSource) {

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
				DependencySite site = parseVersionBlockDependency(call);
				if (site != null) {
					return site;
				}

				GradleDependency dependency = GradleDependency.parse(rawText, declarationSource, propertyResolver);
				if (dependency != null) {
					return dependency.toDependencySite(call, literal);
				}
			}
		}

		PsiElement commandPlatformString = GroovyDeclarationStyleDetector.getInstance().findCommandPlatformString(call);
		if (commandPlatformString == null) {
			return null;
		}

		String gav = GroovyDeclarationStyleDetector.getInstance()
				.getQuotedCommandArgument(commandPlatformString.getText());
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
	 * @return the parsed declaration, possibly incomplete.
	 */
	private @Nullable DependencySite parseMapDeclaration(GrMethodCall call, DeclarationSource declarationSource) {
		return parseMapDependency(call, call.getNamedArguments(), declarationSource);
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
	 * <p>Bare variable references such as {@code prefer varName} are resolved
	 * through the file-scoped property resolver.
	 *
	 * @param call the method call to parse.
	 * @return the parsed declaration, possibly incomplete if no usable version can
	 * be extracted.
	 */
	private @Nullable DependencySite parseVersionBlockDependency(GrMethodCall call) {

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

		GroovyVersionValue prefer = findConstraintValue(versionClosure, GradleVersionConstraint.PREFER);
		GroovyVersionValue strictly = findConstraintValue(versionClosure, GradleVersionConstraint.STRICTLY);
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
	 * @return the parsed declaration, possibly incomplete.
	 */
	private @Nullable DependencySite parseMapDependency(GrMethodCall call, GrNamedArgument[] named,
			DeclarationSource declarationSource) {

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

	private GroovyVersionValue findConstraintValue(GrClosableBlock versionClosure, String constraint) {

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

			if (!(argument instanceof GrReferenceExpression referenceExpression)) {
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
