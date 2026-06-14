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

import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import org.jspecify.annotations.Nullable;

/**
 * Properties declared in a single Maven pom file.
 *
 * @author Mark Paluch
 * @see PropertyResolver
 */
class MavenProperties implements PropertyResolver {

	private final String pomFile;

	private final PropertyResolver propertyResolver;

	MavenProperties(@Nullable PsiFile pomFile, PropertyResolver propertyResolver) {
		VirtualFile file = pomFile != null ? pomFile.getVirtualFile() : null;
		this.pomFile = file != null ? file.getPath() : (pomFile != null ? pomFile.getName() : "unknown");
		this.propertyResolver = propertyResolver;
	}

	/**
	 * Creater a new {@code MavenProperties} for the given {@link PsiFile pom file}.
	 * @param file the pom file to parse.
	 * @return {@code MavenProperties} declared within the given file.
	 */
	public static MavenProperties from(PsiFile file) {

		return CachedValuesManager.getProjectPsiDependentCache(file, it -> {

			if (MavenUtils.isMavenPomFile(it) && it instanceof XmlFile xmlFile) {
				PropertyResolver resolver = PropertyResolver.fromMap(MavenParser.parseProperties(xmlFile));
				return new MavenProperties(it, resolver);
			}

			return new MavenProperties(it, PropertyResolver.empty());
		});
	}

	@Override
	public boolean containsProperty(String key) {
		return propertyResolver.containsProperty(key);
	}

	@Override
	public @Nullable String getProperty(String key) {
		return propertyResolver.getProperty(key);
	}

	@Override
	public @Nullable PropertyValue getPropertyValue(String key) {
		return propertyResolver.getPropertyValue(key);
	}

	@Override
	public String toString() {
		return "MavenProperties{" +
				"pomFile='" + pomFile + '\'' +
				'}';
	}

}
