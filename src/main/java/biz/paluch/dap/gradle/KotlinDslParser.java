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
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.KtValueArgumentList;
import org.jetbrains.kotlin.psi.ValueArgument;
import org.jspecify.annotations.Nullable;

/**
 * Parser for individual dependency declarations in a Kotlin DSL Gradle file.
 * Resolution collaborators and declaration-form strategies share the lifecycle
 * of one {@link KotlinDslFileParser}.
 *
 * @author Mark Paluch
 * @see ArtifactDeclaration
 */
class KotlinDslParser {

	private final PropertyResolver propertyResolver;

	private final VersionCatalogRegistry registry;

	private final List<ParsingStrategy<KotlinDeclarationCall, KtCallElement>> strategies;

	public KotlinDslParser(PropertyResolver propertyResolver, VersionCatalogRegistry registry) {
		this.propertyResolver = propertyResolver;
		this.registry = registry;
		this.strategies = List.of(new VersionCatalogStrategy(),
				new NamedArgumentsStrategy(), new PluginStrategy(),
				new InlineNotationStrategy());
	}

	/**
	 * Parse a Kotlin DSL declaration from the given call.
	 * <p>Supports declarations such as: <pre class="code">
	 * implementation("org.junit.jupiter:junit-jupiter:5.11.0")
	 * implementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.11.0")
	 * implementation("org.junit.jupiter:junit-jupiter") { version { prefer("5.11.0") } }
	 * id("org.springframework.boot") version "3.3.2"
	 * implementation(libs.spring.core)
	 * </pre>
	 * @param call the configuration call to parse.
	 * @return the parsed declaration, or {@literal null} when the call is not
	 * supported.
	 */
	@Nullable
	public ArtifactDeclaration parse(KtCallElement call) {

		KotlinDeclarationCall declarationCall = KotlinDeclarationCall.from(call);
		if (declarationCall == null) {
			return null;
		}

		for (ParsingStrategy<KotlinDeclarationCall, KtCallElement> strategy : strategies) {
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
	public TomlReference findCatalogReference(KtCallElement call) {
		KtDotQualifiedExpression accessor = findCatalogAccessor(call);
		if (accessor == null) {
			return null;
		}
		return TomlReference.from(getSegments(accessor), registry.catalogPaths().keySet());
	}

	private ArtifactDeclaration dependency(DependencySite site) {
		ArtifactReference artifactReference = ArtifactReferenceUtils.resolve(site, () -> propertyResolver);
		return artifactReference.getDeclaration();
	}

	private boolean isCatalogConsumer(KotlinDeclarationCall call) {
		return call.call instanceof KtCallExpression callExpression
				&& KotlinDslUtils.isCatalogConsumerCall(callExpression) && findCatalogAccessor(call.call) != null;
	}

	private static @Nullable KtDotQualifiedExpression findCatalogAccessor(KtCallElement call) {

		for (ValueArgument argument : call.getValueArguments()) {
			if (argument.getArgumentExpression() instanceof KtDotQualifiedExpression accessor) {
				return accessor;
			}
		}

		return null;
	}

	private static List<String> getSegments(KtDotQualifiedExpression dots) {
		return SyntaxTraverser.psiTraverser(dots)
				.expand(it -> !(it instanceof KtNameReferenceExpression))
				.filter(KtNameReferenceExpression.class)
				.map(KtNameReferenceExpression::getReferencedName)
				.toList();
	}

	/**
	 * Kotlin DSL configuration call classified for declaration parsing.
	 */
	static class KotlinDeclarationCall implements ConfigurationContext {

		private final KtCallElement call;

		private final DeclarationSource source;

		private final String configurationName;

		private KotlinDeclarationCall(KtCallElement call, DeclarationSource source, String configurationName) {
			this.call = call;
			this.source = source;
			this.configurationName = configurationName;
		}

		static @Nullable KotlinDeclarationCall from(@Nullable KtCallElement call) {

			// A call inside a string interpolation contributes a value and is never
			// a declaration call itself.
			if (call == null || PsiTreeUtil.getParentOfType(call, KtStringTemplateExpression.class) != null) {
				return null;
			}

			String configurationName = KotlinDslUtils.getKotlinCallName(call);
			if (StringUtils.isEmpty(configurationName)) {
				return null;
			}

			DeclarationSource source = declarationSource(call, configurationName);
			return source != null ? new KotlinDeclarationCall(call, source, configurationName) : null;
		}

		private static @Nullable DeclarationSource declarationSource(KtCallElement call, String configurationName) {

			// Only known declaration names can produce a declaration. Skip the parent
			// walks for everything else.
			if (!GradleUtils.isCatalogConsumerCall(configurationName)
					&& !GradlePluginId.isPluginCall(configurationName)) {
				return null;
			}

			boolean dependency = GradleUtils.isDependencySection(configurationName);
			boolean platform = GradleUtils.isPlatformSection(configurationName)
					|| KotlinDslUtils.isInsidePlatformBlock(call);
			boolean plugin = KotlinDslUtils.isInsidePluginsBlock(call);

			if (plugin) {
				return platform ? DeclarationSource.pluginManagement() : DeclarationSource.plugin();
			}

			if (platform || dependency) {
				return platform ? DeclarationSource.bom() : DeclarationSource.dependency();
			}

			return null;
		}

		public KtCallElement getCall() {
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

		public @Nullable ArtifactDeclaration parse(
				BiFunction<KtCallElement, DeclarationSource, @Nullable ArtifactDeclaration> parser) {
			return parser.apply(call, source);
		}

	}

	/**
	 * TOML/version catalog reference.
	 */
	class VersionCatalogStrategy implements ParsingStrategy<KotlinDeclarationCall, KtCallElement> {

		@Override
		public boolean supports(KotlinDeclarationCall call) {
			return isCatalogConsumer(call);
		}

		@Override
		public @Nullable ArtifactDeclaration parse(KtCallElement call, DeclarationSource declarationSource) {

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

	/**
	 * Named-argument declaration strategy (map-style).
	 */
	class NamedArgumentsStrategy implements ParsingStrategy<KotlinDeclarationCall, KtCallElement> {

		@Override
		public boolean supports(KotlinDeclarationCall call) {
			KtValueArgumentList arguments = call.call.getValueArgumentList();
			return arguments != null && arguments.getArguments().size() > 1;
		}

		@Override
		public @Nullable ArtifactDeclaration parse(KtCallElement call, DeclarationSource declarationSource) {

			DependencySite site = parseNamedArguments(call, declarationSource);
			return site != null ? dependency(site) : null;
		}

		private @Nullable DependencySite parseNamedArguments(KtCallElement call,
				DeclarationSource declarationSource) {

			String group = null;
			String artifact = null;
			Expression version = null;
			PsiElement versionLiteral = null;

			for (ValueArgument argument : call.getValueArguments()) {
				String name = argument.getArgumentName() != null
						? argument.getArgumentName().getAsName().asString()
						: null;
				KtExpression expression = argument.getArgumentExpression();

				if (GradleUtils.GROUP.equals(name)) {
					group = KtLiterals.from(expression).toString(propertyResolver);
				} else if (GradleUtils.NAME.equals(name)) {
					artifact = KtLiterals.from(expression).toString(propertyResolver);
				} else if (GradleUtils.VERSION.equals(name)) {
					KtLiterals literals = KtLiterals.from(expression);
					if (literals.hasText()) {
						version = literals.toExpression();
						versionLiteral = expression;
					}
				}
			}

			if (version == null) {
				return null;
			}

			GradleDependency dependency = GradleDependency.fromNamed(group, artifact, version, declarationSource,
					propertyResolver);
			return dependency != null ? dependency.toDependencySite(call, versionLiteral) : null;
		}

	}

	/**
	 * <p>Supports the conventional infix shape:
	 *
	 * <pre class="code">
	 * id("org.springframework.boot") version "3.3.2"
	 * </pre>
	 */
	class PluginStrategy implements ParsingStrategy<KotlinDeclarationCall, KtCallElement> {

		@Override
		public boolean supports(KotlinDeclarationCall call) {
			return call.isPlugin();
		}

		@Override
		public @Nullable ArtifactDeclaration parse(KtCallElement call, DeclarationSource declarationSource) {
			DependencySite site = fromBinary(call,
					PsiTreeUtil.getParentOfType(call, KtBinaryExpression.class), propertyResolver);

			return site != null ? dependency(site) : null;
		}

		/**
		 * Parse a Kotlin plugin declaration anchored at {@code call} and surrounded by
		 * {@code be}.
		 * @param call the inner {@code id(...)} call element.
		 * @param be the enclosing binary expression carrying the {@code version}
		 * keyword and version literal.
		 * @param scriptProperties property resolver used to resolve interpolated id
		 * placeholders.
		 * @return the parsed plugin declaration, or {@literal null}.
		 */
		private @Nullable DependencySite fromBinary(KtCallElement call, @Nullable KtBinaryExpression be,
				PropertyResolver scriptProperties) {

			String argument = KtLiterals.from(KotlinDslUtils.getFirstValueArgument(call)).toString(scriptProperties);
			if (!GradlePluginId.isValidPluginId(argument)) {
				return null;
			}

			KtStringTemplateExpression versionExpr = findVersionLiteral(be);
			KtLiterals literals = KtLiterals.from(versionExpr);
			if (!literals.hasText()) {
				return null;
			}

			GradlePluginId artifactId = GradlePluginId.fromCall(KotlinDslUtils.getKotlinCallName(call), argument);
			if (artifactId == null) {
				return null;
			}

			return GradleDependency.of(artifactId, literals.toExpression(), DeclarationSource.plugin())
					.toDependencySite(call, versionExpr);
		}

		private static @Nullable KtStringTemplateExpression findVersionLiteral(
				@Nullable KtBinaryExpression expression) {

			if (expression == null
					|| !GradleUtils.VERSION.equals(expression.getOperationReference().getReferencedName())) {
				return null;
			}

			return expression.getRight() instanceof KtStringTemplateExpression versionExpr ? versionExpr : null;
		}

	}

	/**
	 * Parse Kotlin DSL dependency declarations from compact notation, with the
	 * version either inline or in a {@code version { ... }} block.
	 * <p>Supports declarations such as: <pre class="code">
	 * implementation("org.junit.jupiter:junit-jupiter:5.11.0")
	 * implementation("org.junit.jupiter:junit-jupiter") { version { prefer("5.11.0") } }
	 * </pre> Named-argument and plugin forms are handled by
	 * {@link NamedArgumentsStrategy} and {@link PluginStrategy}.
	 */
	class InlineNotationStrategy implements ParsingStrategy<KotlinDeclarationCall, KtCallElement> {

		@Override
		public boolean supports(KotlinDeclarationCall call) {
			return call.isDependency();
		}

		@Override
		public @Nullable ArtifactDeclaration parse(KtCallElement call, DeclarationSource declarationSource) {
			DependencySite site = findDependencySite(call, declarationSource);
			return site != null ? dependency(site) : null;
		}

		private @Nullable DependencySite findDependencySite(KtCallElement call, DeclarationSource declarationSource) {

			GradleDependency dependency = null;
			KtStringTemplateExpression notation = null;

			for (ValueArgument argument : call.getValueArguments()) {
				KtExpression expression = argument.getArgumentExpression();

				// The nested platform call owns implementation(platform(...)).
				if (expression instanceof KtCallElement nested
						&& GradleUtils.isPlatformSection(KotlinDslUtils.getKotlinCallName(nested))) {
					return null;
				}

				if (expression instanceof KtStringTemplateExpression template) {
					dependency = GradleDependency.parse(KtLiterals.from(template).toString(), declarationSource,
							propertyResolver);
					if (dependency != null) {
						notation = template;
						break;
					}
				}
			}

			if (dependency == null) {
				return null;
			}

			if (dependency.getVersionSource().isDefined()) {
				return dependency.toDependencySite(call, notation);
			}

			KtVersion version = KtVersion.fromDependency(call);
			if (version == null) {
				return null;
			}

			return dependency.withVersion(version.getExpression()).toDependencySite(call, version.getElement());
		}

	}

}
