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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.TestCache;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.checker.CvssSeverity;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.fixtures.DependencyAssistantFixtures;
import biz.paluch.dap.fixtures.Releases;
import biz.paluch.dap.fixtures.TestProjectDependencyContext;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.ArtifactReference;
import com.intellij.mock.MockPsiElement;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DependencyDocumentationRenderer}.
 *
 * @author Mark Paluch
 */
class DependencyDocumentationRendererTests {

	static final Vulnerability CVE = new Vulnerability("GHSA-abcd", "CVE-2026-1", "GHSA-abcd", "Remote code execution",
			9.8, CvssSeverity.CRITICAL, "https://example.com/advisory");

	TestCache cache = DependencyAssistantFixtures.createCache();

	@Test
	void shouldDocumentPropertyWithSharedReleaseLine() {

		cache.putVersionOptions(Releases.VAVR_MATCH.toArtifactId(),
				cache.getReleases(Releases.VAVR.toArtifactId()));
		VersionProperty property = new VersionProperty("vavr.version", Releases.VAVR, Releases.VAVR_MATCH);

		String html = documentation().render(property, false);

		assertThat(html)
				.containsOnlyOnce("Version property for:")
				.containsOnlyOnce("<table>")
				.contains("<p>Version property for: <code>io.vavr:vavr</code>, "
						+ "<code>io.vavr:vavr-match</code></p>");
	}

	@Test
	void shouldDocumentPropertyWithIndividualReleaseLines() {

		VersionProperty property = new VersionProperty("managed.version",
				Releases.LETTUCE_CORE, Releases.JUNIT_BOM);

		String html = documentation().render(property, false);

		assertThat(StringUtils.countMatches(html, "Version property for:")).isEqualTo(2);
		assertThat(StringUtils.countMatches(html, "<table>")).isEqualTo(2);
		assertThat(html)
				.contains("<p>Version property for: <code>io.lettuce:lettuce-core</code></p>")
				.contains("<p>Version property for: <code>org.junit:junit-bom</code></p>");
	}

	@Test
	void shouldDocumentSecurityAdvisoriesForVulnerableCurrentVersion() {

		cache.addVulnerabilities(Releases.LETTUCE_CORE, "7.5.1.RELEASE", CVE);

		String html = new DependencyDocumentationRenderer(
				context(Releases.LETTUCE_CORE.toArtifactId(), "7.5.1.RELEASE"), false)
				.render(Releases.LETTUCE_CORE.toArtifactId(), false);

		assertThat(html)
				.contains("Security advisories")
				.contains("Remote code execution")
				.contains("CVE-2026-1")
				.contains("9.8")
				.contains("Critical")
				.contains("https://example.com/advisory");
	}

	@Test
	void shouldOmitSecurityAdvisoryLinkForUnsupportedScheme() {

		Vulnerability cve = new Vulnerability("GHSA-abcd", "CVE-2026-1", "GHSA-abcd", "Remote code execution",
				9.8, CvssSeverity.CRITICAL, "javascript:alert(1)");
		cache.addVulnerabilities(Releases.LETTUCE_CORE, "7.5.1.RELEASE", cve);

		String html = new DependencyDocumentationRenderer(
				context(Releases.LETTUCE_CORE.toArtifactId(), "7.5.1.RELEASE"), false)
				.render(Releases.LETTUCE_CORE.toArtifactId(), false);

		assertThat(html)
				.contains("CVE-2026-1")
				.doesNotContain("href=\"javascript:alert(1)\"");
	}

	@Test
	void shouldEscapeReleaseLinkHref() {

		ArtifactId artifactId = ArtifactId.of("com.example", "unsafe");
		String key = "release\" onclick=\"alert(1)\"><script>";
		cache = new TestCache() {

			@Override
			public biz.paluch.dap.artifact.Releases getReleases(ArtifactId requested) {

				if (artifactId.equals(requested)) {
					return biz.paluch.dap.artifact.Releases.just(Release.of(new GitRef(key)));
				}
				return super.getReleases(requested);
			}

		};

		String html = new DependencyDocumentationRenderer(context(artifactId), true).render(artifactId, false);

		assertThat(html)
				.contains(
						"href=\"dependency-assistant-upgrade:release&quot; onclick=&quot;alert(1)&quot;&gt;&lt;script&gt;\"")
				.doesNotContain("href=\"dependency-assistant-upgrade:release\" onclick=\"alert(1)\"><script>\"");
	}

