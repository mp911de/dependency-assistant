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

package biz.paluch.dap.maven.wrapper;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.lookup.ArtifactReferenceResolver;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.util.PropertyUtils;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;

/**
 * {@link ArtifactReferenceResolver} implementation for Maven Wrapper
 * {@code distributionUrl} and {@code wrapperUrl} declarations.
 *
 * <p>The wrapper version is parsed directly from the property value, so this
 * resolver is stateless and reads no project state.
 *
 * @author Mark Paluch
 */
class MavenWrapperArtifactReferenceResolver implements ArtifactReferenceResolver {

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

		Property property = PropertyUtils.findProperty(element);
		PsiElement literal = property != null ? PropertyUtils.findPropertyValue(property) : null;

		if (literal == null || !WrapperProperty.isWrapperProperty(property)
				|| !MavenWrapperUtils.isWrapperFile(element.getContainingFile())) {
			return ArtifactReference.unresolved();
		}

		WrapperEntry entry = MavenWrapperParser.parse(property);
		if (entry == null) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReference.from(builder -> builder.artifact(entry.property().artifactId())
				.packageSystem(PackageSystem.MAVEN)
				.declarationSource(DeclarationSource.dependency())
				.versionSource(entry.versionSource())
				.declarationElement(literal)
				.versionLiteral(literal)
				.version(entry.version()));
	}

}
