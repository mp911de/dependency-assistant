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
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.VersionedDependencySite;
import biz.paluch.dap.util.PsiVisitors;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * Updates a Gradle build file with a new version.
 *
 * @author Mark Paluch
 */
class UpdateGroovyDsl {

	private final GroovyLookupSiteLocator siteLocator;

	private final PropertyResolver propertyResolver;

	UpdateGroovyDsl(PropertyResolver propertyResolver) {
		this.propertyResolver = propertyResolver;
		this.siteLocator = new GroovyLookupSiteLocator(propertyResolver);
	}

	/**
	 * Update {@code ext.propertyKey = 'newVersion'},
	 * {@code set('propertyKey', 'newVersion')}, or a top-level
	 * {@code def propertyKey = 'newVersion'} script variable.
	 */
	void updateExtProperty(PsiFile file, String propertyKey, String newVersion) {

		file.accept(PsiVisitors.visitTreeUntil(GrLiteral.class, literal -> {

			GroovyExtAssignment assignment = GroovyExtAssignment.from(literal);
			if (assignment == null || !propertyKey.equals(assignment.getKey())) {
				return false;
			}

			GroovyDslUtils.updateText(assignment.getValueLiteral(), newVersion);
			return true;
		}));
	}

	/**
	 * Update the inline declaration for the given artifact, if present.
	 */
	public void updateDeclaration(PsiFile file, ArtifactId artifactId, String newVersion) {
		file.accept(PsiVisitors.visitTreeUntil(GrMethodCall.class, call -> {

			DependencySite site = siteLocator.locateDeclaration(call);

			if (!(site instanceof VersionedDependencySite versioned) || !site.getArtifactId()
					.equals(artifactId)) {
				return false;
			}

			if (versioned.getVersionElement() instanceof GrLiteral literal) {
				GradleUtils.updateVersion(GroovyDslUtils.getText(literal), newVersion,
						it -> GroovyDslUtils.updateText(literal, it));
				return true;
			}

			return false;
		}));
	}

}
