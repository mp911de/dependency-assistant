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
package biz.paluch.dap.artifact;

import biz.paluch.dap.MavenContext;
import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.artifact.xml.PomDependency;
import biz.paluch.dap.artifact.xml.PomProfile;
import biz.paluch.dap.artifact.xml.PomProjection;
import biz.paluch.dap.artifact.xml.XmlBeamProjectorFactory;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.reposearch.DependencySearchService;
import org.jspecify.annotations.Nullable;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;

/**
 * Service that runs dependency version check against Maven repositories.
 */
public class DependencyCheckService {

	private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]*)\\}");

	private final Project project;
	private final DependencySearchService searchService;
	private final MavenProjectsManager projectsManager;
	private final DependencyAssistantService service;

	public DependencyCheckService(Project project) {
		this.project = project;
		this.searchService = DependencySearchService.getInstance(project);
		this.projectsManager = MavenProjectsManager.getInstance(project);
		this.service = project.getService(DependencyAssistantService.class);
	}

	/**
	 * Returns the service instance for the given project.
	 */
	public static DependencyCheckService getInstance(Project project) {
		return project.getService(DependencyCheckService.class);
	}

	public DependencyUpdates runCheck(ProgressIndicator indicator, String pomContent, @Nullable VirtualFile pomFile)
			throws IOException {

		String projectName = project.getName();
		indicator.setText(MessageBundle.message("action.check.dependencies.progress.collecting", projectName));

		if (pomFile == null) {
			return new DependencyUpdates(projectName, List.of(),
					List.of(MessageBundle.message("action.check.dependencies.noEditor")));
		}
		MavenContext mavenContext = MavenContext.of(project, pomFile);

		if (!mavenContext.isAvailable()) {
			return new DependencyUpdates(projectName, List.of(),
					List.of(MessageBundle.message("action.check.dependencies.noEditor")));
		}

		DependencyCollector collector = collectArtifacts(pomContent, pomFile);
		indicator.setFraction(0.3);

		if (collector.isEmpty()) {
			return new DependencyUpdates(projectName, List.of(),
					List.of(MessageBundle.message("action.check.dependencies.empty")));
		}

		ProjectState projectState = service.getProjectState(mavenContext.getMavenId());

		ExecutorService executor = AppExecutorUtil.getAppExecutorService();
		Cache cache = service.getCache();
		List<ReleaseSource> sources = new ArrayList<>();
		sources.addAll(mavenContext.doWithMaven(this::collectRepositories));
		sources.add(new IndexReleaseSource(searchService));

		ReleaseResolver resolver = new ReleaseResolver(sources, executor);
		List<DependencyUpdateOption> items = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		List<Future<ResolverResult>> futures = new ArrayList<>();
		List<Dependency> tasks = new ArrayList<>(collector.getDependencies());
		for (Dependency task : tasks) {
			futures.add(executor.submit(() -> {
				try {

					List<Release> releases = cache.getReleases(task.getArtifactId(), true);

					if (CollectionUtils.isEmpty(releases)) {
						releases = resolver.getReleases(task.getArtifactId(), task.getCurrentVersion());
						cache.putVersionOptions(task.getArtifactId(), releases);
					}

					return new ResolverResult(null, releases);
				} catch (Exception e) {
					return new ResolverResult(task.getArtifactId() + ": " + e.getMessage(), List.of());
				}
			}));
		}

		for (int i = 0; i < futures.size(); i++) {
			indicator.checkCanceled();
			indicator.setFraction(0.5 + ((i + 1) / (double) futures.size()));
			indicator.setText2(tasks.get(i).getArtifactId().toString());
			ResolverResult res;
			try {
				res = futures.get(i).get();
			} catch (ExecutionException e) {
				String msg = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage()
						: e.getMessage();
				res = new ResolverResult(tasks.get(i).getArtifactId() + ": " + msg, List.of());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				res = new ResolverResult(tasks.get(i).getArtifactId() + ": " + e.getMessage(), List.of());
			}
			if (res.error != null) {
				errors.add(res.error);
			}
			Dependency task = tasks.get(i);
			DependencyUpdateOption info = new DependencyUpdateOption(task, res.releases);
			items.add(info);
		}

		projectState.setDependencies(collector);
		cache.recordUpdate();
		items.sort(Comparator.comparing(DependencyUpdateOption::getArtifactId, ArtifactId.BY_ARTIFACT_ID));

		return new DependencyUpdates(projectName, items, errors);
	}

	public DependencyCollector collectArtifacts(String pomContent, VirtualFile pomFile) {
		DependencyCollector collector = new DependencyCollector();
		DependencyCollector nested = new DependencyCollector();
		collectArtifacts(pomFile, pomContent, collector, nested, projectsManager);
		return collector;
	}

	private void collectArtifacts(VirtualFile pomFile, String pomContent, DependencyCollector collector,
			DependencyCollector treeCollector, MavenProjectsManager projectsManager) {

		MavenProject currentProject = projectsManager.findProject(pomFile);
		List<MavenProject> mavenProjects = projectsManager.getProjects();

		PomProjection currentFileProjection = null;
		Map<MavenId, PomProjection> poms = new HashMap<>();
		for (MavenProject mavenProject : mavenProjects) {

			PomProjection pomProjection;

			if (currentProject != null && currentProject.getMavenId().equals(mavenProject.getMavenId())) {
				pomProjection = project(pomContent);
			} else {
				pomProjection = project(mavenProject.getFile());
			}
			poms.put(mavenProject.getMavenId(), pomProjection);
		}

		AllProjects projects = AllProjects.of(poms);

		for (MavenProject mavenProject : mavenProjects) {

			PomProjection pomProjection = poms.get(mavenProject.getMavenId());

			ScopedProjects scoped = new ScopedProjects(mavenProject.getMavenId(), projects);
			if (currentProject != null && currentProject.getMavenId().equals(mavenProject.getMavenId())) {
				currentFileProjection = pomProjection;
				doWithArtifacts(scoped, pomProjection, collector::add);
			}

			doWithArtifacts(scoped, pomProjection, treeCollector::add);
		}

		if (currentFileProjection != null) {

			treeCollector.doWithArtifacts((artifactCoordinate, artifactUsage) -> {
				if (artifactUsage.version() instanceof VersionSource.VersionPropertySource vps) {

					PomProperty pomProperty = resolveProperty(vps.getProperty(), projects);
					if (pomProperty != null) {

						ArtifactVersion artifactVersion = ArtifactVersion.of(pomProperty.value());
						collector.registerUpdateCandidate(artifactCoordinate, artifactVersion, artifactUsage.declaration(),
								pomProperty.versionSource());
					}
				}
			});

			collector.doWithArtifacts((artifactCoordinate, artifactUsage) -> {

				if (artifactUsage.version().equals(VersionSource.none())) {
					return;
				}

				if (artifactUsage.version() instanceof VersionSource.VersionPropertySource vps) {

					PomProperty pomProperty = resolveProperty(vps.getProperty(), projects);
					if (pomProperty != null) {

						ArtifactVersion artifactVersion = ArtifactVersion.of(pomProperty.value());
						collector.registerUpdateCandidate(artifactCoordinate, artifactVersion, artifactUsage.declaration(),
								pomProperty.versionSource());
					}
				} else {

					ArtifactVersion artifactVersion = ArtifactVersion.of(artifactUsage.version().toString());
					collector.registerUpdateCandidate(artifactCoordinate, artifactVersion, artifactUsage.declaration(),
							VersionSource.declared(artifactUsage.declaration()));
				}
			});
		}
	}

	private void doWithDependency(Projects projects, PomDependency dependency, DeclarationSource declarationSource,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {

		String g = dependency.getGroupId();
		g = StringUtils.hasText(g) ? g : "org.apache.maven.plugins";
		String a = dependency.getArtifactId();

		if (a != null && a.contains("${")) {
			a = resolvePropertyValue(a, projects);
		}

		if (g.contains("${")) {
			g = resolvePropertyValue(g, projects);
		}

		VersionSource versionSource = getVersionSource(dependency.getVersion());
		if (a != null) {
			callback.accept(ArtifactId.of(g, a),
					new ArtifactUsage(declarationSource, versionSource));
		}
	}

	private VersionSource getVersionSource(@Nullable String version) {

		if (StringUtils.hasText(version)) {
			if (version.startsWith("${") && version.endsWith("}")) {
				version = version.substring(2, version.length() - 1);
				return VersionSource.property(version);
			}
			return VersionSource.declared(version);
		}

		return VersionSource.none();
	}

	private PomProjection project(String pomContent) {
		return XmlBeamProjectorFactory.INSTANCE.projectXMLString(pomContent, PomProjection.class);
	}

	private PomProjection project(VirtualFile file) {
		try {
			return project(new String(file.contentsToByteArray(), file.getCharset()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private List<ReleaseSource> collectRepositories(MavenProject mavenProject) {

		Map<String, RepositoryCredentials> credentials = SettingsXmlCredentialsLoader.load(project);

		Set<RemoteRepository> urls = new LinkedHashSet<>();
		for (MavenRemoteRepository repository : mavenProject.getRemoteRepositories()) {
			String url = repository.getUrl();
			if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
				String id = repository.getId();
				urls.add(new RemoteRepository(id, url.endsWith("/") ? url : url + "/", credentials.get(id)));
			}
		}

		for (MavenRemoteRepository repository : mavenProject.getRemotePluginRepositories()) {
			String url = repository.getUrl();
			if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
				String id = repository.getId();
				urls.add(new RemoteRepository(id, url.endsWith("/") ? url : url + "/", credentials.get(id)));
			}
		}

		return urls.stream().map(MavenRepositoryReleaseSource::new).map(it -> (ReleaseSource) it).toList();
	}

	interface Projects {

		PomProperty getProperty(String property);
	}

	record ProjectPom(PomProjection projection, Map<String, String> properties, List<Profile> profiles) {

		ProjectPom(PomProjection projection) {
			this(projection, projection.getProperties(),
					projection.getProfiles().stream().map(p -> new Profile(p.getId(), p.getProperties())).toList());
		}

		public PomProperty getProperty(String property) {

			if (properties.containsKey(property)) {
				return new PomProperty(property, properties.get(property), VersionSource.property(property));
			}

			for (Profile profile : profiles) {
				if (profile.properties().containsKey(property)) {
					return new PomProperty(property, profile.properties().get(property),
							VersionSource.profileProperty(profile.id(), property));
				}
			}

			return null;
		}
	}

	record Profile(String id, Map<String, String> properties) {
	}

	record AllProjects(Map<MavenId, ProjectPom> project) implements Projects {

		public static AllProjects of(Map<MavenId, PomProjection> projections) {

			Map<MavenId, ProjectPom> result = new HashMap<>();
			projections.forEach((id, projection) -> result.put(id, new ProjectPom(projection)));

			return new AllProjects(result);
		}

		public PomProperty getProperty(String property) {

			for (ProjectPom value : project.values()) {
				PomProperty pomProperty = value.getProperty(property);
				if (pomProperty != null) {
					return pomProperty;
				}
			}

			return null;
		}

	}

	record ScopedProjects(MavenId currentProject, Projects projects) implements Projects {

		public PomProperty getProperty(String property) {

			if (property.equals("artifactId") || property.equals("project.artifactId")) {
				return new PomProperty(property, currentProject().getArtifactId(), VersionSource.none());
			}

			if (property.equals("groupId") || property.equals("project.groupId")) {
				return new PomProperty(property, currentProject().getGroupId(), VersionSource.none());
			}

			if (property.equals("version") || property.equals("project.version")) {
				return new PomProperty(property, currentProject().getVersion(), VersionSource.none());
			}

			return projects.getProperty(property);
		}

	}

	private void doWithArtifacts(Projects projects, PomProjection pom,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {

		for (PomDependency dep : pom.getDependencyManagementDependencies()) {
			doWithDependency(projects, dep, DeclarationSource.managed(), callback);
		}

		for (PomDependency dep : pom.getDependencies()) {
			doWithDependency(projects, dep, DeclarationSource.dependency(), callback);
		}

		for (PomDependency plugin : pom.getBuildPluginManagementPlugins()) {
			doWithDependency(projects, plugin, DeclarationSource.pluginManagement(), callback);
		}

		for (PomDependency plugin : pom.getBuildPlugins()) {
			doWithDependency(projects, plugin, DeclarationSource.plugin(), callback);
		}

		List<PomProfile> profiles = pom.getProfiles();
		for (PomProfile profile : profiles) {

			String id = profile.getId();

			for (PomDependency dep : profile.getDependencyManagementDependencies()) {
				doWithDependency(projects, dep, DeclarationSource.profileManaged(id), callback);
			}

			for (PomDependency dep : profile.getDependencies()) {
				doWithDependency(projects, dep, DeclarationSource.profileDependency(id), callback);
			}

			for (PomDependency plugin : profile.getBuildPluginManagementPlugins()) {
				doWithDependency(projects, plugin, DeclarationSource.profilePluginManagement(id), callback);
			}

			for (PomDependency plugin : profile.getBuildPlugins()) {
				doWithDependency(projects, plugin, DeclarationSource.profilePlugin(id), callback);
			}
		}
	}

	record PomProperty(String name, String value, VersionSource versionSource) {
		public boolean containsProperty() {
			return value.contains("${");
		}
	}

	private @Nullable PomProperty resolveProperty(String property, Projects projects) {
		return resolveProperty(property, projects, new LinkedHashSet<>());
	}

	private @Nullable PomProperty resolveProperty(String property, Projects projects, Set<String> visited) {

		if (property.startsWith("${") && property.endsWith("}")) {
			property = property.substring(2, property.length() - 1);
		}

		if (!visited.add(property)) {
			return null;
		}

		PomProperty value = projects.getProperty(property);

		if (value != null) {

			if (value.containsProperty()) {
				return resolveProperty(value.value().trim(), projects, visited);
			}

			return new PomProperty(property, value.value().trim(), VersionSource.property(property));
		}

		return null;
	}

	private @Nullable String resolvePropertyValue(String property, Projects projects) {

		Matcher matcher = PROPERTY_PATTERN.matcher(property);
		String result = property;
		while (matcher.find()) {

			String name = matcher.group(1);
			PomProperty pomProperty = resolveProperty(name, projects);
			if (pomProperty != null) {
				result = matcher.replaceFirst(pomProperty.value);
				matcher = PROPERTY_PATTERN.matcher(result);
			}
		}

		return result;
	}


	private record ResolverResult(@Nullable String error, List<Release> releases) {

	}

}
