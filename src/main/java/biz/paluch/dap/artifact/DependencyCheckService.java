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

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.artifact.xml.PomDependency;
import biz.paluch.dap.artifact.xml.PomProfile;
import biz.paluch.dap.artifact.xml.PomProjection;
import biz.paluch.dap.artifact.xml.XmlBeamProjectorFactory;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.DependencyAssistantState;

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
import org.jetbrains.idea.maven.project.MavenProjectsTree;
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

	private final Project project;
	private final DependencyAssistantService cacheService;
	private final DependencyAssistantState state;

	public DependencyCheckService(Project project) {
		this.project = project;
		this.cacheService = DependencyAssistantService.getInstance(project);
		this.state = cacheService.getState();
	}

	public DependencyUpdates runCheck(ProgressIndicator indicator, String pomContent, @Nullable VirtualFile pomFile)
			throws IOException {

		String projectName = project.getName();
		indicator.setText(MessageBundle.message("action.check.dependencies.progress.collecting", projectName));

		if (pomFile == null) {
			return new DependencyUpdates(projectName, List.of(),
					List.of(MessageBundle.message("action.check.dependencies.noEditor")));
		}

		MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
		MavenProject mavenProject = projectsManager.findProject(pomFile);

		if (mavenProject == null) {
			return new DependencyUpdates(projectName, List.of(),
					List.of(MessageBundle.message("action.check.dependencies.noEditor")));
		}

		ArtifactCollector collector = new ArtifactCollector();
		ArtifactCollector nested = new ArtifactCollector();
		collectArtifacts(pomFile, pomContent, collector, nested, projectsManager);
		indicator.setFraction(0.3);

		if (collector.isEmpty()) {
			return new DependencyUpdates(projectName, List.of(),
					List.of(MessageBundle.message("action.check.dependencies.empty")));
		}

		List<RemoteRepository> repoUrls = collectRepositories(mavenProject);
		VersionResolver resolver = new VersionResolver(repoUrls);
		List<DependencyUpdateOption> items = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		ExecutorService executor = AppExecutorUtil.getAppExecutorService();
		List<Future<ResolverResult>> futures = new ArrayList<>();
		List<VersionCheckCandidate> tasks = new ArrayList<>(collector.getVersionCheckCandidates());
		for (VersionCheckCandidate task : tasks) {
			futures.add(executor.submit(() -> {
				try {
					List<VersionOption> versionOptions = state.getCache().getVersionOptions(task.getArtifactId(), true);

					if (CollectionUtils.isEmpty(versionOptions)) {
						versionOptions = resolver.getVersionSuggestions(task.getArtifactId(), task.getCurrentVersion());
						state.getCache().putVersionOptions(task.getArtifactId(), versionOptions);
					}

					return new ResolverResult(null, versionOptions);
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
			VersionCheckCandidate task = tasks.get(i);
			DependencyUpdateOption info = new DependencyUpdateOption(task, res.versionOptions);
			if (info.hasUpdateCandidate()) {
				items.add(info);
			}
		}

		state.getCache().setProperties(collector.getVersionCheckCandidates());
		state.getCache().recordUpdate();
		project.scheduleSave();
		items.sort(Comparator.comparing(DependencyUpdateOption::artifactId));

		return new DependencyUpdates(projectName, items, errors);
	}

	private void collectArtifacts(VirtualFile pomFile, String pomContent, ArtifactCollector collector,
			ArtifactCollector treeCollector, MavenProjectsManager projectsManager) throws IOException {

		MavenProject currentProject = projectsManager.findProject(pomFile);
		MavenProjectsTree projectsTree = projectsManager.getProjectsTree();

		PomProjection currentFileProjection = null;
		Map<MavenId, PomProjection> poms = new HashMap<>();
		for (MavenProject mavenProject : projectsTree.getProjects()) {

			PomProjection pomProjection;

			if (currentProject != null && currentProject.getMavenId().equals(mavenProject.getMavenId())) {
				pomProjection = project(pomContent);
			} else {
				pomProjection = project(mavenProject.getFile());
			}
			poms.put(mavenProject.getMavenId(), pomProjection);
		}

		AllProjects projects = AllProjects.of(poms);

		for (MavenProject mavenProject : projectsTree.getProjects()) {

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
			BiConsumer<biz.paluch.dap.artifact.ArtifactId, ArtifactUsage> callback) {

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
			callback.accept(new biz.paluch.dap.artifact.ArtifactId(g, a),
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

	private PomProjection project(VirtualFile file) throws IOException {
		return project(new String(file.contentsToByteArray()));
	}

	private List<RemoteRepository> collectRepositories(MavenProject mavenProject) {

		Set<RemoteRepository> urls = new LinkedHashSet<>();
		for (MavenRemoteRepository repository : mavenProject.getRemoteRepositories()) {
			String url = repository.getUrl();
			if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
				urls.add(new RemoteRepository(repository.getId(), url.endsWith("/") ? url : url + "/"));
			}
		}

		for (MavenRemoteRepository repository : mavenProject.getRemotePluginRepositories()) {
			String url = repository.getUrl();
			if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
				urls.add(new RemoteRepository(repository.getId(), url.endsWith("/") ? url : url + "/"));
			}
		}

		return new ArrayList<>(urls);
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
			BiConsumer<biz.paluch.dap.artifact.ArtifactId, ArtifactUsage> callback) {

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

		if (property.startsWith("${") && property.endsWith("}")) {
			property = property.substring(2, property.length() - 1);
		}

		PomProperty value = projects.getProperty(property);

		if (value != null) {

			if (value.containsProperty()) {
				return resolveProperty(value.value().trim(), projects);
			}

			return new PomProperty(property, value.value().trim(), VersionSource.property(property));
		}

		return null;
	}

	private @Nullable String resolvePropertyValue(String property, Projects projects) {

		Pattern pattern = Pattern.compile("\\$\\{(.*)\\}");
		Matcher matcher = pattern.matcher(property);
		String result = property;
		while (matcher.find()) {

			String name = matcher.group(1);
			PomProperty pomProperty = resolveProperty(name, projects);
			if (pomProperty != null) {
				result = matcher.replaceFirst(pomProperty.value);
				matcher = pattern.matcher(result);
			}
		}

		return result;
	}


	private record ResolverResult(@Nullable String error, List<VersionOption> versionOptions) {

	}

}
