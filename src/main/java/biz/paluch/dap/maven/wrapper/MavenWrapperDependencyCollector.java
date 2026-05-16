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

import biz.paluch.dap.artifact.DependencyCollector;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiFile;

/**
 * Collects dependency coordinates from Maven wrapper files.
 *
 * @author Mark Paluch
 */
class MavenWrapperDependencyCollector {

	/**
	 * Collect artifact declarations from a Maven wrapper properties file.
	 *
	 * @param file the {@code maven-wrapper.properties} PSI file.
	 * @return a populated {@link DependencyCollector}.
	 */
	public DependencyCollector collect(PsiFile file) {

		DependencyCollector collector = new DependencyCollector();
		doCollect(file, collector);
		return collector;
	}

	/**
	 * Collect declarations from the given Maven wrapper properties PSI file into
	 * {@code collector}.
	 */
	protected void doCollect(PsiFile file, DependencyCollector collector) {

		if (MavenWrapperUtils.isWrapperFile(file) && file instanceof PropertiesFile propertiesFile) {
			MavenWrapperParser parser = new MavenWrapperParser(collector);
			parser.parse(propertiesFile);
		}
	}

}
