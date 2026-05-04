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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.AvailableUpgrades;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.UpgradeSuggestion;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ProblemsHolder.ProblemBuilder;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * {@link LocalInspectionTool} that highlights outdated dependency versions in
 * supported build files and offers per-strategy quick fixes for update
 * qualified same-file declarations.
 *
 * @author Mark Paluch
 */
public class UpgradeAvailableInspection extends LocalInspectionTool {

	@Override
	public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {

		PsiFile file = holder.getFile();
		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(file.getProject(), file);
		if (context == null || context.isAbsent()) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}

		Set<PsiElement> reported = new HashSet<>();

		return new PsiElementVisitor() {

			@Override
			public void visitElement(PsiElement element) {

				if (!context.isVersionElement(element)) {
					return;
				}

				VersionUpgradeLookupSupport lookup = context.getLookup(element);
				AvailableUpgrades upgrades = lookup.suggestUpgrades(element);
				if (!upgrades.isPresent()) {
					return;
				}

				ArtifactDeclaration declaration = upgrades.getArtifactDeclaration();
				PsiElement versionLiteral = declaration.getVersionLiteral();
				if (versionLiteral == null) {
					return;
				}

				if (!reported.add(versionLiteral)) {
					return;
				}

				if (!upgrades.getArtifactDeclaration().isVersionDefinedInSameFile()) {
					return;
				}

				UpgradeSuggestion bestOption = upgrades.getUpgradeSuggestion();
				String strategy = MessageBundle.message("upgrage-strategy." + bestOption.getStrategy());
				String message = MessageBundle.message("inspection.upgrade-available.problem",
						"'" + declaration.getArtifactId().artifactId() + "'", strategy,
						bestOption.getRelease().getVersion());
				TextRange literalRange = versionLiteral.getTextRange();
				ProblemBuilder builder = holder.problem(versionLiteral, message);
				TextRange rangeInElement = context.getInterfaceAssistant().getHighlightRange(versionLiteral);

				if (!(rangeInElement.getEndOffset() > literalRange.getEndOffset() - literalRange.getStartOffset())) {
					builder.range(rangeInElement);
				}

				builder.fix((ModCommandAction) new UpgradeDependencyAction(element, context, bestOption));

				for (UpgradeSuggestion suggestion : upgrades.getUpgrades().values()) {
					if (suggestion == bestOption) {
						continue;
					}
					builder = builder
							.maybeFix((ModCommandAction) new UpgradeDependencyAction(element, context, suggestion));
				}

				builder.register();
			}

		};
	}

	private static boolean isVersionLiteralRewritable(PsiElement versionLiteral) {
		return isRewritableLiteralText(versionLiteral.getText());
	}

	/**
	 * Cheap text-shape filter testing whether the supplied literal text represents
	 * a version source the per-ecosystem updaters can safely rewrite.
	 * @param text the literal text including any surrounding quotes; may be
	 * {@literal null}.
	 * @return {@literal true} if the literal is safe to rewrite; {@literal false}
	 * otherwise.
	 */
	static boolean isRewritableLiteralText(@Nullable String text) {

		if (text == null || text.isEmpty()) {
			return false;
		}

		String rawValue = stripQuotes(text).trim();
		if (rawValue.isEmpty()) {
			return false;
		}

		// Maven rich-version ranges and NPM range expressions starting with [ or (.
		if (rawValue.charAt(0) == '[' || rawValue.charAt(0) == '(') {
			return false;
		}

		// Gradle dynamic versions and Maven 'LATEST'/'RELEASE' prefer-newer markers.
		if (rawValue.indexOf('+') >= 0 || rawValue.startsWith("latest.")) {
			return false;
		}

		// NPM out-of-scope shapes ('latest', '*', '||', tarballs, file:, link:,
		// workspace:).
		if (rawValue.equals("latest") || rawValue.equals("*") || rawValue.contains("||")) {
			return false;
		}

		// NPM x-range prefix versions like '2.x', '2.0.x', '2.0.X'.
		if (rawValue.endsWith(".x") || rawValue.endsWith(".X")) {
			return false;
		}

		return true;
	}

	private static String stripQuotes(String text) {
		if (text.length() < 2) {
			return text;
		}
		char first = text.charAt(0);
		char last = text.charAt(text.length() - 1);
		if ((first == '"' || first == '\'') && first == last) {
			return text.substring(1, text.length() - 1);
		}
		return text;
	}

	/**
	 * Quick fix that applies a single dependency update to the literal anchor.
	 */
	static class UpgradeQuickFix extends LocalQuickFixOnPsiElement {

		private final UpgradeStrategy strategy;

		private final DependencyUpdate update;

		private final String artifactDisplay;

		UpgradeQuickFix(PsiElement versionLiteral, UpgradeStrategy strategy, DependencyUpdate update,
				String artifactDisplay) {
			super(versionLiteral);
			this.strategy = strategy;
			this.update = update;
			this.artifactDisplay = artifactDisplay;
		}

		@Override
		public String getText() {
			String localizedStrategy = MessageBundle.message("dialog.upgradeTarget." + strategy);
			return MessageBundle.message("inspection.newerVersion.fix.text", artifactDisplay,
					update.version().toString(), localizedStrategy);
		}

		@Override
		public String getFamilyName() {
			return MessageBundle.message("inspection.newerVersion.familyName." + familyKey());
		}

		private String familyKey() {
			if (strategy == UpgradeStrategy.RELEASE) {
				return UpgradeStrategy.LATEST.name().toLowerCase(Locale.ROOT);
			}

			return strategy.name().toLowerCase(Locale.ROOT);
		}

		@Override
		public void invoke(Project project, PsiFile file, PsiElement startElement, PsiElement endElement) {

			ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(project, file);
			if (context == null) {
				return;
			}
			context.applyUpdate(startElement, update);
		}

		@Override
		public IntentionPreviewInfo generatePreview(Project project, ProblemDescriptor previewDescriptor) {
			// Default behaviour: clone the file, run invoke on the clone, render the diff.
			return super.generatePreview(project, previewDescriptor);
		}

	}

}
