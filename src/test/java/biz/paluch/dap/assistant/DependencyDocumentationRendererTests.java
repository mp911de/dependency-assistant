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
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.TestCache;
import biz.paluch.dap.checker.CvssSeverity;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.fixtures.DependencyAssistantFixtures;
import biz.paluch.dap.fixtures.Releases;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.VersionProperty;
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

		String html = renderer(null).render(property, false);

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

		String html = renderer(null).render(property, false);

		assertThat(StringUtils.countMatches(html, "Version property for:")).isEqualTo(2);
		assertThat(StringUtils.countMatches(html, "<table>")).isEqualTo(2);
		assertThat(html)
				.contains("<p>Version property for: <code>io.lettuce:lettuce-core</code></p>")
				.contains("<p>Version property for: <code>org.junit:junit-bom</code></p>");
	}

	@Test
	void shouldDocumentSecurityAdvisoriesForVulnerableCurrentVersion() {

		cache.addVulnerabilities(Releases.LETTUCE_CORE, "7.5.1.RELEASE", CVE);

		String html = renderer("7.5.1.RELEASE").render(Releases.LETTUCE_CORE.toArtifactId(), false);

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

		String html = renderer("7.5.1.RELEASE").render(Releases.LETTUCE_CORE.toArtifactId(), false);

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

		String html = renderer(null, true).render(artifactId, false);

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

		String html = renderer("7.5.1.RELEASE").render(Releases.LETTUCE_CORE.toArtifactId(), false);

		assertThat(html).contains("Remote <code>foo</code> execution in <code>bar</code>");
	}

	@Test
	void shouldEscapeUnbalancedSecurityAdvisoryCodeFences() {

		Vulnerability cve = new Vulnerability("GHSA-abcd", "CVE-2026-1", "GHSA-abcd",
				"Remote `foo execution", 9.8, CvssSeverity.CRITICAL,
				"https://example.com/advisory");
		cache.addVulnerabilities(Releases.LETTUCE_CORE, "7.5.1.RELEASE", cve);

		String html = renderer("7.5.1.RELEASE").render(Releases.LETTUCE_CORE.toArtifactId(), false);

		assertThat(html).contains("Remote `foo execution").doesNotContain("Remote <code>foo");
	}

	@Test
	void shouldOmitSecurityAdvisoriesForCleanCurrentVersion() {

		ArtifactId lettuce = Releases.LETTUCE_CORE.toArtifactId();
		cache.addVulnerabilities(lettuce, "7.5.1.RELEASE");

		String html = renderer("7.5.1.RELEASE").render(lettuce, false);

		assertThat(html).contains("Current value").doesNotContain("Security advisories");
	}

	@Test
	void shouldOmitSecurityAdvisoriesForUnscannedCurrentVersion() {

		ArtifactId lettuce = Releases.LETTUCE_CORE.toArtifactId();

		String html = renderer("7.5.1.RELEASE").render(lettuce, false);

		assertThat(html).contains("Current value").doesNotContain("Security advisories");
	}

	@Test
	void shouldRenderAdvisoriesOncePerVulnerableGroup() {

		cache.addVulnerabilities(Releases.LETTUCE_CORE, "7.5.1.RELEASE", CVE);
		VersionProperty property = new VersionProperty("managed.version",
				Releases.LETTUCE_CORE, Releases.JUNIT_BOM);

		String html = renderer("7.5.1.RELEASE").render(property, false);

		assertThat(html).containsOnlyOnce("Security advisories");
	}

	@Test
	void shouldDocumentNewerReleaseLookupItem() {

		cache.addVulnerabilities(Releases.LETTUCE_CORE, "7.5.1.RELEASE", CVE);

		String html = renderer("7.4.1.RELEASE").render(release(Releases.LETTUCE_CORE, "7.5.1.RELEASE"));

		assertThat(html)
				.contains("io.lettuce:lettuce-core 7.5.1.RELEASE")
				.contains("Released")
				.contains("Newer than the current version")
				.contains("7.4.1.RELEASE")
				.contains("Security advisories")
				.contains("CVE-2026-1");
	}

	@Test
	void shouldDocumentOlderReleaseLookupItem() {

		String html = renderer("7.5.1.RELEASE").render(release(Releases.LETTUCE_CORE, "7.4.1.RELEASE"));

		assertThat(html)
				.contains("Older than the current version").contains("7.5.1.RELEASE")
				.doesNotContain("Security advisories");
	}

	@Test
	void shouldDocumentCurrentReleaseLookupItem() {

		String html = renderer("7.4.1.RELEASE").render(release(Releases.LETTUCE_CORE, "7.4.1.RELEASE"));

		assertThat(html)
				.contains("Currently declared version")
				.doesNotContain("Newer than")
				.doesNotContain("Older than");
	}

	@Test
	void shouldDocumentReleaseLookupItemWithoutCurrentVersion() {

		String html = renderer(null).render(release(Releases.LETTUCE_CORE, "7.4.1.RELEASE"));

		assertThat(html)
				.contains("io.lettuce:lettuce-core 7.4.1.RELEASE")
				.doesNotContain("Newer than")
				.doesNotContain("Older than")
				.doesNotContain("Currently declared version");
	}

	@Test
	void shouldDocumentReleaseAgeInMonths() {

		String html = renderer("7.5.0.RELEASE").render(release(Releases.LETTUCE_CORE, "7.5.1.RELEASE"));

		assertThat(html).contains("1 month newer than the current version").contains("7.5.0.RELEASE");
	}

	@Test
	void shouldDocumentReleaseAgeInDays() {

		String html = renderer("5.14.0").render(release(Releases.JUNIT_BOM, "5.14.3"));

		assertThat(html).contains("3 days newer than the current version");
	}

	@Test
	void shouldDocumentOlderReleaseAge() {

		String html = renderer("7.5.1.RELEASE").render(release(Releases.LETTUCE_CORE, "7.5.0.RELEASE"));

		assertThat(html).contains("1 month older than the current version");
	}

	@Test
	void shouldOmitReleaseAgeWithoutReleaseDates() {

		String html = renderer("4.0.4").render(release(Releases.SPRING_BOOT, "4.0.5"));

		assertThat(html)
				.contains("Newer than the current version")
				.doesNotContain("Released");
	}

	private ArtifactRelease release(CachedArtifact artifact, String version) {

		for (Release release : cache.getReleases(artifact.toArtifactId())) {
			if (release.version().toString().equals(version)) {
				return new ArtifactRelease(artifact.toArtifactId(), release);
			}
		}
		throw new IllegalArgumentException("No release " + version);
	}

	private DependencyDocumentationRenderer renderer(@Nullable String currentVersion) {
		return renderer(currentVersion, false);
	}

	private DependencyDocumentationRenderer renderer(@Nullable String currentVersion, boolean linkable) {
		return new DependencyDocumentationRenderer(TestInterfaceAssistant.INSTANCE, cache,
				DependencyRuleEvaluator.absent(),
				currentVersion != null ? ArtifactVersion.of(currentVersion) : null, linkable);
	}

}
