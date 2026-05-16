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

import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.ProjectBuildContext;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * {@link VersionUpgradeLookupSupport} implementation for Maven Wrapper
 * {@code distributionUrl} and {@code wrapperUrl} declarations.
 *
 * @author Mark Paluch
 */
class WrapperVersionUpgradeLookupService extends VersionUpgradeLookupSupport {

	/**
	 * Create a lookup service for the given project and build context.
	 * @param project the IntelliJ project; must not be {@literal null}.
	 * @param buildContext the wrapper-derived build context; must not be
	 * {@literal null}.
	 */
	WrapperVersionUpgradeLookupService(Project project, ProjectBuildContext buildContext) {
		super(project, buildContext);
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

		PropertyImpl property;
		PropertyValueImpl literal;

		if (element instanceof PropertyValueImpl propertyValue
				&& element.getParent() instanceof PropertyImpl propertyImpl) {
			property = propertyImpl;
			literal = propertyValue;
		} else if (element instanceof PropertyImpl propertyImpl) {
			property = propertyImpl;
			literal = PsiTreeUtil.findChildOfType(element, PropertyValueImpl.class);
		} else {
			return ArtifactReference.unresolved();
		}

		if (literal == null || !WrapperProperty.isWrapperProperty(property)
				|| !MavenWrapperUtils.isWrapperFile(element.getContainingFile())) {
			return ArtifactReference.unresolved();
		}

		WrapperEntry entry = MavenWrapperParser.parse(property);
		if (entry == null) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReference.from(builder -> builder.artifact(entry.property().artifactId())
				.versionSource(entry.versionSource())
				.declarationElement(literal)
				.versionLiteral(literal)
				.version(entry.version()));
	}

}
