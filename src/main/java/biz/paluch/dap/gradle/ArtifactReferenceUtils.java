/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package biz.paluch.dap.gradle;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.gradle.GradleDependency.PropertyManagedDependency;
import biz.paluch.dap.gradle.GradleDependency.SimpleDependency;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PsiPropertyValueElement;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Utility methods for resolving Gradle {@link ArtifactReference}s.
 *
 * @author Mark Paluch
 */
class ArtifactReferenceUtils {

	/**
	 * Resolve the given {@link DependencyAndVersionLocation} to an
	 * {@link ArtifactReference}.
	 * @param location the dependency and version location.
	 * @param declaration the declaration (usage) element.
	 * @param propertyResolver property resolver to resolve property-managed
	 * dependencies.
	 * @return the resolved artifact reference.
	 */
	public static ArtifactReference resolve(DependencyAndVersionLocation location, PsiElement declaration,
			PropertyResolver propertyResolver) {

		if (location.dependency() instanceof PropertyManagedDependency managed) {

			PsiPropertyValueElement element = propertyResolver.getElement(managed.property());
			return fromPropertyManaged(managed, declaration, element == null ? null : element.propertyValue(),
					element == null ? null : element.element());
		}

		if (location.dependency() instanceof SimpleDependency simple) {
			return fromSimple(simple, declaration, location.version());
		}

		return ArtifactReference.unresolved();
	}

	/**
	 * Create a resolved {@link ArtifactReference} from a property-managed
	 * dependency.
	 */
	public static ArtifactReference fromPropertyManaged(PropertyManagedDependency managed,
			PsiElement declarationElement, @Nullable String resolvedVersion, @Nullable PsiElement versionPsi) {

		return ArtifactReference.from(it -> {
			it.artifact(managed.getId()).declarationElement(declarationElement)
					.versionSource(managed.getVersionSource());
			if (StringUtils.hasText(resolvedVersion)) {
				ArtifactVersion.from(resolvedVersion).ifPresent(it::version);
			}
			if (versionPsi != null) {
				it.versionLiteral(versionPsi);
			}
		});
	}

	/**
	 * Create a resolved {@link ArtifactReference} from a simple dependency.
	 */
	public static ArtifactReference fromSimple(SimpleDependency simple, PsiElement declarationElement,
			@Nullable PsiElement versionPsi) {

		return ArtifactReference.from(it -> {
			it.artifact(simple.getId()).declarationElement(declarationElement).versionSource(simple.getVersionSource());
			ArtifactVersion.from(simple.version()).ifPresent(it::version);
			if (versionPsi != null) {
				it.versionLiteral(versionPsi);
			}
		});
	}

}
