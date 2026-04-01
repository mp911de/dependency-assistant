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
package biz.paluch.dap.state;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.idea.maven.model.MavenId;
import org.jspecify.annotations.Nullable;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * Project-level service.
 */
@State(name = "DependencyAssistant", storages = @Storage("dependency-assistant.xml"), defaultStateAsResource = true)
public class DependencyAssistantService implements PersistentStateComponent<DependencyAssistantState> {

	private final DependencyAssistantState state = new DependencyAssistantState();
	private final Map<MavenId, DependencyCollector> dependencies = new ConcurrentHashMap<>();

	/**
	 * Returns the service instance for the given project.
	 */
	public static DependencyAssistantService getInstance(Project project) {
		return project.getService(DependencyAssistantService.class);
	}

	@Override
	public DependencyAssistantState getState() {
		return state;
	}

	public Cache getCache() {
		return state.getCache();
	}

	@Override
	public void loadState(DependencyAssistantState state) {
		XmlSerializerUtil.copyBean(state, this.state);
	}

	public ProjectState getProjectState(MavenId mavenId) {
		return new DefaultProjectState(mavenId);
	}

	public void setDependencies(MavenId mavenId, DependencyCollector collector) {
		dependencies.put(mavenId, collector);
	}

	class DefaultProjectState implements ProjectState {

		private final MavenId mavenId;
		private final ProjectCache projectCache;

		public DefaultProjectState(MavenId mavenId) {
			this.mavenId = mavenId;
			this.projectCache = getCache().getProject(mavenId);
		}

		@Override
		public @Nullable Dependency findDependency(ArtifactId artifactId) {

			DependencyCollector dependencyCollector = dependencies.get(mavenId);
			if (dependencyCollector == null) {
				return null;
			}

			return dependencyCollector.getDependency(artifactId);
		}

		@Override
		public void setDependencies(DependencyCollector collector) {
			dependencies.put(mavenId, collector);
			projectCache.setProperties(collector.getDependencies());
		}

		@Override
		public boolean hasDependencies() {
			return dependencies.get(mavenId) != null;
		}

		@Override
		public void invalidateDependencies() {
			dependencies.remove(mavenId);
		}

		@Override
		public @Nullable Property getProperty(String propertyName) {
			return projectCache.getProperty(propertyName);
		}
	}

}
