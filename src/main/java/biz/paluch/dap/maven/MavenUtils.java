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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * @author Mark Paluch
 */
public class MavenUtils {

	/**
	 * Uses the IDE's PSI to detect if the document is a Maven POM: root element must be "project" with Maven POM
	 * namespace (or no namespace).
	 */
	public static boolean isMavenPomFile(Project project, com.intellij.openapi.editor.Document document) {
		PsiElement psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);

		if (!(psiFile instanceof XmlFile xmlFile)) {
			return false;
		}

		return isMavenPomFile(xmlFile);
	}

	public static boolean isMavenPomFile(@Nullable PsiFile file) {
		return file instanceof XmlFile xmlFile && isMavenPomFile(xmlFile);
	}

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

	public static Set<RemoteRepository> getRemoteRepositories(Map<String, RepositoryCredentials> credentials,
			MavenProject project) {

		Set<RemoteRepository> urls = new LinkedHashSet<>();

		forEach(project.getRemoteRepositories(), (id, url) -> urls.add(new RemoteRepository(id, url, credentials.get(id))));
		forEach(project.getRemotePluginRepositories(),
				(id, url) -> urls.add(new RemoteRepository(id, url, credentials.get(id))));

		return urls;
	}

	public static void forEach(Collection<MavenRemoteRepository> repositories, BiConsumer<String, String> consumer) {
		for (MavenRemoteRepository repo : repositories) {

			String url = repo.getUrl();
			if (StringUtils.hasText(url) && (url.startsWith("http://") || url.startsWith("https://"))) {
				String repoId = repo.getId();
				String urlToUse = url.endsWith("/") ? url : url + "/";

				consumer.accept(repoId, urlToUse);
			}
		}
	}

	public static List<ReleaseSource> getReleaseSources(Collection<RemoteRepository> remoteRepositories) {
		return remoteRepositories.stream().map(RemoteRepositoryReleaseSource::new).map(it -> (ReleaseSource) it).toList();
	}

}
