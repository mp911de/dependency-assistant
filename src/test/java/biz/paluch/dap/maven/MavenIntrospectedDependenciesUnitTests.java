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

import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link MavenIntrospectedDependencies}.
 *
 * @author Mark Paluch
 */
class MavenIntrospectedDependenciesUnitTests {

	private ArtifactId COMMONS_LANG = ArtifactId.of("org.apache.commons", "commons-lang3");

	@Test
	void completePromotesDeclarationWhenCollectorHasMatchingPropertyValue() {

		DependencyCollector collector = new DependencyCollector();
		collector.registerDeclaration(COMMONS_LANG, DeclarationSource.managed(),
				VersionSource.property("commons.version"));
		collector.addPropertyValues(Map.of("commons.version", "3.19.0"));

		MavenIntrospectedDependencies introspected = new MavenIntrospectedDependencies();
		introspected.register(collector);

		introspected.complete(collector);

		assertThat(collector).hasDependencyUsage("org.apache.commons", "commons-lang3")
				.hasVersion("3.19.0")
				.hasDeclaration(DeclarationSource.managed())
				.hasVersionSource(VersionSource.property("commons.version"));
	}

	@Test
	void completePromotesAcrossCollectorsForParentChildPropertyResolution() {

		DependencyCollector parent = new DependencyCollector();
		parent.registerDeclaration(COMMONS_LANG, DeclarationSource.managed(),
				VersionSource.property("commons.version"));

		DependencyCollector child = new DependencyCollector();
		child.addPropertyValues(Map.of("commons.version", "3.19.0"));

		MavenIntrospectedDependencies introspected = new MavenIntrospectedDependencies();
		introspected.register(parent);
		introspected.register(child);

		introspected.complete(child);

		assertThat(child).hasDependencyUsage("org.apache.commons", "commons-lang3")
				.hasVersion("3.19.0")
				.hasDeclaration(DeclarationSource.managed())
				.hasVersionSource(VersionSource.property("commons.version"));
	}

	@Test
	void completeLeavesDeclarationUnresolvedWhenNoPropertyValueAvailable() {

		DependencyCollector collector = new DependencyCollector();
		collector.registerDeclaration(COMMONS_LANG, DeclarationSource.managed(),
				VersionSource.property("commons.version"));

		MavenIntrospectedDependencies introspected = new MavenIntrospectedDependencies();
		introspected.register(collector);

		introspected.complete(collector);

		assertThat(collector).isEmpty();
	}

	@Test
	void completeDoesNotDisturbAlreadyResolvedUsage() {

		DependencyCollector collector = new DependencyCollector();
		VersionSource version = VersionSource.property("commons.version");
		collector.registerDeclaration(COMMONS_LANG, DeclarationSource.managed(), version);
		collector.registerUsage(COMMONS_LANG, ArtifactVersion.of("3.0.0"), DeclarationSource.managed(), version);
		collector.addPropertyValues(Map.of("commons.version", "3.19.0"));

		MavenIntrospectedDependencies introspected = new MavenIntrospectedDependencies();
		introspected.register(collector);

		introspected.complete(collector);

		assertThat(collector).hasDependencyUsage("org.apache.commons", "commons-lang3").hasVersion("3.0.0");
	}

	@Test
	void completeWithoutRegisteredCollectorsStillUsesProvidedCollector() {

		DependencyCollector collector = new DependencyCollector();
		collector.registerDeclaration(COMMONS_LANG, DeclarationSource.managed(),
				VersionSource.property("commons.version"));
		collector.addPropertyValues(Map.of("commons.version", "3.19.0"));

		MavenIntrospectedDependencies introspected = new MavenIntrospectedDependencies();

		introspected.complete(collector);

		assertThat(collector).hasDependencyUsage("org.apache.commons", "commons-lang3").hasVersion("3.19.0");
	}

}