	@Test
	void shouldRenderSecurityAdvisoryCodeFences() {

		Vulnerability cve = new Vulnerability("GHSA-abcd", "CVE-2026-1", "GHSA-abcd",
				"Remote `foo` execution in `bar`", 9.8, CvssSeverity.CRITICAL,
				"https://example.com/advisory");
		cache.addVulnerabilities(Releases.LETTUCE_CORE, "7.5.1.RELEASE", cve);

		String html = new DependencyDocumentationRenderer(
				context(Releases.LETTUCE_CORE.toArtifactId(), "7.5.1.RELEASE"), false)
				.render(Releases.LETTUCE_CORE.toArtifactId(), false);

		assertThat(html).contains("Remote <code>foo</code> execution in <code>bar</code>");
	}

	@Test
	void shouldEscapeUnbalancedSecurityAdvisoryCodeFences() {

		Vulnerability cve = new Vulnerability("GHSA-abcd", "CVE-2026-1", "GHSA-abcd",
				"Remote `foo execution", 9.8, CvssSeverity.CRITICAL,
				"https://example.com/advisory");
		cache.addVulnerabilities(Releases.LETTUCE_CORE, "7.5.1.RELEASE", cve);

		String html = new DependencyDocumentationRenderer(
				context(Releases.LETTUCE_CORE.toArtifactId(), "7.5.1.RELEASE"), false)
				.render(Releases.LETTUCE_CORE.toArtifactId(), false);

		assertThat(html).contains("Remote `foo execution").doesNotContain("Remote <code>foo");
	}

	@Test
	void shouldOmitSecurityAdvisoriesForCleanCurrentVersion() {

		ArtifactId lettuce = Releases.LETTUCE_CORE.toArtifactId();
		cache.addVulnerabilities(lettuce, "7.5.1.RELEASE");

		String html = new DependencyDocumentationRenderer(context(lettuce, "7.5.1.RELEASE"), false).render(lettuce,
				false);

		assertThat(html).contains("Current value").doesNotContain("Security advisories");
	}

	@Test
	void shouldOmitSecurityAdvisoriesForUnscannedCurrentVersion() {

		ArtifactId lettuce = Releases.LETTUCE_CORE.toArtifactId();

		String html = new DependencyDocumentationRenderer(context(lettuce, "7.5.1.RELEASE"), false).render(lettuce,
				false);

		assertThat(html).contains("Current value").doesNotContain("Security advisories");
	}

	private DependencyDocumentationRenderer documentation() {
		return new DependencyDocumentationRenderer(context(Releases.VAVR.toArtifactId()), false);
	}

	private ArtifactReferenceContext context(ArtifactId artifactId) {
		return context(artifactId, (ArtifactVersion) null);
	}

	private ArtifactReferenceContext context(ArtifactId artifactId, String currentVersion) {
		return context(artifactId, ArtifactVersion.of(currentVersion));
	}

	private ArtifactReferenceContext context(ArtifactId artifactId,
			@Nullable ArtifactVersion currentVersion) {

		VersionSource versionSource = currentVersion == null ? VersionSource.none()
				: VersionSource.declared(currentVersion.toString());
		ArtifactReference reference = ArtifactReference.from(builder -> {
			builder.artifact(artifactId)
					.versionSource(versionSource)
					.declarationSource(DeclarationSource.dependency())
					.declarationElement(new MockPsiElement(() -> {
					}));
			if (currentVersion != null) {
				builder.version(currentVersion);
			}
		});
		return new ArtifactReferenceContext(TestProjectDependencyContext.INSTANCE, cache, reference,
				DependencyRuleEvaluator.absent());
	}

}
