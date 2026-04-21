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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GradleParserSupport.NamedDependencyDeclaration;
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.support.PropertyResolver;
import com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

/**
 * Package-private semantic site contracts used to separate PSI navigation from
 * resolution and mutation.
 *
 * @author Mark Paluch
 */
interface GradleLookupSite {

	PsiElement declarationElement();

	static GradleVersionSite of(GradleDependency dependency, PsiElement declarationElement, PsiElement versionElement) {
		return new GradleVersionSite(dependency, declarationElement, versionElement);
	}

	static GradleLookupSite from(TomlReference reference, PsiElement declarationElement) {
		return new GradleCatalogReferenceSite(reference, declarationElement);
	}

	static GradlePropertySite property(String propertyName, String version, PsiElement declarationElement,
			PsiElement versionElement, @Nullable ArtifactId artifactId) {
		return new GradlePropertySite(propertyName, version, versionElement, declarationElement, artifactId);
	}

	/*
	 * static GradleLookupSite property(String propertyName, ArtifactId artifactId,
	 * String value, PsiElement declarationElement, PsiElement versionElement) {
	 * return new GradlePropertySite(propertyName, value, versionElement,
	 * declarationElement, artifactId); }
	 */

	/**
	 * Version-bearing dependency or plugin site whose dependency semantics are
	 * already known.
	 */
	record GradleVersionSite(GradleDependency dependency, PsiElement declarationElement, PsiElement versionElement)
			implements GradleLookupSite {

	}

}

/**
 * Property-backed version site.
 * <p>An explicit {@link ArtifactId} is optional. When present, resolution can
 * be performed without consulting project-wide property-to-artifact mappings.
 */
record GradlePropertySite(String propertyName, String version, PsiElement declarationElement,
		PsiElement versionElement, @Nullable ArtifactId artifactId) implements GradleLookupSite {

}

/**
 * Version-catalog accessor site such as {@code libs.foo.bar}.
 */
record GradleCatalogReferenceSite(TomlReference reference, PsiElement declarationElement)
		implements GradleLookupSite {

}

/**
 * Updateable declaration site carrying the concrete version PSI that should be
 * mutated for a declaration-scoped version change.
 */
record GradleDeclarationSite(ArtifactId artifactId, PsiElement declarationElement, PsiElement versionElement,
		VersionSource versionSource) {

	boolean matches(ArtifactId candidate) {
		return artifactId.equals(candidate);
	}

	boolean isUpdateable() {
		return !versionSource.isProperty();
	}

	boolean updateVersion(String newVersion) {

		if (!isUpdateable()) {
			return false;
		}

		return switch (versionElement) {
		case GrLiteral literal -> updateGroovyLiteral(literal, newVersion);
		case KtStringTemplateExpression template -> updateKotlinTemplate(template, newVersion);
		default -> false;
		};
	}

	static @Nullable GradleDeclarationSite from(GradleLookupSite.@Nullable GradleVersionSite versionSite) {

		if (versionSite == null || !versionSite.dependency().getVersionSource().isDefined()) {
			return null;
		}

		return new GradleDeclarationSite(versionSite.dependency().getId(), versionSite.declarationElement(),
				versionSite.versionElement(),
				versionSite.dependency().getVersionSource());
	}


	static @Nullable GradleDeclarationSite from(@Nullable DependencyAndVersionLocation location,
			PsiElement declarationElement) {

		if (location == null || !location.dependency().getVersionSource().isDefined()) {
			return null;
		}

		return new GradleDeclarationSite(location.artifactId(), declarationElement, location.version(),
				location.dependency().getVersionSource());
	}

	static @Nullable GradleDeclarationSite from(
			@Nullable NamedDependencyDeclaration declaration,
			PropertyResolver propertyResolver) {

		if (declaration == null || !declaration.isComplete()) {
			return null;
		}

		GradleDependency dependency = declaration.toDependency(propertyResolver);
		return new GradleDeclarationSite(dependency.getId(), declaration.declaration(),
				declaration.getRequiredVersionLiteral(), dependency.getVersionSource());
	}

	static @Nullable GradleDeclarationSite fromPlugin(ArtifactId artifactId, PsiElement declarationElement,
			PsiElement versionElement, String rawVersion) {

		if (artifactId == null) {
			return null;
		}

		GradleDependency dependency = GradleDependency.of(artifactId, PropertyExpression.from(rawVersion));
		return new GradleDeclarationSite(artifactId, declarationElement, versionElement,
				dependency.getVersionSource());
	}

	private static boolean updateGroovyLiteral(GrLiteral literal, String newVersion) {
		GroovyDslUtils.updateText(literal, getReplacementText(GroovyDslUtils.renderText(literal), newVersion));
		return true;
	}

	private static boolean updateKotlinTemplate(KtStringTemplateExpression template, String newVersion) {

		String text = KotlinDslUtils.getText(template);
		if (text == null) {
			return false;
		}

		template.updateText(getReplacementText(text, newVersion));
		return true;
	}

	private static String getReplacementText(String text, String newVersion) {
		String updated = GradleUtils.updateGavVersion(text, newVersion);
		return updated != null ? updated : newVersion;
	}

}
