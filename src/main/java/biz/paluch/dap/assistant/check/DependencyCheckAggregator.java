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
 * Dependency declarations grouped by package identity in encounter order.
 * <p>All declaration sites are retained for drift reporting.
 *
 * @author Mark Paluch
 */
public class DependencyCheckAggregator implements Sequence<PackageIdentity> {

	private final Map<PackageIdentity, Entry> entries = new LinkedHashMap<>();

	private final Set<VirtualFile> files = new LinkedHashSet<>();

	private final Project project;

	private final StateService stateService;

	private final ApplicationSettings settings;

	public DependencyCheckAggregator(Project project, StateService stateService, ApplicationSettings settings) {
		this.project = project;
		this.stateService = stateService;
		this.settings = settings;
	}

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

	@Override
	public Iterator<PackageIdentity> iterator() {
		return entries.keySet().iterator();
	}

	@Override
	public Stream<PackageIdentity> stream() {
		return entries.keySet().stream();
	}

	/**
	 * Visit each package and its release sources in encounter order.
	 */
	public void forEachArtifact(BiConsumer<PackageIdentity, Collection<ReleaseSource>> consumer) {
		entries.forEach((pkg, entry) -> consumer.accept(pkg, entry.releaseSources));
	}

	/**
	 * Visit each package and its declaration sites in encounter order.
	 */
	public void forEachDeclaration(BiConsumer<PackageIdentity, Collection<DeclarationSite>> consumer) {
		entries.forEach((pkg, entry) -> consumer.accept(pkg, entry.declarationSites()));
	}

	/**
	 * Return the live, mutable set of contributing files in encounter order.
	 */
	public Set<VirtualFile> getFiles() {
		return files;
	}

	/**
	 * Return release sources grouped by package in encounter order.
	 */
	public List<ReleaseSources> getReleaseSources() {
		List<ReleaseSources> sources = new ArrayList<>();
		forEachArtifact((pkg, releaseSources) -> {
			sources.add(ReleaseSources.of(pkg, releaseSources));
		});
		return sources;
	}

	/**
	 * Merge all declaration and version sources into one declaration.
	 */
	public DeclaredDependency mergeDeclarations(PackageIdentity pkg, Entry entry) {

		DeclaredDependency dependency = new DeclaredDependency(pkg);

		entry.declarationSites().forEach(it -> {
			dependency.addAllDeclarationSources(it.dependency().getDeclarationSources());
			dependency.addAllVersionSources(it.dependency().getVersionSources());
		});

		return dependency;
	}

	public void add(UpgradeScope.Entry entry, ProgressIndicator indicator) {
		add(entry.buildFile().getVirtualFile(), entry.context(), indicator);
	}

	/**
	 * Scan dependencies and replace the context's project dependency state.
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
	 * Load context release sources. Callers should invoke this outside the PSI read
	 * action.
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
	 * Create review candidates from release lookup results.
	 * <p>Failed lookups retain a candidate at its current version and contribute an
	 * error. Packages without lookup results or concrete versions are skipped.
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
	 * Return one collected current version per package for metadata indexing.
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

	record Entry(Collection<ReleaseSource> releaseSources, Collection<ProjectDependencyContext> contexts,
			Collection<DeclarationSite> declarationSites) {

	}


}
