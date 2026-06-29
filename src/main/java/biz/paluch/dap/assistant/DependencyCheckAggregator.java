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

package biz.paluch.dap.assistant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.ReleaseSources;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.rule.BranchSource;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleService;
import biz.paluch.dap.rule.ResolutionContext;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.upgrade.DependencyUpgradeSubject;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import biz.paluch.dap.upgrade.UpgradeSuggestionsFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import org.springframework.util.StringUtils;

/**
 * Aggregates dependency declarations found while scanning an upgrade scope.
 *
 * <p>The aggregator groups declarations by artifact coordinate, keeps every
 * declaration site for drift reporting, and retains the build files that
 * contributed to the final result.
 *
 * @author Mark Paluch
 */
class DependencyCheckAggregator implements Iterable<DependencyCheckAggregator.ArtifactPackage> {

	private final Map<ArtifactPackage, Entry> entries = new LinkedHashMap<>();

	private final Set<VirtualFile> files = new LinkedHashSet<>();

	private final Project project;

	private final StateService service;

	/**
	 * Create an aggregator for the given project state.
	 *
	 * @param project the IntelliJ project used for read actions.
	 * @param service the state service used to store scanned dependencies.
	 */
	public DependencyCheckAggregator(Project project, StateService service) {
		this.project = project;
		this.service = service;
	}

	/**
	 * Add a declared dependency and its declaration context.
	 *
	 * @param dependency the dependency declaration or usage to aggregate.
	 * @param context the project dependency context that produced the declaration.
	 * @param virtualFile the build file containing the declaration.
	 * @param releaseSources the release sources collected for the same scan.
	 */
	public void add(DeclaredDependency dependency, ProjectDependencyContext context, VirtualFile virtualFile,
			Collection<ReleaseSource> releaseSources) {

		files.add(virtualFile);
		ArtifactPackage pkg = new ArtifactPackage(dependency.getArtifactId(), context.getPackageSystem());
		Entry entry = entries.computeIfAbsent(pkg,
				it -> new Entry(new LinkedHashSet<>(), new ArrayList<>(), new ArrayList<>()));
		entry.releaseSources.addAll(releaseSources);
		entry.contexts.add(context);
		DeclarationSite site = new DeclarationSite(virtualFile, context.getProjectId(), dependency);
		entry.declarationSites.add(site);
	}

	/**
	 * Iterate over the unique artifacts in encounter order.
	 */
	@Override
	public Iterator<ArtifactPackage> iterator() {
		return entries.keySet().iterator();
	}

	/**
	 * Apply the given consumer to each unique artifact and its release sources.
	 *
	 * @param consumer the consumer receiving artifact identifiers and release
	 * sources in encounter order.
	 */
	public void forEachArtifact(BiConsumer<ArtifactPackage, Collection<ReleaseSource>> consumer) {
		entries.forEach((artifactId, entry) -> consumer.accept(artifactId, entry.releaseSources));
	}

	/**
	 * Return the build files that contributed declarations.
	 *
	 * @return the mutable set of scanned files in encounter order.
	 */
	public Set<VirtualFile> getFiles() {
		return files;
	}

	public List<ReleaseSources> getReleaseSources() {
		List<ReleaseSources> sources = new ArrayList<>();
		forEachArtifact((pkg, releaseSources) -> {
			sources.add(new ReleaseSources(pkg.artifactId(), pkg.packageSystem(), releaseSources));
		});
		return sources;
	}

	/**
	 * Merge the declaration and version sources of every site for the given
	 * artifact into a single declaration.
	 *
	 * @param artifactId the artifact represented by the merged declaration.
	 * @param entry the aggregated declaration data for the artifact.
	 * @return a new declaration carrying all known source locations.
	 */
	public DeclaredDependency mergeDeclarations(ArtifactId artifactId, Entry entry) {

		DeclaredDependency dependency = new DeclaredDependency(artifactId);

		entry.declarationSites().forEach(it -> {
			dependency.addAllDeclarationSources(it.dependency().getDeclarationSources());
			dependency.addAllVersionSources(it.dependency().getVersionSources());
		});

		return dependency;
	}

	/**
	 * Scan and add dependencies from the given upgrade-scope entry.
	 *
	 * @param entry the upgrade-scope entry to scan.
	 * @param indicator the progress indicator used for cancellation.
	 */
	public void add(UpgradeScope.Entry entry, ProgressIndicator indicator) {
		add(entry.buildFile().getVirtualFile(), entry.context(), indicator);
	}

	/**
	 * Scan and add dependencies from the given build file and project context.
	 *
	 * @param buildFile the build file to scan.
	 * @param context the project dependency context for the build file.
	 * @param indicator the progress indicator used for cancellation.
	 */
	public void add(VirtualFile buildFile, ProjectDependencyContext context, ProgressIndicator indicator) {

		ProjectState projectState = service.getProjectState(context.getProjectId());

		DependencyCollector collector = context.scanDependencies(indicator);
		projectState.setDependencies(collector, context.getPackageSystem());

		Collection<ReleaseSource> sources = collector.getReleaseSources();

		for (Dependency usage : collector.getUsages()) {
			add(usage, context, buildFile, sources);
		}

		for (DeclaredDependency declaration : collector.getDeclarations()) {
			if (declaration.hasDefinedVersion()) {
				add(declaration, context, buildFile, sources);
			}
		}
	}

