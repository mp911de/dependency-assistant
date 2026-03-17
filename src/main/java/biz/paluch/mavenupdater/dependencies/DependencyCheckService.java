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
package biz.paluch.mavenupdater.dependencies;

import biz.paluch.mavenupdater.MessageBundle;
import biz.paluch.mavenupdater.dependencies.xml.PomDependency;
import biz.paluch.mavenupdater.dependencies.xml.PomProfile;
import biz.paluch.mavenupdater.dependencies.xml.PomProjection;
import biz.paluch.mavenupdater.dependencies.xml.XmlBeamProjectorFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.Nullable;

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

	public DependencyCheckService(Project project) {
		this.project = project;
	}

	public DependencyUpgrades runCheck(ProgressIndicator indicator, String pomContent, @Nullable VirtualFile pomFile) {

		PomProjection pom = parsePom(pomContent);
		MavenProject mavenProject = (pomFile != null) ? MavenProjectsManager.getInstance(project).findProject(pomFile)
				: null;

		String projectName = project.getName();
		indicator.setText(MessageBundle.message("action.check.dependencies.progress.collecting", projectName));
		Map<ArtifactCoordinates, String> managedVersions = buildManagedVersionMap(pom);
		List<ArtifactTask> tasks = collectArtifactTasks(pom, managedVersions);

		if (tasks.isEmpty()) {
			return new DependencyUpgrades(projectName, List.of(), List.of(
					"No dependencies or plugins found in pom (dependencies, dependencyManagement, build/plugins, pluginManagement, or profiles)."));
		}

		List<String> repoUrls = collectRepositoryUrls(mavenProject);
		VersionResolver resolver = new VersionResolver(repoUrls);
		List<DependencyUpgrade> items = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		ExecutorService executor = AppExecutorUtil.getAppExecutorService();
		List<Future<ResolverResult>> futures = new ArrayList<>();
		for (ArtifactTask task : tasks) {
			futures.add(executor.submit(() -> {
				try {
					List<VersionOption> versionOptions = resolver.getVersionSuggestions(task.coord.groupId(),
							task.coord.artifactId(), task.currentVersion);
					return new ResolverResult(null, versionOptions);
				} catch (Exception e) {
					return new ResolverResult(task.coord + ": " + e.getMessage(), List.of());
				}
			}));
		}

		for (int i = 0; i < futures.size(); i++) {
			indicator.checkCanceled();
			indicator.setFraction((i + 1) / (double) futures.size());
			indicator.setText2(tasks.get(i).coord.toString());
			ResolverResult res;
			try {
				res = futures.get(i).get();
			} catch (ExecutionException e) {
				String msg = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage()
						: e.getMessage();
				res = new ResolverResult(tasks.get(i).coord + ": " + msg, List.of());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				res = new ResolverResult(tasks.get(i).coord + ": " + e.getMessage(), List.of());
			}
			if (res.error != null) {
				errors.add(res.error);
			}
			ArtifactTask task = tasks.get(i);
			DependencyUpgrade info = new DependencyUpgrade(task.coord, task.currentVersion, res.versionOptions, task.scope,
					task.source);
			if (info.hasUpgradeCandidate()) {
				items.add(info);
			}
		}

		items.sort(Comparator.comparing(DependencyUpgrade::coordinates));

		return new DependencyUpgrades(projectName, items, errors);
	}

	private Map<ArtifactCoordinates, String> buildManagedVersionMap(PomProjection pom) {
		Map<ArtifactCoordinates, String> map = new LinkedHashMap<>();
		List<PomDependency> dmDeps = pom.getDependencyManagementDependencies();
		for (PomDependency dep : dmDeps) {
			String g = dep.getGroupId();
			String a = dep.getArtifactId();
			String v = dep.getVersion();
			if (g != null && a != null && v != null) {
				map.put(new ArtifactCoordinates(g, a), v);
			}
		}

		List<PomProfile> profiles = pom.getProfiles();
		for (PomProfile profile : profiles) {
			List<PomDependency> profileDm = profile.getDependencyManagementDependencies();
			for (PomDependency dep : profileDm) {
				String g = dep.getGroupId();
				String a = dep.getArtifactId();
				String v = dep.getVersion();
				if (g != null && a != null && v != null) {
					map.put(new ArtifactCoordinates(g, a), v);
				}
			}
			List<PomDependency> profilePm = profile.getBuildPluginManagementPlugins();
			for (PomDependency plugin : profilePm) {
				String g = plugin.getGroupId() != null ? plugin.getGroupId() : "org.apache.maven.plugins";
				String a = plugin.getArtifactId();
				String v = plugin.getVersion();
				if (a != null && v != null) {
					map.put(new ArtifactCoordinates(g, a), v);
				}
			}
		}

		List<PomDependency> pmPlugins = pom.getBuildPluginManagementPlugins();
		for (PomDependency plugin : pmPlugins) {
			String g = plugin.getGroupId() != null ? plugin.getGroupId() : "org.apache.maven.plugins";
			String a = plugin.getArtifactId();
			String v = plugin.getVersion();
			if (a != null && v != null) {
				map.put(new ArtifactCoordinates(g, a), v);
			}
		}
		return map;
	}

	private List<ArtifactTask> collectArtifactTasks(PomProjection pom, Map<ArtifactCoordinates, String> managedVersions) {

		Set<ArtifactCoordinates> seen = new HashSet<>();
		List<ArtifactTask> tasks = new ArrayList<>();

		List<Pair<PomDependency, DependencySource>> pairs = new ArrayList<>();
		pairs.addAll(collectAllDependenciesWithSource(pom));
		pairs.addAll(collectAllPluginsWithSource(pom));

		for (Pair<PomDependency, DependencySource> entry : pairs) {
			PomDependency dep = entry.first;
			ArtifactCoordinates coord = new ArtifactCoordinates(dep.getGroupId(), dep.getArtifactId());
			if (seen.add(coord) && coord.hasArtifactId()) {
				String rawVersion = dep.getVersion() != null ? dep.getVersion() : managedVersions.get(coord);
				String currentVersion = resolveVersion(rawVersion, pom, new HashSet<>());
				tasks.add(new ArtifactTask(coord, dep.getScope() != null ? dep.getScope() : "", entry.second,
						currentVersion != null ? ArtifactVersion.of(currentVersion) : null));
			}
		}
		return tasks;
	}

	private List<Pair<PomDependency, DependencySource>> collectAllDependenciesWithSource(PomProjection pom) {
		List<Pair<PomDependency, DependencySource>> list = new ArrayList<>();
		List<PomDependency> deps = pom.getDependencies();
		for (PomDependency dep : deps) {
			list.add(new Pair<>(dep, DependencySource.Dependencies.INSTANCE));
		}

		List<PomDependency> dmDeps = pom.getDependencyManagementDependencies();
		for (PomDependency dep : dmDeps) {
			list.add(new Pair<>(dep, DependencySource.DependencyManagement.INSTANCE));
		}

		List<PomProfile> profiles = pom.getProfiles();
		for (PomProfile profile : profiles) {
			String profileId = profile.getId() != null ? profile.getId() : "?";
			List<PomDependency> profileDeps = profile.getDependencies();
			for (PomDependency dep : profileDeps) {
				list.add(new Pair<>(dep, new DependencySource.ProfileDependencies(profileId)));
			}
			List<PomDependency> profileDm = profile.getDependencyManagementDependencies();
			for (PomDependency dep : profileDm) {
				list.add(new Pair<>(dep, new DependencySource.ProfileDependencyManagement(profileId)));
			}
		}

		return list;
	}

	private List<Pair<PomDependency, DependencySource>> collectAllPluginsWithSource(PomProjection pom) {

		List<Pair<PomDependency, DependencySource>> list = new ArrayList<>();
		List<PomDependency> plugins = pom.getBuildPlugins();
		for (PomDependency plugin : plugins) {
			list.add(new Pair<>(plugin, DependencySource.Plugins.INSTANCE));
		}

		List<PomDependency> pmPlugins = pom.getBuildPluginManagementPlugins();
		for (PomDependency plugin : pmPlugins) {
			list.add(new Pair<>(plugin, DependencySource.PluginManagement.INSTANCE));
		}

		List<PomProfile> profiles = pom.getProfiles();
		for (PomProfile profile : profiles) {
			String profileId = profile.getId() != null ? profile.getId() : "?";
			List<PomDependency> profilePlugins = profile.getBuildPlugins();
			for (PomDependency plugin : profilePlugins) {
				list.add(new Pair<>(plugin, new DependencySource.ProfilePlugins(profileId)));
			}
			List<PomDependency> profilePm = profile.getBuildPluginManagementPlugins();
			for (PomDependency plugin : profilePm) {
				list.add(new Pair<>(plugin, new DependencySource.ProfilePluginManagement(profileId)));
			}
		}
		return list;
	}

	private PomProjection parsePom(String pomContent) {
		return XmlBeamProjectorFactory.INSTANCE.projectXMLString(pomContent, PomProjection.class);
	}

	private @Nullable String resolveVersion(String version, PomProjection pom, Set<String> visited) {
		if (!StringUtils.hasText(version) || "?".equals(version)) {
			return null;
		}
		if (!version.startsWith("${") || !version.endsWith("}")) {
			return version;
		}
		String key = version.substring(2, version.length() - 1);
		if (visited.contains(key)) {
			return null;
		}
		visited.add(key);
		try {

			String fromProperty = pom.getProperty(key);
			if (!StringUtils.hasText(fromProperty)) {

				for (PomProfile profile : pom.getProfiles()) {
					fromProperty = profile.getProperty(key);
					if (StringUtils.hasText(fromProperty)) {
						break;
					}
				}

			}

			if (fromProperty != null && !fromProperty.isBlank()) {
				String trimmed = fromProperty.trim();
				return trimmed.contains("${") ? resolveVersion(trimmed, pom, visited) : trimmed;
			}
			return null;
		} finally {
			visited.remove(key);
		}
	}

	private List<String> collectRepositoryUrls(MavenProject mavenProject) {

		Set<String> urls = new java.util.LinkedHashSet<>();
		for (MavenRemoteRepository repository : mavenProject.getRemotePluginRepositories()) {
			String url = repository.getUrl();
			if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
				urls.add(url.endsWith("/") ? url : url + "/");
			}
		}

		return new ArrayList<>(urls);
	}

	private record ArtifactTask(ArtifactCoordinates coord, String scope, DependencySource source,
			@Nullable ArtifactVersion currentVersion) {

	}

	private record ResolverResult(@Nullable String error, List<VersionOption> versionOptions) {

	}

	private static final class Pair<A, B> {

		final A first;

		final B second;

		Pair(A first, B second) {
			this.first = first;
			this.second = second;
		}

	}

}
