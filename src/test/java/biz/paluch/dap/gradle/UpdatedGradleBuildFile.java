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

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.support.UpdatedBuildFile;
import com.intellij.psi.PsiFile;

/**
 * Factory for {@link UpdatedBuildFile} instances backed by Gradle-specific
 * dependency analysis and property resolution.
 *
 * @author Mark Paluch
 */
class UpdatedGradleBuildFile {

	/**
	 * Create an {@link UpdatedBuildFile} backed by fresh dependency analysis and
	 * local property resolution for the given Gradle build file.
	 */
	static UpdatedBuildFile of(PsiFile file) {
		DependencyCollector collector = GradleFixtures.analyze(file);
		GradlePropertyResolver propertyResolver = GradlePropertyResolver.forFile(file);
		return UpdatedBuildFile.of(collector, propertyResolver, file.getName());
	}

}
