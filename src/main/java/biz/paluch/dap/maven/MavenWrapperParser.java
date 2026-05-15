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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.RemoteRepositoryReleaseSource;
import biz.paluch.dap.artifact.VersionSource;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * Parser for {@code .mvn/wrapper/maven-wrapper.properties}.
 * <p>Supports {@code distributionUrl} and {@code wrapperUrl} properties.
 *
 * @author Mark Paluch
 */
class MavenWrapperParser {

	private final DependencyCollector collector;

	public MavenWrapperParser() {
		this(new DependencyCollector());
	}

	public MavenWrapperParser(DependencyCollector collector) {
		this.collector = collector;
	}

	/**
	 * Parse the recognized properties from the given wrapper file.
	 * @param file the {@code maven-wrapper.properties} PSI file.
	 * @return the recognized entries, in declaration order; possibly empty.
	 */
	public List<WrapperEntry> parse(PsiFile file) {
		return file instanceof PropertiesFile propertiesFile ? getEntries(propertiesFile) : List.of();
	}

	/**
	 * Parse supported properties from the given wrapper {@link PropertiesFile} and
	 * register it in the {@link DependencyCollector}.
	 */
	public void parse(PropertiesFile propertiesFile) {

		Set<RemoteRepository> repositories = new HashSet<>();

		Properties.from(propertiesFile).filterMap(MavenWrapperParser::parse)
				.forEach(it -> {

					if (!it.hasConsistentVersions()) {
						return;
					}

					ArtifactVersion version = it.version();
					VersionSource versionSource = it.versionSource();
					collector.registerDeclaration(it.property().artifactId(), DeclarationSource.dependency(),
							versionSource);

					if (version != null) {
						collector.registerUsage(it.property().artifactId(), version, DeclarationSource.dependency(),
								versionSource);
					}

					repositories.add(it.repository());
				});

		repositories.forEach(it -> collector.addReleaseSource(new RemoteRepositoryReleaseSource(it)));
	}

	/**
	 * Parse supported properties from the given wrapper {@link PropertiesFile}.
	 * @param propertiesFile the wrapper properties file.
	 * @return the supported entries, in declaration order; possibly empty.
	 */
	public List<WrapperEntry> getEntries(PropertiesFile propertiesFile) {
		return Properties.from(propertiesFile).filterMap(MavenWrapperParser::parse).toList();
	}

	/**
	 * Attempt to parse a {@link IProperty} into an {@link WrapperEntry}.
	 */
	public static @Nullable WrapperEntry parse(IProperty property) {
		return WrapperProperty.parse(property);
	}

	/**
	 * Attempt to parse a {@link IProperty} into an {@link WrapperEntry} and invoke
	 * {@code entryConsumer} if successful.
	 */
	public static void parse(PropertyImpl property, Consumer<WrapperEntry> entryConsumer) {

		if (!property.getPsiElement().isValid()) {
			return;
		}

		for (WrapperProperty wp : WrapperProperty.values()) {
			WrapperEntry entry = wp.parseProperty(property);
			if (entry != null) {
				entryConsumer.accept(entry);
			}
		}
	}

}
