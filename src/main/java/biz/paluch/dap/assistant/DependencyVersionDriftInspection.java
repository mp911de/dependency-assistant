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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.MessageBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;

/**
 * {@link LocalInspectionTool} that flags dependency version drift: an artifact
 * declared at more than one distinct version across the project's modules.
 *
 * <p>The project-wide declared-version set is read from the in-memory
 * {@link StateService#getDeclaredVersions(ArtifactId) project state}; only
 * declarations that resolve cleanly through the per-ecosystem
 * {@link VersionUpgradeLookup} are flagged. Each drifting version literal in
 * the open file is highlighted and can be reconciled to the highest or lowest
 * declared version.
 *
 * @author Mark Paluch
 */
public class DependencyVersionDriftInspection extends LocalInspectionTool implements Iconable {

	@Override
	public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {

		PsiFile file = holder.getFile();
		Project project = file.getProject();
		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(project, file);
		if (context.isAbsent()) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}

		StateService state = StateService.getInstance(project);
		Set<PsiElement> reported = new HashSet<>();

		return new ArtifactReferenceVisitor(context, file) {

			@Override
			public void visitArtifactReference(PsiElement element, ArtifactReference reference) {

				ArtifactDeclaration declaration = reference.getDeclaration();
				PsiElement versionLiteral = declaration.getVersionLiteral();
				if (versionLiteral == null || !declaration.isVersionDefinedInSameFile()) {
					return;
				}

				if (!reported.add(versionLiteral)) {
					return;
				}

				Set<ArtifactVersion> declaredVersions = state.getDeclaredVersions(declaration.getArtifactId());
				if (declaredVersions.size() <= 1) {
					return;
				}

				ArtifactVersion highest = Collections.max(declaredVersions);
				ArtifactVersion lowest = Collections.min(declaredVersions);
				String versions = declaredVersions.stream().map(Object::toString)
						.collect(Collectors.joining(", "));

				String message = MessageBundle.message("inspection.version-drift.problem",
						declaration.getArtifactId(), versions);

				holder.registerProblem(versionLiteral, message,
						new AlignVersionAction(context, toUpdate(declaration, highest), true),
						new AlignVersionAction(context, toUpdate(declaration, lowest), false));
			}

			private DependencyUpdate toUpdate(ArtifactDeclaration declaration, ArtifactVersion target) {
				return DependencyUpdate.create(declaration.getArtifactId(), target,
						declaration.getDeclarationSource(), declaration.getVersionSource());
			}

		};
	}

	@Override
	public Icon getIcon(int flags) {
		return DependencyAssistantIcons.ICON;
	}

	/**
	 * Quick fix that reconciles a drifting artifact to a chosen declared version by
	 * rewriting every occurrence of that artifact in the file.
	 */
	static class AlignVersionAction extends ModCommandQuickFix {

		private final ProjectDependencyContext context;

		private final DependencyUpdate update;

		private final boolean highest;

		AlignVersionAction(ProjectDependencyContext context, DependencyUpdate update, boolean highest) {
			this.context = context;
			this.update = update;
			this.highest = highest;
		}

		@Override
		public ModCommand perform(Project project, ProblemDescriptor descriptor) {
			PsiElement versionLiteral = descriptor.getPsiElement();
			return ModCommand.psiUpdate(versionLiteral,
					element -> context.applyUpdates(element.getContainingFile(), List.of(update)));
		}

		@Override
		public String getName() {
			return MessageBundle.message(messageKey(), update.version().toString());
		}

		@Override
		public @IntentionFamilyName String getFamilyName() {
			return MessageBundle.message(messageKey() + ".family");
		}

		private String messageKey() {
			return highest ? "inspection.version-drift.fix.highest" : "inspection.version-drift.fix.lowest";
		}

	}

}
