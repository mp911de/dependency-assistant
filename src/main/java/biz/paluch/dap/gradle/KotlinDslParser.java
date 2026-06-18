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
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.*;
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

			if (call == null) {
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
			boolean platform = GradleUtils.isPlatformSection(configurationName);
			boolean plugin = KotlinDslUtils.isInsidePluginsBlock(call);

			if (plugin) {
				return platform ? DeclarationSource.pluginManagement() : DeclarationSource.plugin();
			}

			if (platform || dependency) {
				return platform ? DeclarationSource.managed() : DeclarationSource.dependency();
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
			return resolved.isResolved() ? resolved.getDeclaration().at(call) : null;
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

			if (expression == null) {
				return null;
			}

			PsiElement[] children = expression.getChildren();
			for (int i = 0; i < children.length; i++) {
				PsiElement child = children[i];
				if (child instanceof KtOperationReferenceExpression ops
						&& GradleUtils.VERSION.equals(ops.getReferencedName())
						&& children.length > i + 1
						&& children[i + 1] instanceof KtStringTemplateExpression versionExpr) {
					return versionExpr;
				}
			}

			return null;
		}

	}

	/**
	 * Parse a supported Kotlin DSL dependency or plugin declaration.
	 * <p>Supports declarations such as: <pre class="code">
	 * implementation("org.junit.jupiter:junit-jupiter:5.11.0")
	 * implementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.11.0")
	 * implementation("org.junit.jupiter:junit-jupiter") { version { prefer("5.11.0") } }
	 * id("org.springframework.boot") version "3.3.2"
	 * </pre>
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

		/**
		 * Parse a version-block dependency, resolving bare version-property references
		 * against the file-scoped property resolver.
		 */
		@Nullable
		private DependencySite parseVersionBlockDeclaration(KtCallElement call,
				DeclarationSource declarationSource) {

			KtStringTemplateExpression gavTemplate = null;
			KtLambdaExpression trailingLambda = null;

			for (ValueArgument argument : call.getValueArguments()) {
				if (argument instanceof KtLambdaArgument lambdaArgument
						&& lambdaArgument.getArgumentExpression() instanceof KtLambdaExpression lambda) {
					trailingLambda = lambda;
					continue;
				}
				if (gavTemplate == null
						&& argument.getArgumentExpression() instanceof KtStringTemplateExpression expression) {
					String text = KtLiterals.from(expression).toString(propertyResolver);
					if (GradleArtifactId.isValid(text)) {
						gavTemplate = expression;
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
			for (ValueArgument argument : versionCall.getValueArguments()) {
				if (argument instanceof KtLambdaArgument lambdaArgument
						&& lambdaArgument.getArgumentExpression() instanceof KtLambdaExpression lambda) {
					versionLambda = lambda;
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
					for (ValueArgument argument : inner.getValueArguments()) {
						KtExpression expression = argument.getArgumentExpression();
						if (expression instanceof KtStringTemplateExpression template) {
							if (GradleVersionConstraint.PREFER.equals(name) && preferTemplate == null) {
								preferTemplate = template;
							} else if (GradleVersionConstraint.STRICTLY.equals(name) && strictlyTemplate == null) {
								strictlyTemplate = template;
							}
						} else if (expression instanceof KtNameReferenceExpression reference) {
							if (GradleVersionConstraint.PREFER.equals(name) && preferNameRef == null) {
								preferNameRef = reference;
							} else if (GradleVersionConstraint.STRICTLY.equals(name) && strictlyNameRef == null) {
								strictlyNameRef = reference;
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
				Property resolved = resolveVersionProperty(refName);
				if (resolved == null) {
					return null;
				}
				version = resolved.getValue();
				versionLiteralElement = resolved.getValueLiteral();
				versionProperty = refName;
			} else if (strictlyTemplate != null) {
				KtLiterals literals = KtLiterals.from(strictlyTemplate);
				String strictlyText = KtLiterals.from(strictlyTemplate).toString(propertyResolver);
				if (GradleUtils.isVersionRange(literals.toString())) {
					return null;
				}
				version = strictlyText;
				versionLiteralElement = strictlyTemplate;
				versionProperty = literals.hasProperty() ? literals.getProperty() : null;
			} else if (strictlyNameRef != null) {
				String refName = strictlyNameRef.getReferencedName();
				Property resolved = resolveVersionProperty(refName);
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

			Expression expression = StringUtils.hasText(versionProperty) ? Expression.property(versionProperty)
					: Expression.from(version);
			return GradleDependency.of(artifactId, expression, declarationSource)
					.toDependencySite(call, versionLiteralElement);
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

	private @Nullable Property resolveVersionProperty(String refName) {

		if (!StringUtils.hasText(refName)) {
			return null;
		}

		return propertyResolver.getPropertyValue(refName);
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
		KotlinDeclarationCall context = KotlinDeclarationCall.from(call);
		return context != null && context.isDependency() && isArgumentOfCall(literal, call)
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

	private static @Nullable KtCallElement findPluginIdCallForVersionBinary(KtBinaryExpression binary) {

		if (!GradleUtils.VERSION.equals(binary.getOperationReference().getReferencedName())) {
			return null;
		}

		KtExpression left = binary.getLeft();
		if (left instanceof KtCallElement call && KotlinDslUtils.isInsidePluginsBlock(call)) {
			return call;
		}

		if (left == null) {
			return null;
		}

		KtCallElement call = PsiTreeUtil.findChildOfType(left, KtCallElement.class);
		if (call != null && KotlinDslUtils.isInsidePluginsBlock(call)) {
			return call;
		}

		return null;
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
	private static @Nullable KtCallExpression findVersionBlockDependencyCall(KtCallExpression preferOrStrictlyCall) {

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
		KotlinDeclarationCall context = KotlinDeclarationCall.from(dependencyCall);
		if (context != null && context.isDependency()) {
			return dependencyCall;
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
	 * Return the property names referenced by a supported declaration anywhere in
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
				String name = reference.getReferencedName();
				if (StringUtils.hasText(name)) {
					names.add(name);
				}
			}
		}

		for (KtArrayAccessExpression arrayAccess : SyntaxTraverser.psiTraverser(file)
				.filter(KtArrayAccessExpression.class)) {
			if (isReferenceInsideSupportedVersionLiteral(arrayAccess)) {
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
		KotlinDeclarationCall declarationCall = KotlinDeclarationCall.from(call);
		return declarationCall != null && declarationCall.isDependency();
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
				.map(it -> KtLiterals.from(it).toString())
				.toList();
	}

}
