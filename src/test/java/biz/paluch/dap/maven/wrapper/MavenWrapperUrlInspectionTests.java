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

package biz.paluch.dap.maven.wrapper;

import java.util.List;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.fixtures.DependencyAssistantFixtures;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedRelease;
import biz.paluch.dap.state.StateService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.QuickFix;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.ReflectionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Highlighting and quick-fix integration tests for
 * {@link MavenWrapperUrlInspection}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class MavenWrapperUrlInspectionTests {

	private String FIX_REMOVE_CREDENTIALS = "Remove credentials";

	private String FIX_USE_DEFAULT_URL = "Use default URL";

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		fixture.enableInspections(MavenWrapperUrlInspection.class);
		StateService.getInstance(fixture.getProject()).setCache(new Cache());
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://<warning descr="Wrapper URL contains plaintext credentials">alice:secret@</warning>repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void highlightsCredentialsInUrl(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=<warning descr="Wrapper URL does not match the expected Maven coordinates shape">not a url at all</warning>
			""")
	void highlightsInvalidUrl(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/<warning descr="Wrapper URL path version '3.9.6' disagrees with file version '3.9.5'">3.9.6</warning>/apache-maven-<warning descr="Wrapper URL path version '3.9.6' disagrees with file version '3.9.5'">3.9.5</warning>-bin.zip
			""")
	void highlightsInconsistentVersion(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/<warning descr="Wrapper URL path artifact 'foo' disagrees with file artifact 'bar'">foo</warning>/3.9.6/<warning descr="Wrapper URL file name 'bar-3.9.6-bin.zip' does not follow the canonical pattern"><warning descr="Wrapper URL path artifact 'foo' disagrees with file artifact 'bar'">bar</warning>-3.9.6-bin.zip</warning>
			""")
	void highlightsInconsistentArtifact(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/<warning descr="Wrapper URL group path 'com/example/wrong' is not the canonical Maven group">com/example/wrong</warning>/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void highlightsImproperGroupId(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/<warning descr="Wrapper URL artifactId 'foo' is not a canonical Maven wrapper artifact">foo</warning>/3.9.6/<warning descr="Wrapper URL file name 'foo-3.9.6-bin.zip' does not follow the canonical pattern"><warning descr="Wrapper URL artifactId 'foo' is not a canonical Maven wrapper artifact">foo</warning>-3.9.6-bin.zip</warning>
			""")
	void highlightsUnknownArtifact(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/<warning descr="Wrapper URL file name 'apache-maven-3.9.6.zip' does not follow the canonical pattern">apache-maven-3.9.6.zip</warning>
			""")
	void highlightsMalformedFileName(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			wrapperUrl=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/<warning descr="Wrapper URL file name 'maven-wrapper-3.3.2.zip' does not follow the canonical pattern">maven-wrapper-3.3.2.zip</warning>
			""")
	void highlightsMalformedFileNameForWrapperZip(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void registersMissingDistributionChecksumProblem(PsiFile file) {

		List<ProblemDescriptor> problems = inspectWrapperFile(file);

		assertThat(problems).singleElement().satisfies(problem -> {
			assertThat(problem.getDescriptionTemplate()).isEqualTo("distributionUrl has no SHA-256 checksum");
			assertThat(problem.getHighlightType()).isEqualTo(ProblemHighlightType.WEAK_WARNING);
			assertThat(problem.getFixes()).extracting(QuickFix::getName).contains("Compute SHA-256 checksum");
		});
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			distributionSha256Sum=
			""")
	void doesNotRegisterMissingChecksumWhenShaPropertyExists(PsiFile file) {
		assertThat(inspectWrapperFile(file)).isEmpty();
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void doesNotRegisterMissingChecksumWhenProjectIsUntrusted(PsiFile file) {

		Project project = fixture.getProject();
		TrustedProjects.setProjectTrusted(project, false);
		try {
			assertThat(inspectWrapperFile(file)).isEmpty();
		} finally {
			TrustedProjects.setProjectTrusted(project, true);
		}
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.5-bin.zip
			""")
	void doesNotRegisterMissingChecksumWhenUrlHasAnotherProblem(PsiFile file) {

		assertThat(inspectWrapperFile(file))
				.extracting(ProblemDescriptor::getDescriptionTemplate)
				.containsOnly("Wrapper URL path version '3.9.6' disagrees with file version '3.9.5'")
				.doesNotContain("distributionUrl has no SHA-256 checksum");
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.___IntellijIdeaRulezzz___/apache-maven-3.___IntellijIdeaRulezzz___-bin.zip
			""")
	void doesNotRegisterMissingChecksumDuringCompletion(PsiFile file) {
		assertThat(inspectWrapperFile(file)).isEmpty();
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/${prefix}/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void doesNotRegisterMissingChecksumWhenUrlContainsInterpolation(PsiFile file) {
		assertThat(inspectWrapperFile(file)).isEmpty();
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=http://<warning descr="Wrapper URL contains plaintext credentials">alice:secret@</warning>repo1.maven.org/maven2/org/apache/maven/<warning descr="Wrapper URL artifactId 'foo' is not a canonical Maven wrapper artifact">foo</warning>/3.9.6/<warning descr="Wrapper URL file name 'foo-3.9.6-bin.zip' does not follow the canonical pattern"><warning descr="Wrapper URL artifactId 'foo' is not a canonical Maven wrapper artifact">foo</warning>-3.9.6-bin.zip</warning>
			""")
	void highlightsMultipleProblemsCoExisting(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=${prefix}/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void skipsInterpolationPlaceholderInPath(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://user:${PASS}@repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void skipsCredentialsCheckWhenPlaceholderMakesUrlUnparseable(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://alice<caret>:secret@repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void stripCredentialsFixRemovesUserInfo(PsiFile file) {

		invokeQuickFix(FIX_REMOVE_CREDENTIALS);

		assertThat(file).containsText(
				"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip")
				.doesNotContainText("alice", "secret");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.5<caret>-bin.zip
			""")
	void replaceVersionFixUsesPathVersion(PsiFile file) {

		invokeQuickFix("Use version '3.9.6'", 0);

		assertThat(file).containsText("/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.5<caret>-bin.zip
			""")
	void replaceVersionFixUsesFileVersion(PsiFile file) {

		invokeQuickFix("Use version '3.9.5'", 1);

		assertThat(file).containsText("/apache-maven/3.9.5/apache-maven-3.9.5-bin.zip");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/foo/3.9.6/bar<caret>-3.9.6-bin.zip
			""")
	void replaceArtifactFixSetsCanonical(PsiFile file) {

		invokeQuickFix("Set artifact to 'apache-maven'");

		assertThat(file).containsText("/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/com/example/wrong<caret>/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void replaceGroupPathFixSetsCanonical(PsiFile file) {

		invokeQuickFix("Set group to 'org/apache/maven'");

		assertThat(file).containsText("/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6<caret>.zip
			""")
	void replaceFileNameFixSetsCanonical(PsiFile file) {

		invokeQuickFix("Set file to 'apache-maven-3.9.6-bin.zip'");

		assertThat(file).containsText("/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			wrapperUrl=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2<caret>.zip
			""")
	void replaceFileNameFixForWrapperUrlUsesJar(PsiFile file) {

		invokeQuickFix("Set file to 'maven-wrapper-3.3.2.jar'");

		assertThat(file).containsText("/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=not a url at all<caret>
			""")
	void useDefaultUrlFixWhenCachePopulated(PsiFile file) {

		DependencyAssistantFixtures.setup(fixture.getProject());

		invokeQuickFix(FIX_USE_DEFAULT_URL);

		assertThat(file).containsText(
				"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=not a url at all<caret>
			""")
	void useDefaultUrlFixPrefersNonPreviewRelease(PsiFile file) {

		CachedArtifact artifact = new CachedArtifact("org.apache.maven", "apache-maven");
		artifact.getReleases().add(new CachedRelease("4.0.0-rc-1", "2026-03-01"));
		artifact.getReleases().add(new CachedRelease("3.9.9", "2024-10-04"));
		Cache cache = new Cache();
		cache.addArtifacts(List.of(artifact));
		StateService.getInstance(fixture.getProject()).setCache(cache);

		invokeQuickFix(FIX_USE_DEFAULT_URL);

		assertThat(file)
				.containsText("/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip")
				.doesNotContainText("4.0.0-rc-1");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=not a url at all<caret>
			""")
	void useDefaultUrlFixAbsentWhenCacheEmpty() {
		assertThat(quickFixLabels()).doesNotContain(FIX_USE_DEFAULT_URL);
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			wrapperUrl=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			validateDistributionUrl=true
			""")
	void computeShaFixAddsDistributionShaAfterDistributionUrl(PsiFile file) {

		PropertyImpl property = ReadAction.compute(() -> SyntaxTraverser.psiTraverser(file)
				.filter(PropertyImpl.class)
				.filter(it -> WrapperProperty.DISTRIBUTION.key().equals(it.getUnescapedKey()))
				.first());
		ProblemDescriptor descriptor = ReadAction.compute(() -> InspectionManager.getInstance(fixture.getProject())
				.createProblemDescriptor(property, "distributionUrl has no SHA-256 checksum", true,
						LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.WEAK_WARNING));

		new MavenWrapperChecksumQuickFix(WrapperProperty.DISTRIBUTION,
				(project, url) -> "82e35a63ceba37e9646434c5dd412ea577147f1e4a41ccde1614253187e3dbf9")
						.applyFix(fixture.getProject(), descriptor);

		assertThat(file).containsText(
				"""
						wrapperUrl=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
						distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
						distributionSha256Sum=82e35a63ceba37e9646434c5dd412ea577147f1e4a41ccde1614253187e3dbf9
						validateDistributionUrl=true
						""");
	}


	@SuppressWarnings("rawtypes")
	private void invokeQuickFix(String label) {
		invokeQuickFix(label, -1);

	}

	@SuppressWarnings("rawtypes")
	private void invokeQuickFix(String label, int position) {

		List<ProblemDescriptor> problems = inspectWrapperFile();

		assertThat(problems).isNotEmpty();

		int item = 0;
		for (ProblemDescriptor problem : problems) {

			QuickFix[] fixes = problem.getFixes();
			for (QuickFix fix : fixes) {

				if (item == position || position == -1) {
					invoke(fix, problem);
					return;
				}
				item++;
			}
		}

		fixture.launchAction(fixture.findSingleIntention(label));
	}

	private List<ProblemDescriptor> inspectWrapperFile() {
		return inspectWrapperFile(fixture.getFile());
	}

	private List<ProblemDescriptor> inspectWrapperFile(PsiFile file) {

		MavenWrapperUrlInspection inspection = new MavenWrapperUrlInspection();

		Project project = fixture.getProject();
		return ReadAction.compute(() -> {
			InspectionManager manager = InspectionManager.getInstance(project);
			ProblemsHolder holder = new ProblemsHolder(manager, file, true);
			PsiElementVisitor visitor = inspection.buildVisitor(holder, true);
			SyntaxTraverser.psiTraverser(file).forEach(visitor::visitElement);
			return holder.getResults();
		});
	}

	private void invoke(QuickFix fix, ProblemDescriptor problem) {

		WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> {
			if (fix instanceof ModCommandQuickFix qf) {
				MavenWrapperUrlFixes.WrapperUrlFix urlfix = (MavenWrapperUrlFixes.WrapperUrlFix) ReflectionUtil
						.getField(qf.getClass(), qf, ModCommandAction.class, "myAction");
				urlfix.invoke(ActionContext.from(problem), (PropertyImpl) problem.getStartElement(), null);
			} else {
				fix.applyFix(fixture.getProject(), problem);
			}
		});
	}

	private List<String> quickFixLabels() {
		return fixture.getAvailableIntentions().stream().map(IntentionAction::getText).toList();
	}

}
