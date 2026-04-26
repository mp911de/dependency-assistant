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

import java.util.HashMap;
import java.util.Map;

import biz.paluch.dap.DependencyAssistantFixtures;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.UpdatedBuildFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;

/**
 * Fixtures for Maven.
 *
 * @author Mark Paluch
 */
class MavenFixtures {

	/**
	 * Set up Dependency Assistant for the given project.
	 */
	static void setup(Project project) {
		DependencyAssistantFixtures.setup(project);
	}

	/**
	 * Analyze the given POM file and return the dependency collector.
	 */
	static DependencyCollector analyze(PsiFile file) {
		DependencyCollector collector = new DependencyCollector();
		new MavenParser(collector, new HashMap<>()).parsePomFile(new Cache(), (XmlFile) file);
		return collector;
	}

	/**
	 * Create an {@link UpdatedBuildFile} backed by fresh dependency analysis and
	 * property resolution for the given POM file.
	 */
	static UpdatedBuildFile updatedBuildFile(PsiFile file) {
		DependencyCollector collector = analyze(file);
		Map<String, String> properties = MavenParser.getProperties((XmlFile) file);
		PropertyResolver propertyResolver = properties::get;
		return UpdatedBuildFile.of(collector, propertyResolver, file.getName());
	}

}
