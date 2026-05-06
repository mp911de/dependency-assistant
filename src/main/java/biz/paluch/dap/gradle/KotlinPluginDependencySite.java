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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jspecify.annotations.Nullable;

/**
 * Factory for {@link DependencySite} instances parsed from Kotlin DSL plugin
 * declarations.
 * <p>Supports the conventional infix shape:
 *
 * <pre class="code">
 * id("org.springframework.boot") version "3.3.2"
 * </pre>
 *
 * Where the {@code id(...)} call is the inner {@link KtCallElement} and the
 * surrounding {@link KtBinaryExpression} carries the {@code version} operation
 * and version string template.
 *
 * @author Mark Paluch
 */
class KotlinPluginDependencySite {

	private KotlinPluginDependencySite() {
	}

	/**
	 * Parse a Kotlin plugin declaration anchored at {@code call} and surrounded by
	 * {@code be}.
	 * @param call the inner {@code id(...)} call element.
	 * @param be the enclosing binary expression carrying the {@code version}
	 * keyword and version literal.
	 * @param scriptProperties property resolver used to resolve interpolated id
	 * placeholders.
	 * @return the parsed plugin declaration, or {@code null}.
	 */
	static @Nullable DependencySite fromBinary(KtCallElement call, @Nullable KtBinaryExpression be,
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
			return DependencySite.of(artifactId, VersionSource.property(literals.getProperty()), call);
		}

		DependencySite dependencySite = DependencySite.of(artifactId, VersionSource.declared(versionText), call);
		return ArtifactVersion.from(versionText)
				.map(it -> (DependencySite) dependencySite.withVersion(it, versionExpr))
				.orElse(dependencySite);
	}

	private static @Nullable KtStringTemplateExpression findVersionLiteral(@Nullable KtBinaryExpression be) {

		if (be == null) {
			return null;
		}

		PsiElement[] children = be.getChildren();
		for (int i = 0; i < children.length; i++) {
			PsiElement child = children[i];
			if (child instanceof KtOperationReferenceExpression ops && "version".equals(ops.getReferencedName())
					&& children.length > i + 1
					&& children[i + 1] instanceof KtStringTemplateExpression versionExpr) {
				return versionExpr;
			}
		}

		return null;
	}

}
