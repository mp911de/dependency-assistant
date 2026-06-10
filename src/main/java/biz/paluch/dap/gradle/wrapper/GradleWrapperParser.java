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

package biz.paluch.dap.gradle.wrapper;

import java.util.List;
import java.util.regex.Matcher;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GradleDistributionService;
import biz.paluch.dap.util.Properties;
import biz.paluch.dap.util.PropertyUtils;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import org.jspecify.annotations.Nullable;

/**
 * Parser for {@code gradle/wrapper/gradle-wrapper.properties}.
 *
 * @author Mark Paluch
 */
class GradleWrapperParser {

	private final DependencyCollector collector;

	GradleWrapperParser(DependencyCollector collector) {
		this.collector = collector;
	}

	static List<GradleWrapperEntry> parse(PropertiesFile propertiesFile) {
		return Properties.from(propertiesFile).filterMap(GradleWrapperParser::parse).toList();
	}

	static @Nullable GradleWrapperEntry parse(PropertyImpl property) {

		if (!property.isValid()) {
			return null;
		}

		WrapperProperty wp = WrapperProperty.forKey(property.getUnescapedKey());
		PropertyValueImpl value = PropertyUtils.findPropertyValue(property);

		if (wp == null || value == null || PropertyUtils.containsLineContinuation(value.getText())) {
			return null;
		}

		String decoded = property.getUnescapedValue();
		if (StringUtils.isEmpty(decoded)) {
			return null;
		}

		Matcher matcher = GradleWrapperUtils.GRADLE_DISTRIBUTION_PATTERN.matcher(decoded);
		if (!matcher.find()) {
			return null;
		}

		return new GradleWrapperEntry(wp, property, value, matcher.group("version"), matcher.group("flavor"));
	}

	void collect(PropertiesFile propertiesFile) {

		for (GradleWrapperEntry entry : parse(propertiesFile)) {

			ArtifactVersion version = entry.version();
			VersionSource versionSource = entry.versionSource();
			collector.registerDeclaration(entry.property().artifactId(), DeclarationSource.dependency(),
					versionSource);

			if (version != null) {
				collector.registerUsage(entry.property().artifactId(), version, DeclarationSource.dependency(),
						versionSource);
			}
		}

		collector.addReleaseSource(GradleDistributionService.INSTANCE);
	}

}