	/**
	 * Create a dependency-check result from the resolved releases.
	 *
	 * <p>Artifacts without resolved releases are skipped. Release lookup errors are
	 * copied into the result so the UI can display them alongside the candidates
	 * that were resolved successfully.
	 *
	 * @param releases the resolved releases keyed by artifact.
	 * @return a new dependency-check result with candidates sorted by artifact.
	 */
	public DependencyUpgradeCandidates toDependencyCheckResult(Map<ArtifactId, ReleaseLookupResult> releases) {
		return toDependencyCheckResult(releases, DependencyRuleService.absent());
	}

	/**
	 * Create a dependency-check result from the resolved releases.
	 *
	 * <p>Artifacts without resolved releases are skipped. Release lookup errors are
	 * copied into the result so the UI can display them alongside the candidates
	 * that were resolved successfully.
	 *
	 * @param releases the resolved releases keyed by artifact.
	 * @param evaluator the rule service used to resolve governing dependency rules.
	 * @return a new dependency-check result with candidates sorted by artifact.
	 */
	public DependencyUpgradeCandidates toDependencyCheckResult(Map<ArtifactId, ReleaseLookupResult> releases,
			DependencyRuleService evaluator) {

		List<UpgradeCandidate> candidates = new ArrayList<>();
		List<String> errors = getErrors(releases);
		VulnerabilityScanner scanner = VulnerabilityScanner.create(project, service);
		UpgradeSuggestionsFactory suggestionsFactory = new UpgradeSuggestionsFactory(service.getCache());

		entries.forEach((pkg, entry) -> {

			ArtifactId artifactId = pkg.artifactId();
			ReleaseLookupResult lookup = releases.get(artifactId);
			if (lookup == null) {
				return;
			}

			DeclaredVersions declaredVersions = DeclaredVersions.from(entry.declarationSites(),
					it -> GitVersionResolver.resolveVersion(it, lookup.releases()), project);
			if (!declaredVersions.hasVersion()) {
				return;
			}

			Versioned versioned = Versioned.unversioned();
			for (ProjectDependencyContext context : entry.contexts()) {
				Versioned projectVersion = context.getProjectVersion();
				if (projectVersion.isVersioned()) {
					versioned = projectVersion;
					break;
				}
			}

			DeclaredDependency merged = mergeDeclarations(artifactId, entry);
			Dependency dependency = Dependency.from(merged, declaredVersions.getLowestDeclaredVersion());

			ResolutionContext resolutionContext = ResolutionContext.forAggregate(merged,
					BranchSource.of(entry.declarationSites().iterator().next().file()), versioned);
			DependencyRule rule = evaluator.resolve(resolutionContext);

			VulnerabilityRepository vulnerabilities = getVulnerabilities(pkg, scanner);

			DependencyUpgradeSubject subject = DependencyUpgradeSubject.of(dependency, lookup.releases(),
					vulnerabilities, rule);
			UpgradeSuggestions suggestions = suggestionsFactory.createSuggestions(subject);

			DependencyUpdateCandidate option = new DependencyUpdateCandidate(subject, suggestions);
			candidates.add(new UpgradeCandidate(option, entry.contexts().iterator().next()
					.getInterfaceAssistant(), declaredVersions));
		});

		candidates.sort(Comparator.comparing(UpgradeCandidate::getArtifactId, ArtifactId.BY_ARTIFACT_ID));

		return new DependencyUpgradeCandidates(UpgradeGroups.of(candidates), files, errors);
	}

	private VulnerabilityRepository getVulnerabilities(ArtifactPackage artifactPackage,
			VulnerabilityScanner scanner) {

		if (!scanner.isPresent()) {
			return VulnerabilityRepository.empty();
		}

		return VulnerabilityRepository
				.of(scanner.getVulnerabilities(artifactPackage.artifactId, artifactPackage.packageSystem));
	}

	private static List<String> getErrors(Map<?, ReleaseLookupResult> map) {

		List<String> errors = new ArrayList<>();
		map.values().forEach(r -> {
			if (StringUtils.hasText(r.error())) {
				errors.add(r.error());
			}
		});
		return errors;
	}

	record ArtifactPackage(ArtifactId artifactId, PackageSystem packageSystem) {

	}

	/**
	 * Aggregated scan data for one artifact coordinate.
	 *
	 * @param releaseSources the release sources that can resolve the artifact.
	 * @param contexts the project contexts in which the artifact was found.
	 * @param declarationSites the concrete declaration sites for drift reporting.
	 */
	record Entry(Collection<ReleaseSource> releaseSources, Collection<ProjectDependencyContext> contexts,
			Collection<DeclarationSite> declarationSites) {

	}


}
