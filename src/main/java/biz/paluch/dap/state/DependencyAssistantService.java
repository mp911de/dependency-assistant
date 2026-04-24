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
 * Project-level service exposing persistent cache state and runtime dependency
 * state.
 * <p>The service has two distinct responsibilities:
 * <ul>
 * <li>persisting the durable {@link Cache} through IntelliJ's
 * {@link PersistentStateComponent} contract, and</li>
 * <li>holding in-memory {@link DependencyCollector dependency collectors} for
 * the projects currently analyzed in this IDE session.</li>
 * </ul>
 * Runtime dependency information is intentionally not part of the persisted
 * state and is lost when the IDE process is restarted.
 *
 * @author Mark Paluch
 */
@State(name = "DependencyAssistant", storages = @Storage("dependency-assistant.xml"), defaultStateAsResource = true)
public class DependencyAssistantService implements PersistentStateComponent<DependencyAssistantState> {

	private final DependencyAssistantState state = new DependencyAssistantState();

	private final Map<ProjectId, DependencyCollector> dependencies = new ConcurrentHashMap<>();

	/**
	 * Return the project-scoped service instance.
	 *
	 * @param project the IntelliJ project.
	 * @return the corresponding service instance.
	 */
	public static DependencyAssistantService getInstance(Project project) {
		return project.getService(DependencyAssistantService.class);
	}

	/**
	 * Return the state object managed by IntelliJ persistence.
	 *
	 * @return the persistent service state.
	 */
	@Override
	public DependencyAssistantState getState() {
		return state;
	}

	/**
	 * Return the persistent cache backing this service.
	 *
	 * @return the current cache instance.
	 */
	public Cache getCache() {
		return state.getCache();
	}

	/**
	 * Replace the persistent cache backing this service.
	 *
	 * @param cache the cache to store.
	 */
	public void setCache(Cache cache) {
		state.setCache(cache);
	}

	/**
	 * Copy the persisted state into this service instance.
	 *
	 * @param state the state loaded by IntelliJ persistence.
	 */
	@Override
	public void loadState(DependencyAssistantState state) {
		XmlSerializerUtil.copyBean(state, this.state);
	}

	/**
	 * Return a project-state facade for the given project identity.
	 * <p>The returned facade is backed by the current service instance and reflects
	 * subsequent cache or dependency updates.
	 *
	 * @param identity the project identity to expose.
	 * @return the corresponding project-state facade.
	 */
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

	}

}
