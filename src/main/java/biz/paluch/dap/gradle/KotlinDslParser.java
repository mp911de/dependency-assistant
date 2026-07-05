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
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtLambdaArgument;
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

	KotlinDslParser(PropertyResolver propertyResolver, VersionCatalogRegistry registry) {
		this.propertyResolver = propertyResolver;
		this.registry = registry;
		this.strategies = List.of(new VersionCatalogStrategy(),
				new NamedArgumentsStrategy(), new PluginStrategy(),
				new InlineNotationStrategy(), new VersionBlockStrategy());
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
	ArtifactDeclaration parse(KtCallElement call) {

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
	TomlReference findCatalogReference(KtCallElement call) {

		KtDotQualifiedExpression accessor = findCatalogAccessor(call);
		if (accessor == null) {
			return null;
		}

		return TomlReference.from(getSegments(accessor), registry.catalogPaths().keySet());
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

	private class VersionCatalogStrategy implements ParsingStrategy<KotlinDeclarationCall, KtCallElement> {

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
			return declarationSource instanceof DeclarationSource.Bom
					? resolved.getDeclaration().at(call, declarationSource)
					: resolved.getDeclaration().at(call);
		}

	}

	private class NamedArgumentsStrategy implements ParsingStrategy<KotlinDeclarationCall, KtCallElement> {

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
			String version = null;
			String versionProperty = null;
			PsiElement versionLiteral = null;

			for (ValueArgument argument : call.getValueArguments()) {
				String name = argument.getArgumentName() != null
						? argument.getArgumentName().getAsName().asString()
						: null;
				PsiElement expression = argument.getArgumentExpression();
				String value = expression instanceof KtStringTemplateExpression template
						? KtLiterals.from(template).toString(propertyResolver)
						: null;

				if (GradleUtils.GROUP.equals(name)) {
					group = value;
				} else if (GradleUtils.NAME.equals(name)) {
					artifact = value;
				} else if (GradleUtils.VERSION.equals(name)) {
					if (expression instanceof KtStringTemplateExpression template) {
						KtLiterals literals = KtLiterals.from(template);
						if (literals.hasProperty() && propertyResolver.containsProperty(literals.getProperty())) {
							version = propertyResolver.getProperty(literals.getProperty());
							versionLiteral = expression;
							versionProperty = literals.getProperty();
						} else {
							version = literals.getText();
							versionLiteral = expression;
						}
					} else if (expression instanceof KtNameReferenceExpression reference) {
						String referenceName = reference.getReferencedName();
						Property property = propertyResolver.getPropertyValue(referenceName);
						if (property != null) {
							version = property.getValue();
							versionLiteral = property.getValueLiteral();
							versionProperty = referenceName;
						}
					}
				}
			}

			GradleDependency dependency = GradleDependency.fromNamed(group, artifact, versionProperty, version,
					declarationSource, propertyResolver);
			return dependency != null && versionLiteral != null ? dependency.toDependencySite(call, versionLiteral)
					: null;
		}

	}

	/**
	 * <p>Supports the conventional infix shape:
	 *
	 * <pre class="code">
	 * id("org.springframework.boot") version "3.3.2"
	 * </pre>
	 */
	private class PluginStrategy implements ParsingStrategy<KotlinDeclarationCall, KtCallElement> {

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

			String id = KtLiterals.from(call.getValueArgumentList()).toString(scriptProperties);
			if (StringUtils.isEmpty(id)) {
				return null;
			}

			KtStringTemplateExpression versionExpr = findVersionLiteral(be);
			if (versionExpr == null) {
				return null;
			}

			KtLiterals literals = KtLiterals.from(versionExpr);
			if (!literals.hasText()) {
				return null;
			}

			if (!GradlePluginId.isValidPluginId(id)) {
				return null;
			}

			String versionText = literals.toString();
			GradlePluginId artifactId = GradlePluginId.of(id);
			if (literals.hasProperty()) {
				return DependencySite.of(artifactId, VersionSource.property(literals.getProperty()),
						DeclarationSource.plugin(), call);
			}

			DependencySite dependencySite = DependencySite.of(artifactId, VersionSource.declared(versionText),
					DeclarationSource.plugin(), call);
			return ArtifactVersion.from(versionText)
					.map(it -> (DependencySite) dependencySite.withVersion(it, versionExpr))
					.orElse(dependencySite);
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
	 * Parse inline-notation Kotlin DSL dependency or plugin declarations whose
	 * version is part of the call itself.
	 * <p>Supports declarations such as: <pre class="code">
	 * implementation("org.junit.jupiter:junit-jupiter:5.11.0")
	 * id("org.springframework.boot") version "3.3.2"
	 * </pre> Named-argument and {@code version { ... }} block forms are handled by
	 * {@link NamedArgumentsStrategy} and {@link VersionBlockStrategy}.
	 */
	class InlineNotationStrategy implements ParsingStrategy<KotlinDeclarationCall, KtCallElement> {

		@Override
		public boolean supports(KotlinDeclarationCall call) {

			if (findInlineDependencyLiteral(call.getCall()) != null) {
				return true;
			}

			KtVersion version = KtVersion.fromDependency(call.getCall());
			return version != null && version.containsVersion();
		}

		private @Nullable KtStringTemplateExpression findInlineDependencyLiteral(KtCallElement call) {

			// todo: GradleDependency construction just for supports(…) case
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
					return findInlineDependencyLiteral(nested);
				}
			}

			return null;
		}

		@Override
		public @Nullable ArtifactDeclaration parse(KtCallElement call, DeclarationSource declarationSource) {
			DependencySite site = findDependencySite(call, declarationSource);
			return site != null ? dependency(site) : null;
		}

		private @Nullable DependencySite findDependencySite(KtCallElement call, DeclarationSource declarationSource) {

			KtStringTemplateExpression directNotation = findInlineDependencyLiteral(call);
			if (directNotation != null) {

				KtCallExpression nested = PsiTreeUtil.findChildOfType(call.getValueArgumentList(),
						KtCallExpression.class);
				KotlinDeclarationCall nestedCall = KotlinDeclarationCall.from(nested);

				// The nested platform call owns implementation(platform(...)).
				if (nestedCall != null && nestedCall.isDependency()) {
					return null;
				}

				String notation = KtLiterals.from(call.getValueArgumentList()).toString();
				return getDependencySite(call, declarationSource, notation, null, directNotation);
			}

			KtVersion version = KtVersion.fromDependency(call);
			if (version == null) {
				return null;
			}

			Expression expression = version.getVersionExpression();
			if (expression == null) {
				return null;
			}

			return getDependencySite(call, declarationSource, KtLiterals.from(call.getValueArgumentList()).toString(),
					expression, version.getVersionElement());
		}

		/**
		 * Create a {@link DependencySite} from parsed Kotlin DSL dependency data.
		 */
		private @Nullable DependencySite getDependencySite(KtCallElement call, DeclarationSource declarationSource,
				String gav, @Nullable Expression versionExpression, @Nullable PsiElement versionElement) {

			if (versionElement instanceof KtCallElement && declarationSource instanceof DeclarationSource.Plugin
					&& versionExpression != null) {

				if (!GradlePluginId.isValidPluginId(gav)) {
					return null;
				}

				GradleDependency dependency = GradleDependency.of(GradlePluginId.of(gav), versionExpression,
						declarationSource);
				return dependency.toDependencySite(call, versionElement);
			}

			GradleDependency dependency = GradleDependency.parse(gav, declarationSource);
			if (dependency == null) {
				return null;
			}

			if (versionExpression != null) {
				dependency = dependency.withVersion(versionExpression);
			}

			if (versionElement != null) {
				return dependency.toDependencySite(call, versionElement);
			}

			return dependency.toDependencySite(call);
		}

	}

	private class VersionBlockStrategy implements ParsingStrategy<KotlinDeclarationCall, KtCallElement> {

		@Override
		public boolean supports(KotlinDeclarationCall call) {
			return hasVersionBlock(call);
		}

		@Override
		public @Nullable ArtifactDeclaration parse(KtCallElement call, DeclarationSource declarationSource) {

			DependencySite site = parseVersionBlockDeclaration(call, declarationSource);
			return site != null ? dependency(site) : null;
		}

		@Nullable
		private DependencySite parseVersionBlockDeclaration(KtCallElement call,
				DeclarationSource declarationSource) {

			KtStringTemplateExpression gavTemplate = findArtifactLiteral(call);
			if (gavTemplate == null) {
				return null;
			}

			KtVersion version = KtVersion.fromDependency(call);
			if (version == null || version.getVersionExpression() == null || version.getVersionElement() == null) {
				return null;
			}

			String gavText = KtLiterals.from(gavTemplate).toString(propertyResolver);
			if (StringUtils.isEmpty(gavText) || !GradleArtifactId.isValid(gavText)) {
				return null;
			}

			ArtifactId artifactId = GradleArtifactId.from(gavText);
			return GradleDependency.of(artifactId, version.getVersionExpression(), declarationSource)
					.toDependencySite(call, version.getVersionElement());
		}

		private @Nullable KtStringTemplateExpression findArtifactLiteral(KtCallElement call) {

			for (ValueArgument argument : call.getValueArguments()) {
				if (argument instanceof KtLambdaArgument) {
					continue;
				}
				if (argument.getArgumentExpression() instanceof KtStringTemplateExpression expression) {
					String text = KtLiterals.from(expression).toString(propertyResolver);
					if (GradleArtifactId.isValid(text)) {
						return expression;
					}
				}
			}

			return null;
		}

	}

	private ArtifactDeclaration dependency(DependencySite site) {
		ArtifactReference artifactReference = ArtifactReferenceUtils.resolve(site, () -> propertyResolver);
		return artifactReference.getDeclaration();
	}

	private boolean isCatalogConsumer(KotlinDeclarationCall call) {
		return call.call instanceof KtCallExpression callExpression
				&& KotlinDslUtils.isCatalogConsumerCall(callExpression) && findCatalogAccessor(call.call) != null;
	}

	private boolean hasVersionBlock(KotlinDeclarationCall call) {

		if (!call.isDependency()) {
			return false;
		}

		for (ValueArgument argument : call.call.getValueArguments()) {
			if (argument instanceof KtLambdaArgument) {
				return true;
			}
		}

		return false;
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

}
