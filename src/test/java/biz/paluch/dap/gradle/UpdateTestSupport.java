/*
 * Copyright 2026-present the original author or authors.
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
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assertions.UpdatedBuildFile;
import biz.paluch.dap.assistant.review.TestFileUpdateDelegate;
import biz.paluch.dap.support.DependencyUpdate;
import com.intellij.psi.PsiFile;

/**
 * Factory for {@link UpdatedBuildFile} instances backed by Gradle-specific
 * dependency analysis and property resolution.
 *
 * @author Mark Paluch
 */
class UpdateTestSupport {

	/**
	 * Apply a plain dependency update without referencing the current version. The
	 * {@code fromVersion} is defaulted to {@code toVersion} as an inert
	 * placeholder; use the explicit {@code fromVersion} overload when assertions
	 * depend on the declared version (ranges, comparator pairs, dynamic
	 * constraints).
	 */
	static UpdatedBuildFile applyUpdate(PsiFile targetFile, String groupId, String artifactId,
			String toVersion) {

		return applyUpdate(targetFile, groupId, artifactId, DeclarationSource.dependency(),
				VersionSource.declared(toVersion), toVersion);
	}

	static UpdatedBuildFile applyPluginUpdate(PsiFile targetFile, String pluginId,
			String toVersion) {

		return applyUpdate(targetFile, pluginId, pluginId, DeclarationSource.plugin(),
				VersionSource.declared(toVersion), toVersion);
	}

	static UpdatedBuildFile applyPropertyUpdate(PsiFile targetFile, String groupId, String artifactId,
			String propertyName, String toVersion) {

		return applyUpdate(targetFile, groupId, artifactId, DeclarationSource.dependency(),
				VersionSource.property(propertyName), toVersion);
	}

	static UpdatedBuildFile applyUpdate(PsiFile targetFile, String groupId, String artifactId,
			DeclarationSource declarationSource, VersionSource versionSource, String toVersion) {

		ArtifactId id = ArtifactId.of(groupId, artifactId);
		ArtifactVersion updateTo = ArtifactVersion.of(toVersion);

		Dependency dependency = new Dependency(PackageIdentity.of(id, PackageSystem.MAVEN), updateTo);
		dependency.addDeclarationSource(declarationSource);
		dependency.addVersionSource(versionSource);

		DependencyUpdate update = DependencyUpdate.from(dependency, updateTo);

		new TestFileUpdateDelegate(targetFile.getProject(),
				(file, updates) -> new UpdateGradleFile(targetFile.getProject()).applyUpdates(targetFile, updates))
						.updateFile(targetFile.getVirtualFile(), update);
		return UpdateTestSupport.of(targetFile);
	}

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
