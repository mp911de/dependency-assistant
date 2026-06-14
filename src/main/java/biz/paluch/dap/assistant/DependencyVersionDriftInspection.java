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
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.MessageBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
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
 * {@link LocalInspectionTool} that flags dependency drift: an artifact declared
 * at more than one distinct version or through mixed declaration styles across
 * the project's modules.
 *
 * <p>The open file contributes its live, in-editor version and version source
 * straight from PSI, while versions and version sources from other modules are
 * read from the in-memory
 * {@link StateService#getDeclaredVersions(ArtifactId, biz.paluch.dap.state.ProjectId)
 * project state}. Reading the current file live keeps the inspection consistent
 * with unsaved edits, whose module state is only re-indexed on save, while
 * still reusing the cheap cached cross-module set. Only declarations that
 * resolve to a concrete version are flagged; ranges and dynamic versions are
 * out of scope. Version drift can be reconciled to the highest or lowest
 * declared version; pure declaration drift is reported without an automated
 * quick fix.
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
				if (versionLiteral == null || !declaration.isVersionDefinedInSameFile()
						|| !declaration.isVersionDefined()) {
					return;
				}

				VersionSource versionSource = declaration.getVersionSource();
				boolean catalogProperty = versionSource instanceof VersionSource.VersionCatalog
						&& versionSource instanceof VersionSource.VersionProperty;

				// A version-catalog [versions] entry is a shared version definition, not a
				// declaration site. Skip the definition itself and report drift on the
				// library aliases that reference it through version.ref, whose version
				// literal points back into the [versions] table.
				if (catalogProperty && element == versionLiteral) {
					return;
				}

				PsiElement anchor = catalogProperty ? element : versionLiteral;
				if (!reported.add(anchor)) {
					return;
				}

				Set<ArtifactVersion> declaredVersions = state.getDeclaredVersions(declaration.getArtifactId(),
						context.getProjectId());
				declaredVersions.add(declaration.getVersion());

				Set<VersionSource> versionSources = state.getVersionSources(
						project -> !context.getProjectId().equals(project), declaration.getArtifactId());
				versionSources.add(versionSource);

				boolean versionDrift = declaredVersions.size() > 1;
				boolean declarationDrift = hasDeclarationDrift(versionSources);
				if (!versionDrift && !declarationDrift) {
					return;
				}

				if (!versionDrift) {
					String message = MessageBundle.message("inspection.version-drift.declaration.problem",
							declaration.getArtifactId());
					holder.registerProblem(anchor, message, ProblemHighlightType.WEAK_WARNING);
					return;
				}

				ArtifactVersion highest = Collections.max(declaredVersions);
				ArtifactVersion lowest = Collections.min(declaredVersions);
				String versions = declaredVersions.stream().map(Object::toString)
						.collect(Collectors.joining(", "));

				String message = MessageBundle.message(declarationDrift
						? "inspection.version-drift.version-and-declaration.problem"
						: "inspection.version-drift.problem", declaration.getArtifactId(), versions);

				holder.registerProblem(anchor, message,
						new AlignVersionAction(context, toUpdate(declaration, highest), true),
						new AlignVersionAction(context, toUpdate(declaration, lowest), false));
			}

			private DependencyUpdate toUpdate(ArtifactDeclaration declaration, ArtifactVersion target) {
				return DependencyUpdate.create(declaration.getArtifactId(), target,
						declaration.getDeclarationSource(), declaration.getVersionSource());
			}

		};
	}

	private static boolean hasDeclarationDrift(Iterable<VersionSource> versionSources) {

		boolean inline = false;
		boolean property = false;
		for (VersionSource versionSource : versionSources) {
			if (versionSource instanceof VersionSource.DeclaredVersion) {
				inline = true;
			}
			if (versionSource instanceof VersionSource.VersionProperty) {
				property = true;
			}
		}
		return inline && property;
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
