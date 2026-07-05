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

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jspecify.annotations.Nullable;

/**
 * Groovy DSL PSI locator for declaration-aware artifact lookups.
 *
 * @author Mark Paluch
 */
class GroovyArtifactReferenceLocator {

	private final PropertyResolver propertyResolver;

	private final @Nullable VersionCatalogRegistry registry;

	private final @Nullable ProjectState projectState;

	private @Nullable PsiFile cachedFile;

	private @Nullable GroovyDslFileParser cachedParser;

	GroovyArtifactReferenceLocator(PropertyResolver propertyResolver) {
		this(propertyResolver, null, null);
	}

	GroovyArtifactReferenceLocator(PropertyResolver propertyResolver, @Nullable VersionCatalogRegistry registry,
			@Nullable ProjectState projectState) {
		this.propertyResolver = propertyResolver;
		this.registry = registry;
		this.projectState = projectState;
	}

	/**
	 * Resolve the artifact reference owning the given Groovy PSI element.
	 * <p>Supports direct dependency literals, property-backed declarations, and
	 * version catalog references such as: <pre class="code">
	 * implementation 'org.springframework:spring-core:6.2.0'
	 * implementation "org.springframework:spring-core:$springVersion"
	 * ext.springVersion = '6.2.0'
	 * implementation libs.spring.core
	 * </pre>
	 *
	 * @param element the PSI element to inspect.
	 * @return the artifact reference, or an unresolved reference if no supported
	 * declaration can be derived.
	 */
	ArtifactReference locate(GroovyPsiElement element) {

		ArtifactReference catalogReference = locateCatalogReference(element);
		if (catalogReference.isResolved()) {
			return catalogReference;
		}

		if (element instanceof GrLiteral literal) {

			ArtifactReference propertySite = locatePropertyLiteral(literal);
			if (propertySite.isResolved()) {
				return propertySite;
			}

			ArtifactReference commandPlatformSite = locateCommandPlatformString(literal);
			if (commandPlatformSite.isResolved()) {
				return commandPlatformSite;
			}

			return resolveVersionSite(literal);
		}

		if (element instanceof GrReferenceExpression referenceExpression) {

			ArtifactReference commandPlatformSite = locateCommandPlatformString(referenceExpression);
			if (commandPlatformSite.isResolved()) {
				return commandPlatformSite;
			}

			return resolveVersionSite(referenceExpression);
		}

		return ArtifactReference.unresolved();
	}

	/**
	 * Resolve an element that occupies a recognized version position by delegating
	 * to the forward parser for the construct that owns it, so reverse lookup and
	 * dependency collection share one declaration model.
	 */
	private ArtifactReference resolveVersionSite(PsiElement element) {

		DeclarationStyle site = GroovyDeclarationStyleDetector.getInstance().detect(element);
		if (site.isAbsent() || !site.kind().isInlineInCall() || isExcludedVersionPosition(site, element)) {
			return ArtifactReference.unresolved();
		}

		return site.owningCall() instanceof GrMethodCall owner ? reference(parserFor(element).parse(owner))
				: ArtifactReference.unresolved();
	}

	/**
	 * Whether the element is a version position that does not resolve to a
	 * navigable version: a {@code strictly} version range, whose resolved version
	 * comes from a sibling {@code prefer} or is rejected outright, or a property
	 * reference nested inside a string interpolation, where the enclosing
	 * interpolated string is the resolving element instead.
	 */
	private static boolean isExcludedVersionPosition(DeclarationStyle site, PsiElement element) {

		if (site.kind() == DeclarationStyle.Kind.VERSION_BLOCK_STRICTLY && element instanceof GrLiteral literal
				&& GradleUtils.isVersionRange(GroovyDslUtils.getText(literal))) {
			return true;
		}

		return element instanceof GrReferenceExpression
				&& PsiTreeUtil.getParentOfType(element, GrStringInjection.class) != null;
	}

	/**
	 * Locate an {@link ArtifactDeclaration} from a Groovy dependency or plugin
	 * call.
	 * <p>Supports direct string notation, named-argument declarations, version
	 * blocks, and plugin declarations such as: <pre class="code">
	 * implementation 'org.junit.jupiter:junit-jupiter:5.11.0'
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * implementation('org.junit.jupiter:junit-jupiter') { version { prefer '5.11.0' } }
	 * id 'org.springframework.boot' version '3.3.2'
	 * </pre>
	 *
	 * @param call the method call to inspect.
	 * @return the artifact declaration, or {@literal null} if the call does not
	 * represent a supported declaration.
	 */
	@Nullable
	ArtifactDeclaration locateDeclaration(GrMethodCall call) {
		return parserFor(call).parse(call);
	}

	private ArtifactReference locatePropertyLiteral(GrLiteral literal) {

		Property property = GroovyExtAssignment.from(literal);
		if (property == null && propertyResolver instanceof GradlePropertyResolver resolver) {
			property = resolver.findBindingForValueLiteral(literal);
		}
		if (property == null) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReferenceUtils.resolve(property.getKey(), property.getValue(), literal,
				property.getValueLiteral(), projectState);
	}

	private ArtifactReference locateCommandPlatformString(PsiElement element) {

		GrMethodCall call = GroovyDeclarationStyleDetector.getInstance().findCommandPlatformDependencyCall(element);
		String text = GroovyDeclarationStyleDetector.getInstance().getCommandPlatformStringText(element);
		if (call == null || !StringUtils.hasText(text)) {
			return ArtifactReference.unresolved();
		}

		GradleDependency dependency = GradleDependency.parse(text, DeclarationSource.bom(), propertyResolver);
		if (dependency == null) {
			return ArtifactReference.unresolved();
		}

		PsiElement stringElement = GroovyDeclarationStyleDetector.getInstance().findCommandPlatformString(element);
		PsiElement versionElement = stringElement != null ? stringElement : element;
		return reference(dependency.toDependencySite(call, versionElement));
	}

	private ArtifactReference locateCatalogReference(PsiElement element) {

		if (registry == null) {
			return ArtifactReference.unresolved();
		}

		GrMethodCall catalogCall = GroovyDslUtils.findEnclosingGroovyCatalogAccessorCall(element);
		if (catalogCall == null) {
			return ArtifactReference.unresolved();
		}

		PsiElement argument = GroovyDslUtils.getFirstGroovyCatalogArgumentExpression(catalogCall);
		if (!(argument instanceof GrExpression expression)) {
			return ArtifactReference.unresolved();
		}

		List<String> segments = GroovyDslUtils.getVersionCatalogSegments(expression);
		if (segments.isEmpty() || !registry.containsAlias(segments.getFirst())) {
			return ArtifactReference.unresolved();
		}

		return reference(parserFor(catalogCall).parse(catalogCall));
	}

	private ArtifactReference reference(@Nullable DependencySite site) {
		return site != null ? ArtifactReferenceUtils.resolve(site, () -> propertyResolver)
				: ArtifactReference.unresolved();
	}

	private static ArtifactReference reference(@Nullable ArtifactDeclaration declaration) {
		return declaration != null ? ArtifactReference.from(declaration) : ArtifactReference.unresolved();
	}

	private GroovyDslFileParser parserFor(PsiElement element) {

		PsiFile file = element.getContainingFile();
		if (cachedParser == null || cachedFile != file) {
			cachedFile = file;
			cachedParser = new GroovyDslFileParser(file, propertyResolver,
					registry != null ? registry : VersionCatalogRegistry.from(file));
		}
		return cachedParser;
	}

}
