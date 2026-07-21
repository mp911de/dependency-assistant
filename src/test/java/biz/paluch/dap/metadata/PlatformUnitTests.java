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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.fixtures.TestProjects;
import biz.paluch.dap.github.GitHubPlatform;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link Platform} implementations: coordinate detection and
 * the browsable URLs rendered by the created repository handles.
 *
 * @author Mark Paluch
 */
class PlatformUnitTests {

	@Test
	void gitHubAcceptsCanonicalCoordinates() {

		assertThat(detect(new GitHubPlatform(), "https://github.com/spring-projects/spring-framework")).isNotNull();
		assertThat(detect(new GitHubPlatform(), "scm:git:git@github.com:mp911de/logstash-gelf.git")).isNotNull();
		assertThat(detect(new GitHubPlatform(), "https://github.com/spring-projects/.github")).isNotNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"https://github.com/%2e%2e/%2e%2e",
			"https://github.com/-owner/repo",
			"https://github.com/owner-/repo",
			"https://github.com/own--er/repo",
			"https://github.com/owner/.."})
	void gitHubRejectsNamesOutsideGitHubRules(String url) {
		assertThat(detect(new GitHubPlatform(), url)).isNull();
	}

	@Test
	void gitLabAcceptsNestedGroupCoordinates() {
		assertThat(detect(new GitLabPlatform(), "scm:git:https://gitlab.com/gitlab-org/security-products/analyzers"))
				.isNotNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"scm:git:https://gitlab.com/../etc",
			"scm:git:https://gitlab.com/group/%2e%2e",
			"scm:git:https://gitlab.com/-group/repo"})
	void gitLabRejectsSegmentsOutsidePathFormat(String url) {
		assertThat(detect(new GitLabPlatform(), url)).isNull();
	}

	@Test
	void codebergAcceptsFlatCoordinates() {
		assertThat(detect(new CodebergPlatform(), "scm:git:https://codeberg.org/forgejo/forgejo")).isNotNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"scm:git:https://codeberg.org/%2e%2e/secret",
			"scm:git:https://codeberg.org/owner/..",
			"scm:git:https://codeberg.org/ow..ner/repo"})
	void codebergRejectsNamesOutsideForgejoRules(String url) {
		assertThat(detect(new CodebergPlatform(), url)).isNull();
	}

	@Test
	void bitbucketAcceptsFlatCoordinates() {
		assertThat(detect(new BitbucketPlatform(), "scm:git:https://bitbucket.org/atlassian/atlassian-frontend-mirror"))
				.isNotNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"scm:git:https://bitbucket.org/../repo",
			"scm:git:https://bitbucket.org/workspace/.hidden",
			"scm:git:https://bitbucket.org/work.space/repo"})
	void bitbucketRejectsNamesOutsideWorkspaceAndSlugRules(String url) {
		assertThat(detect(new BitbucketPlatform(), url)).isNull();
	}

	@Test
	void sourceForgeAcceptsUnixNames() {

		assertThat(detect(new SourceForgePlatform(), "https://sourceforge.net/projects/libjpeg-turbo/")).isNotNull();
		assertThat(detect(new SourceForgePlatform(), "git://git.code.sf.net/p/libpng/code")).isNotNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"https://sourceforge.net/projects/../files",
			"https://sourceforge.net/projects/%2e%2e/files",
			"https://sourceforge.net/projects/some.project/"})
	void sourceForgeRejectsNamesOutsideUnixNameRules(String url) {
		assertThat(detect(new SourceForgePlatform(), url)).isNull();
	}

	@Test
	void bitbucketRendersDownloadsAndTagCommitUrls() {

		ProjectRepository repository = repository(new BitbucketPlatform(),
				"scm:git:https://bitbucket.org/atlassian/atlassian-frontend-mirror.git");

		assertThat(repository.getUrl()).hasToString("https://bitbucket.org/atlassian/atlassian-frontend-mirror");
		assertThat(repository.getReleasesUrl())
				.hasToString("https://bitbucket.org/atlassian/atlassian-frontend-mirror/downloads");
		assertThat(repository.getReleaseNotesUrl("v1.0"))
				.hasToString("https://bitbucket.org/atlassian/atlassian-frontend-mirror/commits/tag/v1.0");
		assertThat(repository.getIssueTracker()).isNull();
	}

	@Test
	void codebergRendersReleaseAndIssueUrls() {

		ProjectRepository repository = repository(new CodebergPlatform(),
				"scm:git:https://codeberg.org/forgejo/forgejo.git");

		assertThat(repository.getUrl()).hasToString("https://codeberg.org/forgejo/forgejo");
		assertThat(repository.getReleasesUrl()).hasToString("https://codeberg.org/forgejo/forgejo/releases");
		assertThat(repository.getReleaseNotesUrl("v14.0.2"))
				.hasToString("https://codeberg.org/forgejo/forgejo/releases/tag/v14.0.2");

		IssueTracker tracker = repository.getIssueTracker();
		assertThat(tracker).isNotNull();
		assertThat(tracker.getBaseUrl()).hasToString("https://codeberg.org/forgejo/forgejo/issues");
		assertThat(tracker.getCreateNewIssueUrl(ArtifactId.of("org.forgejo", "forgejo"), ArtifactVersion.of("14.0.2")))
				.hasToString("https://codeberg.org/forgejo/forgejo/issues/new");
	}

	@Test
	void sourceForgeRendersFileListing() {

		ProjectRepository repository = repository(new SourceForgePlatform(),
				"https://sourceforge.net/projects/libjpeg-turbo/");

		assertThat(repository.getUrl()).hasToString("https://sourceforge.net/projects/libjpeg-turbo/");
		assertThat(repository.getReleasesUrl()).hasToString("https://sourceforge.net/projects/libjpeg-turbo/files/");
		assertThat(repository.getReleaseNotesUrl("1.0")).isNull();
		assertThat(repository.getIssueTracker()).isNull();
	}

	@Test
	void encodesTagNameAsSingleReleaseNotesPathSegment() {

		ProjectRepository repository = repository(new CodebergPlatform(),
				"scm:git:https://codeberg.org/forgejo/forgejo.git");

		assertThat(repository.getReleaseNotesUrl("v1/../../evil#frag"))
				.hasToString("https://codeberg.org/forgejo/forgejo/releases/tag/v1%2F..%2F..%2Fevil%23frag");
	}

	@Test
	void codebergDerivesTrackerForEmptyOrMatchingHint() {

		CodebergPlatform platform = new CodebergPlatform();
		RepositoryConnection connection = detect(platform, "scm:git:https://codeberg.org/forgejo/forgejo");

		assertThat(connection).isNotNull();
		assertThat(platform.detectIssueTracker(connection, null)).isNotNull();
		assertThat(platform.detectIssueTracker(connection, "Codeberg Issues")).isNotNull();
		assertThat(platform.detectIssueTracker(connection, "JIRA")).isNull();
	}

	private static ProjectRepository repository(Platform platform, String url) {

		RepositoryConnection connection = detect(platform, url);
		assertThat(connection).isNotNull();

		ProjectRepository repository = connection.createRepository(TestProjects.PROJECT);
		assertThat(repository).isNotNull();
		return repository;
	}

	private static @Nullable RepositoryConnection detect(Platform platform, String url) {

		RepositoryUrl repositoryUrl = RepositoryUrl.parse(url);
		assertThat(repositoryUrl).isNotNull();
		return platform.detect(repositoryUrl, null);
	}

}
