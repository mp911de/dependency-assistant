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

package biz.paluch.dap.assistant;

import java.time.LocalDateTime;
import java.util.List;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.VersionProperty;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DocumentationContext}.
 *
 * @author Mark Paluch
 */
class DocumentationContextTests {

	static final InterfaceAssistant ASSISTANT = TestInterfaceAssistant.INSTANCE;

	@Test
	void propertyDocumentationCollapsesArtifactsWithSameReleaseVersions() {

		ArtifactId core = ArtifactId.of("org.springframework", "spring-core");
		ArtifactId context = ArtifactId.of("org.springframework", "spring-context");
		Cache cache = new Cache();
		cache.putVersionOptions(core, List.of(release("6.2.0", 1), release("6.1.1", 2)));
		cache.putVersionOptions(context, List.of(release("6.1.1", 3), release("6.2.0", 4)));
		VersionProperty property = property("spring.version", core, context);

		String html = documentation(cache).render(property, null);

		assertThat(html)
				.containsOnlyOnce("Version property for:")
				.containsOnlyOnce("<table>")
				.contains("<p>Version property for: <code>org.springframework:spring-core</code>, "
						+ "<code>org.springframework:spring-context</code></p>");
	}

	@Test
	void propertyDocumentationKeepsSeparateTablesForDifferentReleaseVersions() {

		ArtifactId core = ArtifactId.of("org.springframework", "spring-core");
		ArtifactId context = ArtifactId.of("org.springframework", "spring-context");
		ArtifactId boot = ArtifactId.of("org.springframework.boot", "spring-boot");
		Cache cache = new Cache();
		cache.putVersionOptions(core, List.of(release("6.2.0", 1), release("6.1.1", 2)));
		cache.putVersionOptions(context, List.of(release("6.1.1", 3), release("6.2.0", 4)));
		cache.putVersionOptions(boot, List.of(release("3.5.0", 5), release("3.4.1", 6)));
		VersionProperty property = property("spring.version", core, context, boot);

		String html = documentation(cache).render(property, null);

		assertThat(StringUtils.countMatches(html, "Version property for:")).isEqualTo(2);
		assertThat(StringUtils.countMatches(html, "<table>")).isEqualTo(2);
		assertThat(html)
				.contains("<p>Version property for: <code>org.springframework:spring-core</code>, "
						+ "<code>org.springframework:spring-context</code></p>")
				.contains("<p>Version property for: <code>org.springframework.boot:spring-boot</code></p>");
	}

	private static DocumentationContext documentation(Cache cache) {
		return new DocumentationContext(ASSISTANT, cache, null, false);
	}

	private static VersionProperty property(String name, ArtifactId... artifactIds) {
		return new VersionProperty(name, List.of(artifactIds).stream().map(CachedArtifact::new).toList());
	}

	private static Release release(String version, int dayOfMonth) {
		return Release.of(ArtifactVersion.of(version), LocalDateTime.of(2026, 1, dayOfMonth, 0, 0));
	}

}
