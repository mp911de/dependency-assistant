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

package biz.paluch.dap.maven;

import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.RepositoryCredentials;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;

/**
 * Effective Maven {@code settings.xml} state relevant to release lookups: server
 * credentials and mirror routing.
 *
 * @author Mark Paluch
 */
public class MavenSettings {

	private static final MavenSettings EMPTY = new MavenSettings(Map.of(), List.of());

	private final Map<String, RepositoryCredentials> credentials;

	private final DefaultMirrorSelector mirrorSelector;

	MavenSettings(Map<String, RepositoryCredentials> credentials, List<Mirror> mirrors) {
		this.credentials = credentials;
		this.mirrorSelector = new DefaultMirrorSelector();
		for (Mirror mirror : mirrors) {
			mirrorSelector.add(mirror.id(), mirror.url(), "default", false, false, mirror.mirrorOf(), "*");
		}
	}

	public static MavenSettings empty() {
		return EMPTY;
	}

	/**
	 * Resolve the effective remote repository for the given declared repository,
	 * applying mirror routing and attaching credentials.
	 *
	 * <p>If a mirror matches the repository, the result carries the mirror's id and
	 * URL and the credentials of the {@code <server>} matching the mirror id.
	 * Otherwise, the repository is returned unchanged with the credentials of the
	 * {@code <server>} matching its own id. Credentials are attached only when
	 * their repository binding permits the effective URL. Credentials without URL
	 * bindings remain eligible by repository id alone.
	 *
	 * @param id the declared repository id.
	 * @param url the declared repository URL.
	 * @return the effective remote repository.
	 */
	public RemoteRepository getRemoteRepository(String id, String url) {

		org.eclipse.aether.repository.RemoteRepository declared = new org.eclipse.aether.repository.RemoteRepository.Builder(
				id, "default", url).build();
		org.eclipse.aether.repository.RemoteRepository mirror = mirrorSelector.getMirror(declared);
		String effectiveId = mirror != null ? mirror.getId() : id;
		String effectiveUrl = mirror != null ? mirror.getUrl() : url;
		RepositoryCredentials repositoryCredentials = credentials.get(effectiveId);
		if (repositoryCredentials != null && !repositoryCredentials.allowsRepositoryUrl(effectiveUrl)) {
			repositoryCredentials = null;
		}

		return new RemoteRepository(effectiveId, effectiveUrl, repositoryCredentials);
	}

}
