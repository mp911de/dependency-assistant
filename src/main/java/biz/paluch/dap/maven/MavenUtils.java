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
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.RemoteRepositoryReleaseSource;
import biz.paluch.dap.artifact.RepositoryCredentials;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Contract;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.Nullable;

/**
 * Internal utilities for Maven POM file detection and remote repository
 * assembly.
 *
 * @author Mark Paluch
 */
class MavenUtils {

	static final String WRAPPER_FILENAME = "maven-wrapper.properties";

	private static final String WRAPPER_DIR = "wrapper";

	private static final String MVN_DIR = ".mvn";

	/**
	 * Return whether the given file is a Maven POM by filename alone.
	 * <p>This is a lightweight check suitable for action-visibility guards. It does
	 * not inspect file content or PSI structure.
	 *
	 * @param file the file to test; can be {@literal null}.
	 * @return {@literal true} if the filename is {@code pom.xml}; {@literal false}
	 * otherwise.
	 */
	public static boolean isMavenPomFile(@Nullable VirtualFile file) {
		return file != null && "pom.xml".equals(file.getName());
	}

	/**
	 * Return whether the given file is a Maven POM by filename and type.
	 * <p>This is a lightweight check suitable for action-visibility guards. It does
	 * not inspect file content or PSI structure.
	 *
	 * @param file the file to test; can be {@literal null}.
	 * @return {@literal true} if the file is an XML file named {@code pom.xml};
	 * {@literal false} otherwise.
	 */
	public static boolean isMavenPomFile(@Nullable PsiFile file) {
		return file instanceof XmlFile && "pom.xml".equals(file.getName());
	}

	/**
	 * Return whether the given XML file is a Maven POM by root element structure.
	 *
	 * @param xmlFile the XML file to inspect; must not be {@literal null}.
	 * @return {@literal true} if the root element identifies the file as a Maven
	 * POM; {@literal false} otherwise.
	 */
	public static boolean isMavenPomFile(XmlFile xmlFile) {

		XmlTag rootTag = xmlFile.getDocument() != null ? xmlFile.getDocument().getRootTag() : null;
		if (rootTag == null) {
			return false;
		}
		String localName = rootTag.getLocalName();
		if (!"project".equals(localName)) {
			return false;
		}
		String namespace = rootTag.getNamespace();
		return namespace.isEmpty() || "http://maven.apache.org/POM/4.0.0".equals(namespace);
	}

	/**
	 * Collect all remote repositories (dependency and plugin) from the given Maven
	 * project, decorated with credentials where available.
	 * <p>Repositories are deduplicated by URL.
	 *
	 * @param credentials credentials keyed by repository id; must not be
	 * {@literal null}.
	 * @param project the Maven project to inspect; must not be {@literal null}.
	 * @return the deduplicated set of remote repositories; guaranteed to be not
	 * {@literal null} but may be empty.
	 */
	public static Set<RemoteRepository> getRemoteRepositories(Map<String, RepositoryCredentials> credentials,
			MavenProject project) {

		Set<RemoteRepository> urls = new LinkedHashSet<>();

		forEach(project.getRemoteRepositories(),
				(id, url) -> urls.add(remoteRepository(id, url, credentials.get(id))));
		forEach(project.getRemotePluginRepositories(),
				(id, url) -> urls.add(remoteRepository(id, url, credentials.get(id))));

		return urls;
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
	 * Wrap each {@link RemoteRepository} as a {@link ReleaseSource} backed by
	 * {@link RemoteRepositoryReleaseSource}.
	 *
	 * @param remoteRepositories the repositories to wrap; must not be
	 * {@literal null}.
	 * @return a list of release sources in the same order; guaranteed to be not
	 * {@literal null}.
	 */
	public static List<ReleaseSource> getReleaseSources(Collection<RemoteRepository> remoteRepositories) {
		return remoteRepositories.stream().map(RemoteRepositoryReleaseSource::new).map(it -> (ReleaseSource) it)
				.toList();
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

		Map<String, RepositoryCredentials> credentials = SettingsXmlCredentialsLoader.load(project);
		Set<RemoteRepository> remoteRepositories = new LinkedHashSet<>();
		MavenProjectsManager manager = MavenProjectsManager.getInstance(project);

		for (MavenProject candidate : manager.getProjects()) {
			remoteRepositories.addAll(MavenUtils.getRemoteRepositories(credentials, candidate));
		}

		return getReleaseSources(remoteRepositories);
	}

	private static RemoteRepository remoteRepository(String id, String url,
			@Nullable RepositoryCredentials credentials) {

		if (credentials != null && !credentials.allowsRepositoryUrl(url)) {
			credentials = null;
		}
		return new RemoteRepository(id, url, credentials);
	}

	@Contract("null -> false")
	public static boolean isVersionElement(@Nullable PsiElement element) {

		if (element == null) {
			return false;
		}

		XmlTag currentTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
		if (currentTag == null) {
			return false;
		}

		XmlTag parentTag = currentTag.getParentTag();
		if (parentTag == null) {
			return false;
		}

		if (currentTag.getLocalName().equals("properties") || parentTag.getLocalName().equals("properties")
				|| currentTag.getLocalName().equals("dependency") || currentTag.getLocalName().equals("plugin")
				|| parentTag.getLocalName().equals("dependency") || parentTag.getLocalName().equals("plugin")) {
			return true;
		}

		return "version".equals(currentTag.getLocalName())
				&& ("dependency".equals(parentTag.getLocalName()) || "plugin".equals(parentTag.getLocalName()));
	}

	/**
	 * Return whether the given file is a Maven Wrapper properties file located at
	 * {@code .mvn/wrapper/maven-wrapper.properties}.
	 */
	static boolean isWrapperFile(@Nullable VirtualFile file) {

		if (file == null || !WRAPPER_FILENAME.equals(file.getName())) {
			return false;
		}

		VirtualFile parent = file.getParent();
		if (parent == null || !WRAPPER_DIR.equals(parent.getName())) {
			return false;
		}

		VirtualFile grandParent = parent.getParent();
		return grandParent != null && MVN_DIR.equals(grandParent.getName());
	}

	/**
	 * Return whether the given file is a Maven Wrapper properties file located at
	 * {@code .mvn/wrapper/maven-wrapper.properties}.
	 */
	static boolean isWrapperFile(@Nullable PsiFile file) {

		if (file == null || !WRAPPER_FILENAME.equals(file.getName())) {
			return false;
		}

		PsiDirectory parent = file.getParent();
		if (parent == null || !WRAPPER_DIR.equals(parent.getName())) {
			return false;
		}

		PsiDirectory grandParent = parent.getParent();
		return grandParent != null && MVN_DIR.equals(grandParent.getName());
	}

	/**
	 * Return whether the given {@link PropertiesFile} is a Maven Wrapper properties
	 * file located at the wrapper path.
	 */
	static boolean isWrapperFile(@Nullable PropertiesFile propsFile) {
		return propsFile != null && isWrapperFile(propsFile.getContainingFile());
	}
}
