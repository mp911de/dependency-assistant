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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jspecify.annotations.Nullable;

/**
 * Project-level service.
 */
@State(name = "DependencyAssistant", storages = @Storage("dependency-assistant.xml"), defaultStateAsResource = true)
public class DependencyAssistantService implements PersistentStateComponent<DependencyAssistantState> {

	private final DependencyAssistantState state = new DependencyAssistantState();
	private final Map<ProjectId, DependencyCollector> dependencies = new ConcurrentHashMap<>();

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

	public ProjectState getProjectState(ProjectId identity) {
		return new DefaultProjectState(identity);
	}

	class DefaultProjectState implements ProjectState {

		private final ProjectId identity;
		private final ProjectCache projectCache;

		public DefaultProjectState(ProjectId identity) {
			this.identity = identity;
			this.projectCache = getCache().getProject(identity);
		}

		@Override
		public @Nullable Dependency findDependency(ArtifactId artifactId) {

			DependencyCollector dependencyCollector = dependencies.get(identity);
			if (dependencyCollector == null) {
				return null;
			}

			return dependencyCollector.getUsage(artifactId);
		}

		@Override
		public void setDependencies(DependencyCollector collector) {
			dependencies.put(identity, collector);
			projectCache.setProperties(collector);
		}

		@Override
		public boolean hasDependencies() {
			return dependencies.get(identity) != null;
		}

		@Override
		public void invalidateDependencies() {
			dependencies.remove(identity);
		}

		@Override
		public @Nullable Property findProperty(String propertyName, Predicate<Property> filter) {
			ProjectProperty projectProperty = findProjectProperty(propertyName, filter);
			return projectProperty != null ? projectProperty.property() : null;
		}

		@Override
		public @Nullable ProjectProperty findProjectProperty(String propertyName, Predicate<Property> filter) {
			return getCache().findProperty(propertyName, filter);
		}

		@Override
		public @Nullable ArtifactId findArtifactByPropertyName(String versionPropertyName) {

			Property property = findProperty(versionPropertyName);
			return property != null ? property.artifacts().getFirst().toArtifactId() : null;
		}

	}

}
