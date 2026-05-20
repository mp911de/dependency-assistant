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

package biz.paluch.dap.gradle.wrapper;

import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.ProjectBuildContext;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * {@link VersionUpgradeLookupSupport} for Gradle wrapper
 * {@code distributionUrl}.
 *
 * @author Mark Paluch
 */
class WrapperVersionUpgradeLookupService extends VersionUpgradeLookupSupport {

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
				|| !GradleWrapperUtils.isWrapperFile(element.getContainingFile())) {
			return ArtifactReference.unresolved();
		}

		GradleWrapperEntry entry = GradleWrapperParser.parse(property);
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
