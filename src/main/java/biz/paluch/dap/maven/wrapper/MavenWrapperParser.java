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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.MavenRepository;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.Properties;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
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
	 * Parse the supported properties from the given wrapper {@link PropertiesFile}.
	 * @param propertiesFile the wrapper properties file.
	 * @return the supported entries, in declaration order; possibly empty.
	 */
	public static List<WrapperEntry> parse(PropertiesFile propertiesFile) {
		return Properties.from(propertiesFile).filterMap(MavenWrapperParser::parse).toList();
	}

	/**
	 * Attempt to parse a {@link PropertyImpl} into a {@link WrapperEntry} by
	 * matching it against every supported wrapper URL property.
	 * @param property the property to parse.
	 * @return the parsed wrapper entry, or {@literal null} if no supported wrapper
	 * property matches or the value cannot be parsed.
	 */
	public static @Nullable WrapperEntry parse(PropertyImpl property) {

		if (!property.isValid()) {
			return null;
		}

		WrapperProperty wp = WrapperProperty.forKey(property.getUnescapedKey());
		return wp != null ? WrapperPropertyParser.parseProperty(wp, property) : null;
	}

	/**
	 * Parse supported properties from the given wrapper {@link PropertiesFile} and
	 * register them with the {@link DependencyCollector} passed at construction.
	 * @param propertiesFile the wrapper properties file.
	 */
	public void collect(PropertiesFile propertiesFile) {

		Set<RemoteRepository> repositories = new HashSet<>();

		for (WrapperEntry entry : parse(propertiesFile)) {

			if (!entry.hasConsistentVersions()) {
				continue;
			}

			ArtifactVersion version = entry.version();
			VersionSource versionSource = entry.versionSource();
			collector.registerDeclaration(entry.property().artifactId(), DeclarationSource.dependency(),
					versionSource);

			if (version != null) {
				collector.registerUsage(entry.property().artifactId(), version, DeclarationSource.dependency(),
						versionSource);
			}

			repositories.add(entry.repository());
		}

		repositories.forEach(it -> collector.addReleaseSource(new MavenRepository(it)));
	}

}
