/*
 * Copyright 2026-present the original author or authors.
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
import biz.paluch.dap.artifact.BillOfMaterials;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.VulnerabilitiesRepository;
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Predicates;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jspecify.annotations.Nullable;

/**
 * Project service for persistent metadata and runtime dependencies. Dependency
 * collectors last only for the current IDE session.
 *
 * @author Mark Paluch
 */
@State(name = "DependencyAssistant", storages = @Storage("dependency-assistant.xml"))
public class StateService
		implements PersistentStateComponentWithModificationTracker<DependencyAssistantState>,
		ModificationTracker, VulnerabilitiesRepository {

	private final DependencyAssistantState state = new DependencyAssistantState();

	private final Map<ProjectId, DependencyCollector> dependencies = new ConcurrentHashMap<>();

	public StateService() {
	}

	public StateService(Cache cache) {
		setCache(cache);
	}

	public static StateService getInstance(Project project) {
		return project.getService(StateService.class);
	}

	public Cache getCache() {
		return state.getCache();
	}

	public void setCache(Cache cache) {
		state.setCache(cache);
	}

	/**
	 * Snapshot persistent state so serialization does not traverse live cache
	 * collections.
	 */
	@Override
	public DependencyAssistantState getState() {

		DependencyAssistantState snapshot = new DependencyAssistantState();
		snapshot.setCache(state.getCache().snapshot());
		snapshot.setUsedOnce(state.isUsedOnce());
		return snapshot;
	}

	@Override
	public void loadState(DependencyAssistantState state) {
		XmlSerializerUtil.copyBean(state, this.state);
		this.state.postLoad();
	}

	@Override
	public long getModificationCount() {
		return getStateModificationCount();
	}

	@Override
	public long getStateModificationCount() {
		return state.getCache().getModificationCount();
	}

	/**
	 * Record that Dependency Assistant has been used actively.
	 */
	public void markUsed() {
		if (state.isUsedOnce()) {
			return;
		}
		state.setUsedOnce(true);
	}

	/**
	 * Return a view that reflects subsequent state updates.
	 */
	public ProjectState getProjectState(ProjectId identity) {
		return new DefaultProjectState(identity);
	}

	@Override
	public Vulnerabilities getVulnerabilities(PackageIdentity pkg, ArtifactVersion version) {
		return getVulnerabilities(pkg.getArtifactId(), version);
	}

	/**
	 * Return cached vulnerabilities, aggregating BOM advisories with advisories for
	 * used members at their managed versions. Missing memberships may be predicted
	 * by {@link CachedArtifact#predictBom}. Without vulnerable members, retain the
	 * artifact scan result.
	 */
	@Transient
	public Vulnerabilities getVulnerabilities(ArtifactId artifactId, ArtifactVersion artifactVersion) {

		Cache cache = getCache();
		Vulnerabilities vulnerabilities = cache.getVulnerabilities(artifactId, artifactVersion);

		CachedArtifact cachedArtifact = cache.findCachedArtifact(artifactId);
		if (cachedArtifact == null) {
			return vulnerabilities;
		}

		BillOfMaterials billOfMaterials = cachedArtifact.getBom(artifactVersion);
		if (billOfMaterials == null) {
			billOfMaterials = cachedArtifact.predictBom(artifactVersion);
		}
		if (billOfMaterials.isEmpty()) {
			return vulnerabilities;
		}

		Map<ArtifactId, ArtifactVersion> bom = billOfMaterials.getMembers();
		BomAggregate.Builder aggregate = BomAggregate.builder(artifactId)
				.member(artifactId, artifactVersion, vulnerabilities);

		for (DependencyCollector collector : dependencies.values()) {

			bom.forEach((memberId, managedVersion) -> {
				Dependency usage = collector.getUsage(memberId);
				if (usage != null && managedVersion.equals(usage.getCurrentVersion())) {
					aggregate.member(memberId, managedVersion, cache::getVulnerabilities);
					return;
				}

				DeclaredDependency declaration = collector.getDeclaration(memberId);
				if (declaration != null && !declaration.hasDefinedVersion()) {
					aggregate.member(memberId, managedVersion, cache::getVulnerabilities);
					return;
				}
			});
		}

		return aggregate.orElse(vulnerabilities);
	}

	/**
	 * Return an immutable map containing live collectors. This allows partial
	 * rescans to retain declarations from modules they did not visit.
	 */
	public Map<ProjectId, DependencyCollector> getCollectors() {
		return Map.copyOf(dependencies);
	}

	/**
	 * Visit runtime dependency usages across all modules.
	 */
	public void doWithDependencies(Consumer<Dependency> consumer) {
		doWithDependencies(Predicates.alwaysTrue(), consumer);
	}

	/**
	 * Visit runtime usages in selected modules. An artifact used in multiple
	 * modules is visited for each module.
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
	 * Visit each selected module's runtime usage of the artifact.
	 */
	public void doWithDependencies(ArtifactId artifactId, Predicate<ProjectId> projectFilter,
			Consumer<Dependency> consumer) {
		for (Map.Entry<ProjectId, DependencyCollector> entry : dependencies.entrySet()) {
			if (projectFilter.test(entry.getKey())) {

				Dependency dependency = entry.getValue().getUsage(artifactId);
				if (dependency != null) {
					consumer.accept(dependency);
				}
			}
		}
	}

	/**
	 * Visit resolved BOMs across runtime collectors.
	 */
	public void doWithBillOfMaterials(Consumer<BillOfMaterials> consumer) {
		doWithBillOfMaterials(Predicates.alwaysTrue(), consumer);
	}

	/**
	 * Visit resolved BOMs in selected runtime collectors. A BOM imported by
	 * multiple modules is visited for each module.
	 */
	public void doWithBillOfMaterials(Predicate<ProjectId> projectFilter, Consumer<BillOfMaterials> consumer) {
		for (Map.Entry<ProjectId, DependencyCollector> entry : dependencies.entrySet()) {
			if (projectFilter.test(entry.getKey())) {
				for (BillOfMaterials bom : entry.getValue().getBillOfMaterials()) {
					consumer.accept(bom);
				}
			}
		}
	}

	/**
	 * Whether the persistent cache contains projects or releases, regardless of
	 * runtime collector availability.
	 */
	public boolean hasDependenciesOrReleases() {
		return getCache().hasReleases() || getCache().hasDependencies();
	}

	public boolean hasBeenUsed() {
		return state.isUsedOnce();
	}

	class DefaultProjectState implements ProjectState {

		private final ProjectId identity;

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
		public void setDependencies(DependencyCollector collector) {

			dependencies.put(identity, collector);

			Cache cache = getCache();
			cache.getProject(identity).setProperties(collector, cache.now());
			for (BillOfMaterials bom : collector.getBillOfMaterials()) {
				cache.putBillOfMaterials(bom);
			}
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
		public void remove() {
			getCache().removeProject(identity);
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
