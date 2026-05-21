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

package biz.paluch.dap.gradle.wrapper;

import java.util.Arrays;
import java.util.List;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.QuickFix;
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
 * {@link GradleWrapperUrlInspection}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GradleWrapperUrlInspectionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		fixture.enableInspections(GradleWrapperUrlInspection.class);
		GradleWrapperFixtures.setup(fixture.getProject());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https://<warning descr="Wrapper URL contains plaintext credentials">alice:secret@</warning>services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void highlightsCredentialsInUrl(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=<warning descr="Wrapper URL does not match the expected Gradle distribution shape">not a url at all</warning>
			""")
	void highlightsInvalidUrl(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https://services.gradle.org/distributions/<warning descr="Wrapper URL artifactId 'wrapper' is not the canonical Gradle distribution artifact">wrapper</warning>-8.14.3-bin.zip
			""")
	void highlightsUnknownArtifact(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=<weak_warning descr="distributionUrl has no SHA-256 checksum">https://services.gradle.org/distributions/<warning descr="Wrapper URL file name 'gradle-8.14.3.zip' does not follow the canonical pattern">gradle-8.14.3.zip</warning></weak_warning>
			""")
	void highlightsMalformedFileName(PsiFile file) {
		fixture.testHighlighting(true, false, true, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=<weak_warning descr="distributionUrl has no SHA-256 checksum">https://services.gradle.org/distributions/gradle-8.14.3-bin.zip</weak_warning>
			""")
	void highlightsMissingDistributionSha(PsiFile file) {
		fixture.testHighlighting(true, false, true, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			distributionSha256Sum=
			""")
	void skipsMissingDistributionShaWhenShaPropertyExists(PsiFile file) {
		fixture.testHighlighting(true, false, true, file.getVirtualFile());
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=https://services.gradle.org/distributions/gradle-8.14.3<caret>-bin.zip
			""")
	void missingDistributionShaOffersComputeFix() {
		assertThat(problemQuickFixLabels()).contains("Add SHA-256 checksum");
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https://services.gradle.org/distributions/gradle-8.___IntellijIdeaRulezzz___-bin.zip
			""")
	void skipsCompletionPlaceholder(PsiFile file) {
		fixture.testHighlighting(true, false, true, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=ftp://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void skipsMissingDistributionShaForUnsupportedSchemes(PsiFile file) {
		fixture.testHighlighting(true, false, true, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "application.properties", content = """
			distributionUrl=http://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void ignoresNonWrapperFiles(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=https://alice<caret>:secret@services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void stripCredentialsFixRemovesUserInfo(PsiFile file) {

		invokeQuickFix(0, 0);

		assertThat(file)
				.containsText("https://services.gradle.org/distributions/gradle-8.14.3-bin.zip")
				.doesNotContainText("alice", "secret");
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=https://services.gradle.org/distributions/wrapper-8.14.3<caret>-bin.zip
			""")
	void replaceUnknownArtifactFixSetsCanonicalFileName(PsiFile file) {

		invokeQuickFix(0, 0);

		assertThat(file).containsText("https://services.gradle.org/distributions/gradle-8.14.3-bin.zip");
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=https://services.gradle.org/distributions/gradle-8.14.3<caret>.zip
			""")
	void replaceMalformedFileNameFixSetsCanonicalFileName(PsiFile file) {

		invokeQuickFix(0, 0);

		assertThat(file).containsText("https://services.gradle.org/distributions/gradle-8.14.3-bin.zip");
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=not a <caret>url at all
			""")
	void invalidUrlOffersDefaultUrlFixWhenCacheIsEmpty(PsiFile file) {

		invokeQuickFix(0, 0);

		assertThat(file).containsText("https://services.gradle.org/distributions/gradle-9.5.1-bin.zip");
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			networkTimeout=10000
			distributionUrl=https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
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

		new GradleWrapperChecksumQuickFix(WrapperProperty.DISTRIBUTION,
				"82e35a63ceba37e9646434c5dd412ea577147f1e4a41ccde1614253187e3dbf9")
						.applyFix(fixture.getProject(), descriptor);

		assertThat(file).containsText("""
				networkTimeout=10000
				distributionUrl=https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
				distributionSha256Sum=82e35a63ceba37e9646434c5dd412ea577147f1e4a41ccde1614253187e3dbf9
				validateDistributionUrl=true
				""");
	}

	private void invokeQuickFix(int problemIndex, int fixIndex) {

		List<ProblemDescriptor> problems = inspectionProblems();
		assertThat(problems).hasSizeGreaterThan(problemIndex);
		QuickFix[] fixes = problems.get(problemIndex).getFixes();
		assertThat(fixes).hasSizeGreaterThan(fixIndex);
		invoke(fixes[fixIndex], problems.get(problemIndex));
	}

	private List<ProblemDescriptor> inspectionProblems() {

		GradleWrapperUrlInspection inspection = new GradleWrapperUrlInspection();
		Project project = fixture.getProject();
		PsiFile file = fixture.getFile();
		return ReadAction.compute(() -> {
			InspectionManager manager = InspectionManager.getInstance(project);
			ProblemsHolder holder = new ProblemsHolder(manager, file, true);
			PsiElementVisitor visitor = inspection.buildVisitor(holder, true);
			SyntaxTraverser.psiTraverser(file).forEach(visitor::visitElement);
			return holder.getResults();
		});
	}

	@SuppressWarnings("rawtypes")
	private void invoke(QuickFix fix, ProblemDescriptor problem) {

		WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> {
			if (fix instanceof ModCommandQuickFix qf) {
				GradleWrapperUrlFixes.WrapperUrlFix urlfix = (GradleWrapperUrlFixes.WrapperUrlFix) ReflectionUtil
						.getField(qf.getClass(), qf, ModCommandAction.class, "myAction");
				urlfix.invoke(ActionContext.from(problem), (PropertyImpl) problem.getStartElement(), null);
			} else {
				fix.applyFix(fixture.getProject(), problem);
			}
		});
	}

	private List<String> problemQuickFixLabels() {

		return inspectionProblems().stream()
				.flatMap(problem -> Arrays.stream(problem.getFixes()))
				.map(QuickFix::getName)
				.toList();
	}

}
