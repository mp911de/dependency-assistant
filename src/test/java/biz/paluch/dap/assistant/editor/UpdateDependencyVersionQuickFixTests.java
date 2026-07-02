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

package biz.paluch.dap.assistant.editor;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.assistant.ArtifactReferenceContext;
import biz.paluch.dap.assistant.VersionStatus;
import biz.paluch.dap.checker.CheckerIcons;
import biz.paluch.dap.checker.CvssSeverity;
import biz.paluch.dap.checker.SecurityShieldIcons;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.maven.MavenFixtures;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Integration tests for the Safe Version and vulnerable-tier presentation of
 * {@link UpdateDependencyVersionQuickFix}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class UpdateDependencyVersionQuickFixTests {

	private static final String POM = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version>6.0.<caret>0</version>
					</dependency>
				</dependencies>
			</project>
			""";

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		MavenFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "pom.xml", content = POM)
	void safeFixShowsSafeLabelAndShield(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		UpdateDependencyVersionQuickFix fix = fix(pomFile, UpgradeStrategy.SAFE, "6.0.2", Vulnerabilities.clean());

		assertThat(fix.getText()).isEqualTo("Upgrade to safe version 6.0.2");
		assertThat(fix.getIcon(Iconable.ICON_FLAG_VISIBILITY)).isSameAs(CheckerIcons.SAFE);
	}

	@Test
	@EditorFile(name = "pom.xml", content = POM)
	void vulnerableTierFixAppendsSuffixAndFilledShield(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		UpdateDependencyVersionQuickFix fix = fix(pomFile, UpgradeStrategy.PATCH, "6.0.3",
				Vulnerabilities.of(cve(CvssSeverity.HIGH)));

		assertThat(fix.getText()).isEqualTo("Upgrade to 6.0.3 (High: CVE-2026-1)");
		assertThat(fix.getIcon(Iconable.ICON_FLAG_VISIBILITY))
				.isSameAs(SecurityShieldIcons.filled(CvssSeverity.HIGH).getIcon());
	}

	@Test
	void vulnerableSuggestionDetailShowsSingleIdentifierAndSeverity() {

		Vulnerabilities vulnerabilities = Vulnerabilities.of(cve(CvssSeverity.CRITICAL));

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getDetail())
				.isEqualTo("Critical: CVE-2026-1");
	}

	@Test
	void vulnerableSuggestionDetailCountsHighestSeverity() {

		Vulnerabilities vulnerabilities = Vulnerabilities.of(
				cve("CVE-2026-1", "GHSA-1", CvssSeverity.CRITICAL),
				cve("CVE-2026-2", "GHSA-2", CvssSeverity.CRITICAL));

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getDetail())
				.isEqualTo("2 Critical");
	}

	@Test
	void vulnerableSuggestionDetailCountsOtherSeverities() {

		Vulnerabilities vulnerabilities = Vulnerabilities.of(
				cve("CVE-2026-1", "GHSA-1", CvssSeverity.CRITICAL),
				cve("CVE-2026-2", "GHSA-2", CvssSeverity.HIGH),
				cve("CVE-2026-3", "GHSA-3", CvssSeverity.MEDIUM),
				cve("CVE-2026-4", "GHSA-4", CvssSeverity.LOW),
				cve("CVE-2026-5", "GHSA-5", CvssSeverity.HIGH),
				cve("CVE-2026-6", "GHSA-6", CvssSeverity.MEDIUM));

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getDetail())
				.isEqualTo("1 Critical and 5 others");
	}

	@Test
	void vulnerableSuggestionDetailPluralizesSingleOtherSeverity() {

		Vulnerabilities vulnerabilities = Vulnerabilities.of(
				cve("CVE-2026-1", "GHSA-1", CvssSeverity.CRITICAL),
				cve("CVE-2026-2", "GHSA-2", CvssSeverity.HIGH));

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getDetail())
				.isEqualTo("1 Critical and 1 other");
	}

	@Test
	void vulnerableSuggestionDetailPrefersGhsaWhenCveIsAbsent() {

		Vulnerabilities vulnerabilities = Vulnerabilities.of(cve(null, "GHSA-2026-1", CvssSeverity.HIGH));

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getDetail())
				.isEqualTo("High: GHSA-2026-1");
	}

	@Test
	void vulnerableSuggestionDetailFallsBackToAdvisoryId() {

		Vulnerabilities vulnerabilities = Vulnerabilities.of(cve(null, null, CvssSeverity.MEDIUM));

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getDetail())
				.isEqualTo("Medium: GHSA-1");
	}

	@Test
	void vulnerableSuggestionDetailSkipsUnratedSeverities() {

		Vulnerabilities vulnerabilities = Vulnerabilities.of(
				cve("CVE-2026-1", "GHSA-1", CvssSeverity.CRITICAL),
				cve("CVE-2026-2", "GHSA-2", CvssSeverity.UNKNOWN),
				cve("CVE-2026-3", "GHSA-3", CvssSeverity.NONE));

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getDetail())
				.isEqualTo("Critical: CVE-2026-1");
	}

	@Test
	void vulnerableSuggestionDetailFallsBackWhenOnlyUnratedSeveritiesRemain() {

		Vulnerabilities vulnerabilities = Vulnerabilities.of(
				cve("CVE-2026-1", "GHSA-1", CvssSeverity.UNKNOWN),
				cve("CVE-2026-2", "GHSA-2", CvssSeverity.NONE));

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getDetail())
				.isEqualTo("known vulnerabilities");
	}

	@Test
	@EditorFile(name = "pom.xml", content = POM)
	void cleanTierFixIsUnchanged(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		UpdateDependencyVersionQuickFix fix = fix(pomFile, UpgradeStrategy.PATCH, "6.0.3", Vulnerabilities.absent());

		assertThat(fix.getText()).isEqualTo("Upgrade to 6.0.3");
		assertThat(fix.getIcon(Iconable.ICON_FLAG_VISIBILITY)).isNotSameAs(CheckerIcons.SAFE);
	}

	private UpdateDependencyVersionQuickFix fix(PsiFile pomFile, UpgradeStrategy strategy, String targetVersion,
			Vulnerabilities targetVulnerabilities) {

		PsiElement leaf = pomFile.findElementAt(fixture.getEditor().getCaretModel().getOffset());
		PsiElement element = leaf.getParent();
		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(element);
		ArtifactReferenceContext referenceContext = ArtifactReferenceContext.from(element);
		ArtifactReference reference = referenceContext.getArtifactReference();
		DependencyUpdate update = DependencyUpdate.from(reference, Release.of(targetVersion));
		VersionStatus status = VersionStatus.of(referenceContext.getEvaluator(),
				reference.getDeclaration().getVersion(),
				update.version(), targetVulnerabilities);
		return new UpdateDependencyVersionQuickFix(reference.getDeclaration().getVersionLiteral(), strategy, context,
				update, reference.getDeclaration(), status);
	}

	private static Vulnerability cve(CvssSeverity severity) {
		return cve("CVE-2026-1", "GHSA-1", severity);
	}

	private static Vulnerability cve(String cveId, String ghsaId, CvssSeverity severity) {
		return new Vulnerability("GHSA-1", cveId, ghsaId, "Boom", 7.5, severity, "https://example.com");
	}

}
