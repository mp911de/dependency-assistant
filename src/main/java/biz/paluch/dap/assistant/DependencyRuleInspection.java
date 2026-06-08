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

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.RuleService;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.MessageBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.icons.AllIcons;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * {@link LocalInspectionTool} that flags {@link DependencyRule} violations.
 *
 * <p>The governing rule is resolved through {@link RuleService}. The inspection
 * is advisory and warning-only: it stays silent for artifacts that no rule
 * governs and produces nothing when the project has no
 * {@code dependencyfile.json} descriptor. When the release cache holds a
 * version matching the governing generation, it offers a batchable quick fix
 * that realigns the declaration to the newest compliant release.
 *
 * @author Mark Paluch
 */
public class DependencyRuleInspection extends LocalInspectionTool {

	@Override
	public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {

		PsiFile file = holder.getFile();
		Project project = file.getProject();
		VirtualFile virtualFile = file.getVirtualFile();
		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(project, file);
		if (context.isAbsent()) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}

		StateService stateService = StateService.getInstance(project);
		Cache cache = stateService.getCache();
		RuleService rules = RuleService.getInstance(project);
		Versioned projectVersion = context.getProjectVersion();
		Set<PsiElement> reported = new HashSet<>();

		return new ArtifactReferenceVisitor(context, file) {

			@Override
			public void visitArtifactReference(PsiElement element, ArtifactReference reference) {

				ArtifactDeclaration declaration = reference.getDeclaration();
				PsiElement versionLiteral = declaration.getVersionLiteral();
				if (versionLiteral == null || !declaration.isVersionDefined()) {
					return;
				}

				if (!reported.add(versionLiteral)) {
					return;
				}

				ArtifactVersion version = declaration.getVersion();
				DependencyRule rule = rules.resolve(declaration.getArtifactId(), project, virtualFile,
						projectVersion);
				if (rule.test(version)) {
					return;
				}

				String message = MessageBundle.message("inspection.dependency-rule.problem",
						declaration.getArtifactId().artifactId(),
						context.getInterfaceAssistant()
								.getDocumentationText(version), rule.getGeneration());
				AlignGenerationQuickFix fix = AlignGenerationQuickFix.findFix(reference, rule, cache, context);

				if (fix == null) {
					holder.registerProblem(versionLiteral, message);
					return;
				}
				holder.problem(versionLiteral, message).fix((ModCommandAction) fix)
						.register();
			}

		};
	}

	/**
	 * Quick fix that realigns a drifting version literal to the newest cached
	 * release matching the governing generation.
	 */
	static class AlignGenerationQuickFix extends UpdateDependencyAction implements Iconable {

		private final ArtifactDeclaration declaration;

		private final DependencyUpdate update;

		AlignGenerationQuickFix(ArtifactDeclaration declaration, ProjectDependencyContext context,
				DependencyUpdate update) {
			super(declaration, context, update);
			this.declaration = declaration;
			this.update = update;
		}

		public static @Nullable AlignGenerationQuickFix findFix(ArtifactReference reference, DependencyRule rule,
				Cache cache,
				ProjectDependencyContext context) {

			if (reference.getDeclaration().getVersionLiteral() == null) {
				return null;
			}
			List<Release> releases = cache.getReleases(reference.getArtifactId());
			Release target = releases.stream()
					.filter(release -> rule.test(release.getVersion()))
					.max(Comparator.naturalOrder()).orElse(null);
			if (target == null) {
				return null;
			}
			DependencyUpdate update = DependencyUpdate.from(reference.toDependency(), target);

			return new AlignGenerationQuickFix(reference.getDeclaration(), context, update);
		}

		@Override
		protected Presentation getPresentation(ActionContext context, PsiElement element) {
			String message = MessageBundle.message("inspection.dependency-rule.fix.name",
					declaration.getArtifactId().artifactId(), update.versionAsString());
			return Presentation.of(message).withIcon(AllIcons.General.GreenCheckmark);
		}

		@Override
		public String getFamilyName() {
			return MessageBundle.message("inspection.dependency-rule.fix.family");
		}

		@Override
		protected Icon getVersionAgeIcon() {
			return AllIcons.General.GreenCheckmark;
		}

		@Override
		public Icon getIcon() {
			return super.getIcon();
		}

		@Override
		public Icon getIcon(int i) {
			return getIcon();
		}

	}

}
