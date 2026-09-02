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

import java.util.List;
import java.util.function.BiFunction;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
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

		private GroovyDeclarationCall(GrMethodCall call, DeclarationSource source, String configurationName) {
			this.call = call;
			this.source = source;
			this.configurationName = configurationName;
		}

		static @Nullable GroovyDeclarationCall from(@Nullable GrMethodCall call) {

			if (call == null) {
				return null;
			}

			String configurationName = GroovyDslUtils.getGroovyMethodName(call);
			if (StringUtils.isEmpty(configurationName)) {
				return null;
			}

			DeclarationSource source = declarationSource(call, configurationName);
			return source != null ? new GroovyDeclarationCall(call, source, configurationName) : null;
		}

		private static @Nullable DeclarationSource declarationSource(GrMethodCall call, String configurationName) {

			// Only known declaration names can produce a declaration. Skip the parent
			// walks for everything else.
			if (!GradleUtils.isCatalogConsumerCall(configurationName)) {
				return null;
			}

			if (GroovyDslUtils.isInsidePluginsBlock(call)) {
				return DeclarationSource.plugin();
			}

			boolean dependency = GradleUtils.isDependencySection(configurationName);
			boolean platform = GradleUtils.isPlatformSection(configurationName)
					|| GroovyDslUtils.isInsidePlatformBlock(call);

			if (platform || dependency) {
				return platform ? DeclarationSource.bom() : DeclarationSource.dependency();
			}

			return null;
		}

		GrMethodCall getCall() {
			return call;
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
			return GroovyDslUtils.isGroovyCatalogConsumerCall(call.getCall());
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
			return resolved.getDeclaration()
					.mutate(it -> {
						it.declarationElement(call);
						if (declarationSource instanceof DeclarationSource.Bom) {
							it.declarationSource(declarationSource);
						}
					});
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

	/**
	 * Map-style declaration strategy. <pre class="code">
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * </pre>
	 */
	private class MapStyleStrategy implements ParsingStrategy<GroovyDeclarationCall, GrMethodCall> {

		@Override
		public boolean supports(GroovyDeclarationCall call) {
			return call.isDependency() && call.getCall().getNamedArguments().length > 1;
		}

		@Override
		public @Nullable ArtifactDeclaration parse(GrMethodCall call, DeclarationSource declarationSource) {
			DependencySite site = parseMapDependency(call, declarationSource);
			return site != null ? dependency(site) : null;
		}

		private @Nullable DependencySite parseMapDependency(GrMethodCall call, DeclarationSource declarationSource) {

			String group = null;
			String artifact = null;
			Expression version = null;
			PsiElement versionLiteral = null;

			for (GrNamedArgument argument : call.getNamedArguments()) {

				String key = argument.getLabelName();
				GrExpression value = argument.getExpression();

				if (GradleUtils.GROUP.equals(key)) {
					group = literalText(value);
				} else if (GradleUtils.NAME.equals(key)) {
					artifact = literalText(value);
				} else if (GradleUtils.VERSION.equals(key)) {
					version = GroovyDslUtils.toExpression(value);
					versionLiteral = value;
				}
			}

			if (version == null) {
				return null;
			}

			GradleDependency dependency = GradleDependency.fromNamed(group, artifact, version, declarationSource,
					propertyResolver);
			return dependency != null ? dependency.toDependencySite(call, versionLiteral) : null;
		}

		private @Nullable String literalText(@Nullable GrExpression value) {
			return value instanceof GrLiteral literal
					? propertyResolver.resolvePlaceholders(GroovyDslUtils.getText(literal))
					: null;
		}

	}

	/**
	 * Compact notation strategy, with the version either inline or in a
	 * {@code version { ... }} block. <pre class="code">
	 * implementation 'org.junit.jupiter:junit-jupiter:5.11.0'
	 * implementation platform('org.springframework.boot:spring-boot-dependencies:3.3.2')
	 * implementation('org.junit.jupiter:junit-jupiter') { version { prefer '5.11.0' } }
	 * </pre>
	 */
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

		private @Nullable DependencySite parseDependency(GrMethodCall call, DeclarationSource declarationSource) {

			for (PsiElement argument : call.getArgumentList().getAllArguments()) {

				// Unwrap platform()/enforcedPlatform() wrapper
				if (argument instanceof GrMethodCall innerCall
						&& GradleUtils.PLATFORM_FUNCTIONS.contains(GroovyDslUtils.getGroovyMethodName(innerCall))) {
					PsiElement[] innerArgs = innerCall.getArgumentList().getAllArguments();
					argument = innerArgs.length > 0 ? innerArgs[0] : null;
				}

				if (!(argument instanceof GrLiteral literal)) {
					continue;
				}

				GradleDependency dependency = GradleDependency.parse(GroovyDslUtils.getText(literal), declarationSource,
						propertyResolver);
				if (dependency == null) {
					continue;
				}

				if (dependency.getVersionSource().isDefined()) {
					return dependency.toDependencySite(call, literal);
				}

				GrExpression constraint = findVersionBlockConstraint(call);
				Expression version = GroovyDslUtils.toExpression(constraint);
				if (version != null) {
					return dependency.withVersion(version).toDependencySite(call, constraint);
				}

				return dependency.toDependencySite(call, literal);
			}

			GrReferenceExpression commandPlatformString = GroovyDslUtils.getCommandPlatformString(call);
			String gav = commandPlatformString != null ? commandPlatformString.getReferenceName() : null;
			if (StringUtils.isEmpty(gav)) {
				return null;
			}

			GradleDependency dependency = GradleDependency.parse(gav, declarationSource, propertyResolver);
			return dependency != null ? dependency.toDependencySite(call, commandPlatformString) : null;
		}

		/**
		 * Return the effective version argument of a {@code version { ... }} block. The
		 * strongest concrete constraint wins: {@code strictly}, then {@code require},
		 * then {@code prefer}. Range constraints are skipped because callers need a
		 * single upgradeable version value.
		 */
		private @Nullable GrExpression findVersionBlockConstraint(GrMethodCall call) {

			GrClosableBlock[] closures = call.getClosureArguments();
			if (closures.length == 0) {
				return null;
			}

			GrMethodCall versionCall = SyntaxTraverser.psiTraverser(closures[0]).filter(GrMethodCall.class)
					.filter(it -> GradleUtils.VERSION.equals(GroovyDslUtils.getGroovyMethodName(it)))
					.first();
			if (versionCall == null) {
				return null;
			}

			for (String name : GradleVersionConstraint.PRECEDENCE) {

				GrExpression argument = findConstraintArgument(versionCall, name);
				if (argument instanceof GrLiteral literal
						&& GradleUtils.isVersionRange(GroovyDslUtils.getText(literal))) {
					continue;
				}
				if (argument != null) {
					return argument;
				}
			}

			return null;
		}

		private @Nullable GrExpression findConstraintArgument(GrMethodCall versionCall, String constraint) {

			for (GrClosableBlock closure : versionCall.getClosureArguments()) {
				for (GrMethodCall constraintCall : SyntaxTraverser.psiTraverser(closure).filter(GrMethodCall.class)) {

					if (constraint.equals(GroovyDslUtils.getGroovyMethodName(constraintCall))) {
						GrExpression[] arguments = constraintCall.getExpressionArguments();
						return arguments.length > 0 ? arguments[0] : null;
					}
				}
			}

			return null;
		}

	}

}
