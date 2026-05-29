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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedRelease;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitRefIntrospectedDependencies}.
 *
 * @author Mark Paluch
 */
class GitRefIntrospectedDependenciesUnitTests {

	private static final ArtifactId CHECKOUT = ArtifactId.of("actions", "checkout");

	private static final String SHA_V4 = "d1185ce59f7757407fe6a5febb1e03e3dba2a530";

	private static final ArtifactId ANTORA_UI = GitArtifactId.of("github.com", "spring-io", "antora-ui-spring");

	private static final ArtifactId ANTORA_UI_CACHE_KEY = ArtifactId.of("spring-io", "antora-ui-spring");

	@Test
	void completePromotesDeclarationWhenCacheMatchesVersion() {

		Cache cache = new Cache();
		CachedArtifact checkout = new CachedArtifact(CHECKOUT);
		checkout.getReleases().add(new CachedRelease("v4.2.0", "2024-10-01", SHA_V4));
		cache.addArtifacts(List.of(checkout));

		DependencyCollector collector = new DependencyCollector();
		collector.registerDeclaration(CHECKOUT, DeclarationSource.dependency(), VersionSource.declared("v4.2.0"));

		new GitRefIntrospectedDependencies(cache).complete(collector);

		Dependency usage = collector.getUsage(CHECKOUT);
		assertThat(usage).isNotNull();
		assertThat(usage.getCurrentVersion()).isInstanceOf(GitVersion.class);
		assertThat(usage.getCurrentVersion().toString()).isEqualTo("v4.2.0");
	}

	@Test
	void completePromotesGitArtifactIdDeclarationWhenCacheMatchesByOwnerAndRepository() {

		Cache cache = new Cache();
		CachedArtifact ui = new CachedArtifact(ANTORA_UI_CACHE_KEY);
		ui.getReleases().add(new CachedRelease("v0.4.26", "2025-01-01",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
		cache.addArtifacts(List.of(ui));

		DependencyCollector collector = new DependencyCollector();
		collector.registerDeclaration(ANTORA_UI, DeclarationSource.dependency(), VersionSource.declared("v0.4.26"));

		new GitRefIntrospectedDependencies(cache).complete(collector);

		Dependency usage = collector.getUsage(ANTORA_UI);
		assertThat(usage).isNotNull();
		assertThat(usage.getCurrentVersion()).isInstanceOf(GitVersion.class);
		assertThat(usage.getCurrentVersion().toString()).isEqualTo("v0.4.26");
	}

	@Test
	void completeLeavesDeclarationUnresolvedWhenCacheIsCold() {

		Cache cache = new Cache();

		DependencyCollector collector = new DependencyCollector();
		collector.registerDeclaration(CHECKOUT, DeclarationSource.dependency(), VersionSource.declared("v4.2.0"));

		new GitRefIntrospectedDependencies(cache).complete(collector);

		assertThat(collector.getUsages()).isEmpty();
	}

	@Test
	void completeDoesNotDisturbAlreadyResolvedUsage() {

		Cache cache = new Cache();
		CachedArtifact checkout = new CachedArtifact(CHECKOUT);
		checkout.getReleases().add(new CachedRelease("v4.2.0", "2024-10-01", SHA_V4));
		cache.addArtifacts(List.of(checkout));

		DependencyCollector collector = new DependencyCollector();
		VersionSource version = VersionSource.declared("v3.6.0");
		collector.registerDeclaration(CHECKOUT, DeclarationSource.dependency(), version);
		collector.registerUsage(CHECKOUT, ArtifactVersion.of("v3.6.0"), DeclarationSource.dependency(), version);

		new GitRefIntrospectedDependencies(cache).complete(collector);

		assertThat(collector.getUsage(CHECKOUT).getCurrentVersion().toString()).isEqualTo("v3.6.0");
	}

}
