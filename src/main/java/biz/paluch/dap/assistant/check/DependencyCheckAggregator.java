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

package biz.paluch.dap.assistant.check;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.ReleaseSources;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.assistant.presentation.DependencyPresentationFactory;
import biz.paluch.dap.assistant.presentation.IconDependencyPresentation;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.metadata.ProjectMetadataService;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleService;
import biz.paluch.dap.rule.ResolutionContext;
import biz.paluch.dap.state.ApplicationSettings;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;


/**
 * Aggregates dependency declarations found while scanning an upgrade scope.
 *
 * <p>The aggregator groups declarations by {@link PackageIdentity}, keeps every
 * declaration site for drift reporting, combines release sources and project
 * contexts, and retains the build files that contributed to the final result.
 *
 * @author Mark Paluch
 */
public class DependencyCheckAggregator implements Sequence<PackageIdentity> {

	private final Map<PackageIdentity, Entry> entries = new LinkedHashMap<>();

	private final Set<VirtualFile> files = new LinkedHashSet<>();

	private final Project project;

	private final StateService stateService;

	private final ApplicationSettings settings;

	/**
	 * Create an aggregator for one project-scoped dependency check.
	 *
	 * @param project the project whose build files are scanned.
	 * @param stateService the project state and cache owner.
	 * @param settings the application settings used to derive candidate
	 * presentations.
	 */
	public DependencyCheckAggregator(Project project, StateService stateService, ApplicationSettings settings) {
		this.project = project;
		this.stateService = stateService;
		this.settings = settings;
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
		PackageIdentity pkg = PackageIdentity.of(dependency.getArtifactId(), context.getPackageSystem());
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
	public Iterator<PackageIdentity> iterator() {
		return entries.keySet().iterator();
	}

	/**
	 * Stream over the unique artifacts in encounter order.
	 */
	@Override
	public Stream<PackageIdentity> stream() {
		return entries.keySet().stream();
	}

	/**
	 * Apply the given consumer to each unique artifact and its release sources.
	 *
	 * @param consumer the consumer receiving package identities and release sources
	 * in encounter order.
	 */
	public void forEachArtifact(BiConsumer<PackageIdentity, Collection<ReleaseSource>> consumer) {
		entries.forEach((pkg, entry) -> consumer.accept(pkg, entry.releaseSources));
	}

	/**
	 * Apply the given consumer to each unique artifact and its declaration sites.
	 *
	 * @param consumer the consumer receiving package identities and declaration
	 * sites in encounter order.
	 */
	public void forEachDeclaration(BiConsumer<PackageIdentity, Collection<DeclarationSite>> consumer) {
		entries.forEach((pkg, entry) -> consumer.accept(pkg, entry.declarationSites()));
	}

	/**
	 * Return the build files that contributed declarations.
	 *
	 * @return the mutable set of scanned files in encounter order.
	 */
	public Set<VirtualFile> getFiles() {
		return files;
	}

	/**
	 * Return one release-source group per package identity.
	 *
	 * @return the release sources in package encounter order.
	 */
	public List<ReleaseSources> getReleaseSources() {
		List<ReleaseSources> sources = new ArrayList<>();
		forEachArtifact((pkg, releaseSources) -> {
			sources.add(ReleaseSources.of(pkg, releaseSources));
		});
		return sources;
	}

	/**
	 * Merge the declaration and version sources of every site for the given
	 * artifact into a single declaration.
	 *
	 * @param pkg the package identity represented by the merged declaration.
	 * @param entry the aggregated declaration data for the artifact.
	 * @return a new declaration carrying all known source locations.
	 */
	public DeclaredDependency mergeDeclarations(PackageIdentity pkg, Entry entry) {

		DeclaredDependency dependency = new DeclaredDependency(pkg);

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

		ProjectState projectState = stateService.getProjectState(context.getProjectId());

		DependencyCollector collector = context.scanDependencies(indicator);
		projectState.setDependencies(collector);

		Collection<ReleaseSource> sources = new LinkedHashSet<>(collector.getReleaseSources());

		for (Dependency usage : collector.getUsages()) {
			add(usage, context, buildFile, sources);
		}

		for (DeclaredDependency declaration : collector.getDeclarations()) {
			add(declaration, context, buildFile, sources);
		}
	}

	/**
	 * Load context-specific release sources after PSI scanning has left its read
	 * action. Each context is loaded once and its sources are shared by all
	 * artifact entries contributed by that context.
	 */
	public void addContextReleaseSources() {

		Map<ProjectDependencyContext, Collection<ReleaseSource>> sourcesByContext = new LinkedHashMap<>();
		for (Entry entry : entries.values()) {
			for (ProjectDependencyContext context : entry.contexts()) {
				sourcesByContext.computeIfAbsent(context, it -> new LinkedHashSet<>(it.getReleaseSources()));
			}
		}

		for (Entry entry : entries.values()) {
			for (ProjectDependencyContext context : entry.contexts()) {
				entry.releaseSources().addAll(sourcesByContext.get(context));
			}
		}
	}

	/**
	 * Create a dependency-check result from the resolved releases.
	 *
	 * <p>Packages absent from the lookup map and declarations without a concrete
	 * version are skipped. A failed lookup still produces a candidate anchored at
	 * its current version, and its error is copied into the result for the UI.
	 *
	 * @param releases the lookup results keyed by package identity.
	 * @param evaluator the rule service used to resolve governing dependency rules.
	 * @return a new dependency-check result with candidates sorted by artifact.
	 */
	public DependencyCheckResult toDependencyCheckResult(Map<PackageIdentity, ReleaseLookupResult> releases,
			DependencyRuleService evaluator) {

		ProjectMetadataService metadataService = ProjectMetadataService.getInstance(project);
		DependencyPresentationFactory presentationFactory = new DependencyPresentationFactory(metadataService,
				settings);
		List<DependencyUpgradeCandidate> upgrades = new ArrayList<>();
		List<String> errors = getErrors(releases);
		entries.forEach((pkg, entry) -> {

			ReleaseLookupResult lookup = releases.get(pkg);
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

			DeclaredDependency merged = mergeDeclarations(pkg, entry);
			Dependency dependency = Dependency.from(merged, declaredVersions.getLowestDeclaredVersion());

			ResolutionContext resolutionContext = ResolutionContext.forAggregate(merged,
					entry.declarationSites().iterator().next().file(), versioned);
			DependencyRule rule = evaluator.resolve(resolutionContext);

			VulnerabilityRepository vulnerabilities = version -> stateService.getVulnerabilities(pkg, version);
			ProjectDependencyContext next = entry.contexts().iterator().next();
			DependencyAssistant assistant = next.getAssistant();

			IconDependencyPresentation presentation = presentationFactory.create(dependency, rule,
					assistant.getInterfaceAssistant());

			DependencyUpgradeCandidate candidate = DependencyUpgradeCandidate.create(dependency, assistant,
					lookup.releases(), vulnerabilities, rule, presentation, declaredVersions);
			upgrades.add(candidate);
		});

		upgrades.sort(Comparator.comparing(DependencyUpgradeCandidate::getArtifactId, ArtifactId.BY_ARTIFACT_ID));

		return new DependencyCheckResult(upgrades, FileScope.of(new ArrayList<>(files)), errors);
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

	/**
	 * Return the collected current versions used for project metadata indexing.
	 *
	 * @return one current version per package identity.
	 */
	public Map<PackageIdentity, ArtifactVersion> getDependencyVersions() {
		Map<PackageIdentity, ArtifactVersion> versions = new HashMap<>();
		forEachDeclaration((packageIdentity, declarationSites) -> {

			for (DeclarationSite site : declarationSites) {
				if (site.dependency() instanceof Dependency dependency) {
					versions.put(packageIdentity, dependency.getCurrentVersion());
				}
			}
		});
		return versions;
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
