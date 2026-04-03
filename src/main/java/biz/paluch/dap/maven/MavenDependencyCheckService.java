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
package biz.paluch.dap.maven;

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactUsage;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdates;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.DependencyCheckSupport;
import biz.paluch.dap.xml.PomDependency;
import biz.paluch.dap.xml.PomProfile;
import biz.paluch.dap.xml.PomProjection;
import biz.paluch.dap.xml.XmlBeamProjectorFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Service that runs dependency version check against Maven repositories.
 */
public class MavenDependencyCheckService extends DependencyCheckSupport {

	private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]*)\\}");

	private final Project project;
	private final MavenProjectsManager projectsManager;

	public MavenDependencyCheckService(Project project) {
		super(project);
		this.project = project;
		this.projectsManager = MavenProjectsManager.getInstance(project);
	}

	/**
	 * Returns the service instance for the given project.
	 */
	public static MavenDependencyCheckService getInstance(Project project) {
		return project.getService(MavenDependencyCheckService.class);
	}

	public DependencyUpdates runCheck(ProgressIndicator indicator, PsiFile pomFile)
			throws IOException {

		String projectName = project.getName();
		indicator.setText(MessageBundle.message("action.check.dependencies.progress.collecting", projectName));

		MavenProjectContext mavenContext = MavenProjectContext.of(project, pomFile);

		if (!mavenContext.isAvailable()) {
			return new DependencyUpdates(projectName, List.of(),
					List.of(MessageBundle.message("maven.action.check.dependencies.noEditor")));
		}

		return getDependencyUpdates(indicator, pomFile, mavenContext);
	}

	@Override
	public DependencyCollector collectArtifacts(PsiFile pomFile) {
		DependencyCollector collector = new DependencyCollector();
		DependencyCollector nested = new DependencyCollector();
		collectArtifacts(pomFile, collector, nested, projectsManager);
		return collector;
	}

	private void collectArtifacts(PsiFile pomFile, DependencyCollector collector,
			DependencyCollector treeCollector, MavenProjectsManager projectsManager) {

		MavenProject currentProject = projectsManager.findProject(pomFile.getVirtualFile());
		List<MavenProject> mavenProjects = projectsManager.getProjects();

		PomProjection currentFileProjection = null;
		Map<MavenId, PomProjection> poms = new HashMap<>();
		for (MavenProject mavenProject : mavenProjects) {

			PomProjection pomProjection;

			if (currentProject != null && currentProject.getMavenId().equals(mavenProject.getMavenId())) {
				pomProjection = project(pomFile);
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

	private PomProjection project(PsiFile pomFile) {
		return XmlBeamProjectorFactory.INSTANCE.projectXMLString(pomFile.getText(), PomProjection.class);
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

	interface Projects {

		PomProperty getProperty(String property);

	}

	record ProjectPom(PomProjection projection, Map<String, String> properties, List<Profile> profiles) {

		ProjectPom(PomProjection projection) {
			this(projection, projection.getProperties(),
					projection.getProfiles().stream().map(p -> new Profile(p.getId(), p.getProperties())).toList());
		}

		public @Nullable PomProperty getProperty(String property) {

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
