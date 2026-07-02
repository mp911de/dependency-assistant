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

package biz.paluch.dap.assistant.editor;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.assistant.ArtifactReferenceContext;
import biz.paluch.dap.assistant.ArtifactReferenceContextVisitor;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.rule.DependencyfileService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;

/**
 * {@link LocalInspectionTool} that flags {@link DependencyRule} violations.
 *
 * <p>The governing rule is resolved through {@link DependencyfileService}. The
 * inspection is advisory and warning-only: it stays silent for artifacts that
 * no rule governs and produces nothing when the project has no
 * {@code dependencyfile.json} descriptor. When the release cache holds a
 * version matching the governing generation, it offers a batchable quick fix
 * that realigns the declaration to the newest compliant release.
 *
 * @author Mark Paluch
 */
public class DependencyRuleInspection extends LocalInspectionTool implements Iconable {

	@Override
	public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {

		PsiFile file = holder.getFile();
		Project project = file.getProject();
		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(project, file);

		if (context.isAbsent()) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}

		return new ArtifactReferenceContextVisitor(context, file) {

			@Override
			public void visitArtifactReference(PsiElement element, ArtifactReferenceContext artifactContext) {

				ArtifactDeclaration declaration = artifactContext.getDeclaration();
				PsiElement versionLiteral = declaration.getVersionLiteral();
				if (versionLiteral == null || !declaration.isVersionDefined()) {
					return;
				}

				ArtifactVersion version = declaration.getVersion();
				DependencyRuleEvaluator evaluator = artifactContext.getEvaluator();

				if (evaluator.test(version)) {
					return;
				}

				Releases releases = artifactContext.getReleases();
				Release remediation = evaluator.getRule().suggestRemediation(releases);
				String message = MessageBundle.message("inspection.dependency-rule.problem",
						evaluator.getDependencyName(), version.toDocumentationString(),
						evaluator.getRule().getGenerations().value());

				if (remediation != null) {
					message += " " + MessageBundle.message("inspection.dependency-rule.remediation.message",
							remediation.getVersion().toDocumentationString());
				}

				if (declaration.getVersionLiteral() == null || remediation == null) {
					holder.registerProblem(element, message);
					return;
				}

				DependencyUpdate update = DependencyUpdate.from(declaration.toDependency(), remediation);
				AlignGenerationQuickFix fix = new AlignGenerationQuickFix(artifactContext, update);

				holder.problem(element, message).fix(fix).register();
			}

		};
	}

	@Override
	public Icon getIcon(int flags) {
		return DependencyAssistantIcons.ICON;
	}

	/**
	 * Quick fix that realigns a drifting version literal to the newest cached
	 * release matching the governing generation.
	 */
	static class AlignGenerationQuickFix extends UpdateDependencyVersionQuickFix implements Iconable {

		AlignGenerationQuickFix(ArtifactReferenceContext context,
				DependencyUpdate update) {
			super(UpgradeStrategy.RULE, context, update);
		}

		@Override
		public String getFamilyName() {
			return MessageBundle.message("inspection.dependency-rule.fix.family");
		}

		@Override
		public Icon getIcon() {
			return AllIcons.General.GreenCheckmark;
		}

		@Override
		public Icon getIcon(int i) {
			return getIcon();
		}

	}

}
