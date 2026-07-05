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
import java.util.function.Consumer;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.VulnerabilitiesRepository;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Predicates;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
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
@State(name = "DependencyAssistant", storages = @Storage("dependency-assistant.xml"))
public class StateService implements PersistentStateComponent<DependencyAssistantState>, VulnerabilitiesRepository {

	private final DependencyAssistantState state = new DependencyAssistantState();

	private final Map<ProjectId, DependencyCollector> dependencies = new ConcurrentHashMap<>();

	public StateService() {
	}

	public StateService(Cache cache) {
		setCache(cache);
	}

	/**
	 * Return the project-scoped service instance.
	 *
	 * @param project the IntelliJ project.
	 * @return the corresponding service instance.
	 */
	public static StateService getInstance(Project project) {
		return project.getService(StateService.class);
	}

	/**
	 * Return the state object managed by IntelliJ persistence.
	 * <p>The returned state is a snapshot that decouples platform serialization
	 * from concurrent cache mutations performed by background tasks.
	 *
	 * @return the persistent service state.
	 */
	@Override
	public DependencyAssistantState getState() {

		DependencyAssistantState snapshot = new DependencyAssistantState();
		snapshot.setCache(state.getCache().snapshot());
		snapshot.setUsedOnce(state.isUsedOnce());
		return snapshot;
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


	@Override
	public Vulnerabilities getVulnerabilities(PackageIdentity pkg, ArtifactVersion version) {
		return getVulnerabilities(pkg.getArtifactId(), version);
	}

	/**
	 * Return the {@link Vulnerabilities} for the given artifact and version.
	 * <p>For a Bill of Materials with cached membership, the result is a
	 * {@link BomAggregate} over the BOM itself and all used members whose effective
	 * version is managed by the BOM. Without vulnerable members, the artifact's own
	 * scan result is returned.
	 *
	 * @param artifactId the artifact to look up.
	 * @param artifactVersion the exact version whose scan is requested.
	 * @return the vulnerability scan.
	 */
	@Transient
	public Vulnerabilities getVulnerabilities(ArtifactId artifactId, ArtifactVersion artifactVersion) {

		Cache cache = getCache();
		Vulnerabilities vulnerabilities = cache.getVulnerabilities(artifactId, artifactVersion);

		CachedArtifact cachedArtifact = cache.findCachedArtifact(artifactId);
		if (cachedArtifact == null) {
			return vulnerabilities;
		}

		Map<ArtifactId, ArtifactVersion> bom = cachedArtifact.getBom(artifactVersion);
		if (bom.isEmpty()) {
			return vulnerabilities;
		}

		BomAggregate.Builder aggregate = BomAggregate.builder(artifactId)
				.member(artifactId, artifactVersion, vulnerabilities);

		doWithDependencies(dependency -> {

			ArtifactVersion managedVersion = bom.get(dependency.getArtifactId());
			if (managedVersion != null && managedVersion.equals(dependency.getCurrentVersion())) {
				aggregate.member(dependency.getArtifactId(), managedVersion,
						cache::getVulnerabilities);
			}
		});

		doWithDeclarations(declaration -> {

			if (declaration.hasDefinedVersion()) {
				return;
			}

			ArtifactVersion managedVersion = bom.get(declaration.getArtifactId());
			if (managedVersion != null) {
				aggregate.member(declaration.getArtifactId(), managedVersion,
						cache::getVulnerabilities);
			}
		});

		return aggregate.orElse(vulnerabilities);
	}

	/**
	 * Perform the given action for every dependency declared across all modules
	 * currently held in runtime dependency state.
	 *
	 * @param consumer the action invoked with each dependency declaration; must not
	 * be {@literal null}.
	 */
	public void doWithDependencies(Consumer<Dependency> consumer) {
		doWithDependencies(Predicates.alwaysTrue(), consumer);
	}

	/**
	 * Perform the given action for every dependency declared across the modules
	 * accepted by {@code projectFilter}.
	 * <p>The consumer is invoked once for each dependency in each accepted module,
	 * so an artifact declared by several modules is visited several times. Only the
	 * in-memory runtime dependency state is traversed; the persisted cache is not
	 * consulted.
	 *
	 * @param projectFilter selects which modules are traversed .
	 * @param consumer the action invoked with each dependency declaration; must not
	 * be {@literal null}.
	 */
	public void doWithDependencies(Predicate<ProjectId> projectFilter, Consumer<Dependency> consumer) {
		for (Map.Entry<ProjectId, DependencyCollector> entry : dependencies.entrySet()) {
			if (projectFilter.test(entry.getKey())) {
				for (Dependency dependency : entry.getValue().getUsages()) {
					consumer.accept(dependency);
				}
			}
		}
	}

	/**
	 * Perform the given action for every version-constraint declaration across all
	 * modules currently held in runtime dependency state.
	 * <p>The consumer is invoked once for each declaration in each module, so an
	 * artifact declared by several modules is visited several times. Only the
	 * in-memory runtime dependency state is traversed; the persisted cache is not
	 * consulted.
	 *
	 * @param consumer the action invoked with each declaration .
	 */
	public void doWithDeclarations(Consumer<DeclaredDependency> consumer) {
		doWithDeclarations(Predicates.alwaysTrue(), consumer);
	}

	/**
	 * Perform the given action for every version-constraint declaration across the
	 * modules accepted by {@code projectFilter}.
	 * <p>Only the in-memory runtime dependency state is traversed; the persisted
	 * cache is not consulted.
	 *
	 * @param projectFilter selects which modules are traversed .
	 * @param consumer the action invoked with each declaration .
	 */
	public void doWithDeclarations(Predicate<ProjectId> projectFilter, Consumer<DeclaredDependency> consumer) {
		for (Map.Entry<ProjectId, DependencyCollector> entry : dependencies.entrySet()) {
			if (projectFilter.test(entry.getKey())) {
				for (DeclaredDependency declaration : entry.getValue().getDeclarations()) {
					consumer.accept(declaration);
				}
			}
		}
	}

	/**
	 * Return whether this service currently knows dependencies or releases.
	 */
	public boolean hasDependenciesOrReleases() {
		return getCache().hasReleases() || getCache().hasDependencies();
	}

	/**
	 * Return {@literal true} if Dependency Assistant has been used actively.
	 */
	public boolean hasBeenUsed() {
		return state.isUsedOnce();
	}

	class DefaultProjectState implements ProjectState {

		private final ProjectId identity;

		/**
		 * Create a state facade for the given project identity.
		 */
		public DefaultProjectState(ProjectId identity) {
			this.identity = identity;
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
		public void setDependencies(DependencyCollector collector, PackageSystem packageSystem) {
			dependencies.put(identity, collector);
			getCache().getProject(identity).setProperties(collector, getCache().now());
			getCache().putBillOfMaterials(collector);
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
		public @Nullable VersionProperty findProperty(String propertyName, Predicate<VersionProperty> filter) {
			ProjectProperty projectProperty = findProjectProperty(propertyName, filter);
			return projectProperty != null ? projectProperty.property() : null;
		}

		@Override
		public @Nullable ProjectProperty findProjectProperty(String propertyName, Predicate<VersionProperty> filter) {
			return getCache().findProperty(propertyName, filter);
		}

	}

}
