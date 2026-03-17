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
package biz.paluch.mavenupdater.dependencies.xml;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.xmlbeam.annotation.XBRead;

/**
 * XMLBeam projection for Maven POM (pom.xml).
 */
public interface PomProjection {

	@XBRead("/project/artifactId")
	@Nullable
	String getArtifactId();

	@XBRead("/project/name")
	@Nullable
	String getName();

	@XBRead("/project/properties/{0}")
	@Nullable
	String getProperty(String propertyName);

	@XBRead("/project/dependencies/dependency")
	List<PomDependency> getDependencies();

	@XBRead("/project/dependencyManagement/dependencies/dependency")
	List<PomDependency> getDependencyManagementDependencies();

	@XBRead("/project/profiles/profile")
	List<PomProfile> getProfiles();

	@XBRead("/project/build/plugins/plugin")
	List<PomDependency> getBuildPlugins();

	@XBRead("/project/build/pluginManagement/plugins/plugin")
	List<PomDependency> getBuildPluginManagementPlugins();

	@XBRead("/project/repositories/repository/url")
	List<String> getRepositoryUrls();

}
