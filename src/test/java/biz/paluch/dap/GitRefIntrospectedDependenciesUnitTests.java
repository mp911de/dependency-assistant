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

package biz.paluch.dap;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.github.TestGitHubReleases;
import biz.paluch.dap.state.Cache;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link GitRefIntrospectedDependencies}.
 *
 * @author Mark Paluch
 */
class GitRefIntrospectedDependenciesUnitTests {


	Cache cache = new Cache();

	@Test
	void completePromotesDeclarationWhenCacheMatchesVersion() {

		cache.addArtifacts(TestGitHubReleases.CHECKOUT);

		DependencyCollector collector = new DependencyCollector();
		collector.registerDeclaration(TestGitHubReleases.CHECKOUT.toArtifactId(), DeclarationSource.dependency(),
				VersionSource.declared("v4.2.0"));

		new GitRefIntrospectedDependencies(cache).complete(collector);

		Dependency usage = collector.getUsage(TestGitHubReleases.CHECKOUT.toArtifactId());
		assertThat(usage).isNotNull();
		assertThat(usage.getCurrentVersion()).isInstanceOfSatisfying(GitVersion.class, version -> {
			assertThat(version).hasToString("v4.2.0");
			assertThat(version.getSha()).isEqualTo(TestGitHubReleases.CHECKOUT_SHA_LATEST);
		});
	}

	@Test
	void completePromotesGitArtifactIdDeclarationWhenCacheMatchesByOwnerAndRepository() {

		cache.addArtifacts(TestGitHubReleases.ANTORA_UI);

		DependencyCollector collector = new DependencyCollector();
		collector.registerDeclaration(TestGitHubReleases.GIT_ANTORA_UI, DeclarationSource.dependency(),
				VersionSource.declared("v0.4.26"));

		new GitRefIntrospectedDependencies(cache).complete(collector);

		Dependency usage = collector.getUsage(TestGitHubReleases.GIT_ANTORA_UI);
		assertThat(usage).isNotNull();
		GitVersion version = (GitVersion) usage.getCurrentVersion();
		assertThat(version).hasToString("v0.4.26");
		assertThat(version.getSha()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
	}

	@Test
	void completeLeavesDeclarationUnresolvedWhenCacheIsCold() {

		DependencyCollector collector = new DependencyCollector();
		collector.registerDeclaration(TestGitHubReleases.CHECKOUT.toArtifactId(), DeclarationSource.dependency(),
				VersionSource.declared("v4.2.0"));

		new GitRefIntrospectedDependencies(cache).complete(collector);

		assertThat(collector).isEmpty();
	}

	@Test
	void completeDoesNotDisturbAlreadyResolvedUsage() {

		cache.addArtifacts(TestGitHubReleases.CHECKOUT);

		DependencyCollector collector = new DependencyCollector();
		VersionSource version = VersionSource.declared("v3.6.0");
		collector.registerDeclaration(TestGitHubReleases.CHECKOUT.toArtifactId(), DeclarationSource.dependency(),
				version);
		collector.registerUsage(TestGitHubReleases.CHECKOUT.toArtifactId(),
				ArtifactVersion.of("v3.6.0"), DeclarationSource.dependency(), version);

		new GitRefIntrospectedDependencies(cache).complete(collector);

		assertThat(collector).hasDependencyUsage("checkout")
				.hasVersion("v3.6.0");
	}

}
