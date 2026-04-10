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

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.Cache;

import java.util.Map;

import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;

/**
 * Collects dependency coordinates from Maven build files associated.
 *
 * @author Mark Paluch
 */
class MavenDependencyCollector {

	private final Cache cache;
	private final Map<String, String> properties;

	public MavenDependencyCollector(Cache cache, Map<String, String> properties) {
		this.cache = cache;
		this.properties = properties;
	}

	/**
	 * Collects artifact declarations from {@code buildFile}.
	 *
	 * @param buildFile the POM file.
	 * @return a populated {@link DependencyCollector}
	 */
	public DependencyCollector collect(PsiFile buildFile) {

		DependencyCollector collector = new DependencyCollector();
		doCollect(buildFile, collector);
		return collector;
	}

	protected void doCollect(PsiFile psiFile, DependencyCollector collector) {

		if (MavenUtils.isMavenPomFile(psiFile) && psiFile instanceof XmlFile xmlFile) {
			MavenParser parser = new MavenParser(collector, properties);
			parser.parsePomFile(cache, xmlFile);
		}
	}

}
