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

package biz.paluch.dap.metadata;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.RemoteUrl;
import org.jspecify.annotations.Nullable;

/**
 * Recognizes {@code sourceforge.net} project pages and {@code *.code.sf.net}
 * SCM hosts. SourceForge is project-based, not owner/repository-based: the
 * project name is extracted from {@code /projects/{project}} and
 * {@code /p/{project}} paths, and SCM URLs may be Git, Subversion, or Mercurial
 * alike, so detection does not gate on the repository type.
 *
 * <p>File releases are the release concept, so the releases list is
 * {@code /projects/{project}/files/}. There is no generic per-tag page, and the
 * ticket-tracker mount point ({@code bugs}, {@code tickets}, ...) is
 * per-project configuration, so neither a release-notes nor a tracker URL is
 * derived.
 *
 * @author Mark Paluch
 */
public class SourceForgePlatform extends PlatformSupport {

	/**
	 * SourceForge project unix names: letters, numbers, and dashes per the
	 * project-creation rules, starting and ending with an alphanumeric. Underscores
	 * are tolerated for legacy projects; the documented 3-30 length is relaxed to
	 * an upper bound of 50.
	 */
	private static final Pattern PROJECT_NAME = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9_-]{0,48}[A-Za-z0-9])?");

	public SourceForgePlatform() {
		super(null, "files/", null, null);
	}

	@Override
	public @Nullable RepositoryConnection detect(RepositoryUrl repositoryUrl, @Nullable String hint) {

		String project = projectName(repositoryUrl.getRemote());
		if (project != null) {
			return new SimpleRepositoryConnection(this, "sourceforge/" + project,
					"https://sourceforge.net/projects/%s/".formatted(project));
		}

		return null;
	}

	/**
	 * Extract the SourceForge project name from a project-web or SCM URL, or return
	 * {@literal null} when the URL is not SourceForge-shaped.
	 */
	private static @Nullable String projectName(RemoteUrl remoteUrl) {

		if (!isSourceForgeHost(remoteUrl.host())) {
			return null;
		}

		List<String> segments = remoteUrl.pathSegments();
		if (segments.size() < 2 || (!segments.get(0).equals("projects") && !segments.get(0).equals("p"))) {
			return null;
		}

		String project = segments.get(1);
		return PROJECT_NAME.matcher(project).matches() ? project : null;
	}

	private static boolean isSourceForgeHost(String host) {

		String name = host.toLowerCase(Locale.ROOT);
		return name.equals("sourceforge.net") || name.equals("www.sourceforge.net") || name.endsWith(".sf.net");
	}

}
