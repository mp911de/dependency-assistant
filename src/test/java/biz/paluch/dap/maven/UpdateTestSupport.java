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

package biz.paluch.dap.maven;

import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assertions.UpdatedBuildFile;
import biz.paluch.dap.support.BuildActionDelegate;
import biz.paluch.dap.support.PropertyResolver;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;

/**
 * Factory for {@link UpdatedBuildFile} instances backed by Maven-specific
 * dependency analysis and property resolution.
 *
 * @author Mark Paluch
 */
class UpdateTestSupport {

	public static UpdatedBuildFile applyUpdate(PsiFile targetFile, String groupId, String artifactId,
			String fromVersion,
			String toVersion) {

		return applyUpdate(targetFile, groupId, artifactId, fromVersion, DeclarationSource.dependency(),
				VersionSource.declared(fromVersion), toVersion);
	}

	public static UpdatedBuildFile applyUpdate(PsiFile targetFile, String groupId, String artifactId,
			String fromVersion,
			DeclarationSource declarationSource, VersionSource versionSource, String toVersion) {

		ArtifactId id = ArtifactId.of(groupId, artifactId);
		ArtifactVersion current = ArtifactVersion.of(fromVersion);
		ArtifactVersion updateTo = ArtifactVersion.of(toVersion);

		Dependency dep = new Dependency(id, current);
		dep.addDeclarationSource(declarationSource);
		dep.addVersionSource(versionSource);

		DependencyUpdate update = new DependencyUpdate(id, updateTo, dep.getDeclarationSources(),
				dep.getVersionSources());

		new BuildActionDelegate(targetFile.getProject(),
				(file, updates) -> new UpdatePomFile().applyUpdates(targetFile, updates),
				targetFile.getVirtualFile()).updateBuildFile(List.of(update));
		return UpdateTestSupport.of(targetFile);
	}

	/**
	 * Create an {@link UpdatedBuildFile} backed by fresh dependency analysis and
	 * local property resolution for the given Maven build file.
	 */
	static UpdatedBuildFile of(PsiFile file) {
		DependencyCollector collector = MavenFixtures.analyze(file);
		Map<String, String> properties = MavenParser.getProperties((XmlFile) file);
		PropertyResolver propertyResolver = properties::get;
		return UpdatedBuildFile.of(collector, propertyResolver, file.getName());
	}

}
