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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.Nullable;

/**
 * Utility to obtain Maven repositories.
 * @author Mark Paluch
 */
class MavenRepositories {

	/**
	 * Collect all remote repositories (dependency and plugin) from the given Maven
	 * project, decorated with credentials where available.
	 * <p>Repositories are deduplicated by URL.
	 *
	 * @param settings Maven settings; must not be
	 * {@literal null}.
	 * @param project the Maven project to inspect; must not be {@literal null}.
	 * @return the deduplicated set of remote repositories; guaranteed to be not
	 * {@literal null} but may be empty.
	 */
	public static Set<RemoteRepository> getRemoteRepositories(MavenSettings settings,
			MavenProject project, @Nullable PsiFile pomFile) {

		record RepositoryId(String id, String url) {
		}

		Set<RepositoryId> urls = new LinkedHashSet<>();

		forEach(project.getRemoteRepositories(),
				(id, url) -> urls.add(new RepositoryId(id, url)));
		forEach(project.getRemotePluginRepositories(),
				(id, url) -> urls.add(new RepositoryId(id, url)));

		if (pomFile != null) {
			List<MavenRemoteRepository> repositories = MavenParser.parseRepositories(pomFile);
			forEach(repositories, (id, url) -> urls.add(new RepositoryId(id, url)));
		}

		Set<RemoteRepository> remoteRepositories = new LinkedHashSet<>();

		for (RepositoryId url : urls) {
			RemoteRepository remoteRepository = settings.getRemoteRepository(url.id, url.url);
			remoteRepositories.add(remoteRepository);
		}

		return remoteRepositories;
	}

	private static void forEach(Collection<MavenRemoteRepository> repositories, BiConsumer<String, String> consumer) {
		for (MavenRemoteRepository repo : repositories) {

			String url = repo.getUrl();
			if (StringUtils.hasText(url) && (url.startsWith("http://") || url.startsWith("https://"))) {
				String repoId = repo.getId();
				String urlToUse = url.endsWith("/") ? url : url + "/";

				consumer.accept(repoId, urlToUse);
			}
		}
	}

	/**
	 * Collect release sources for all Maven sub-projects in the given project.
	 * <p>Loads credentials from {@code settings.xml}, then aggregates the remote
	 * repositories across every project known to {@link MavenProjectsManager},
	 * deduplicates them, and wraps each as a {@link ReleaseSource}.
	 *
	 * @param project the IntelliJ project; must not be {@literal null}.
	 * @return the aggregated release sources; guaranteed to be not {@literal null}
	 * but may be empty.
	 */
	public static List<ReleaseSource> getReleaseSources(Project project) {

		PsiManager psiManager = PsiManager.getInstance(project);
		MavenSettings settings = SettingsXmlLoader.load(project);
		Set<RemoteRepository> remoteRepositories = new LinkedHashSet<>();
		MavenProjectsManager manager = MavenProjectsManager.getInstance(project);

		for (MavenProject candidate : manager.getProjects()) {
			PsiFile pomFile = psiManager.findFile(candidate.getFile());
			remoteRepositories.addAll(getRemoteRepositories(settings, candidate, pomFile));
		}

		return ReleaseSource.getReleaseSources(remoteRepositories);
	}

}
