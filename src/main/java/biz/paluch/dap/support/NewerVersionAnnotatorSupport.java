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
package biz.paluch.dap.support;

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.NewerVersionSeveritiesProvider;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

/**
 * Annotator that applies a highlight to version text in a build file when a newer version is available in the cache.
 * <p>
 * Complements {@code NewerVersionLineMarkerProvider}: the gutter icon provides a click target while this annotation
 * draws the reader's eye directly to the outdated version string in the editor.
 *
 * @author Mark Paluch
 */
public abstract class NewerVersionAnnotatorSupport implements Annotator {

	private final IntentionAction action;
	private final HighlightSeverity severity;

	protected NewerVersionAnnotatorSupport(IntentionAction action, HighlightSeverity severity) {
		this.action = action;
		this.severity = severity;
	}

	@Override
	public void annotate(PsiElement element, AnnotationHolder holder) {

		VersionUpgradeLookupSupport service = getVersionLookupSupport(element);
		VersionUpgradeLookupSupport.UpgradeAvailable availableUpgrade = service.findAvailableUpgrade(element);
		if (availableUpgrade == null) {
			return;
		}

		VersionUpgradeLookupSupport.UpgradeSuggestion upgradeSuggestion = availableUpgrade.suggestion();

		IntentionAction action = this.action;
		String message = upgradeSuggestion.getMessage();
		if (availableUpgrade.metadata() != null && !availableUpgrade.metadata().localVersionDeclared()
				&& availableUpgrade.metadata().versionLocation() != null) {

			LocalFileSystem lfs = LocalFileSystem.getInstance();
			VirtualFile virtualFile = lfs.findFileByPath(availableUpgrade.metadata().versionLocation());
			if (virtualFile != null) {

				message = MessageBundle.message("gutter.declaration.file", virtualFile.getName()) + System.lineSeparator()
						+ message;
				action = null;
			}
		}

		AnnotationBuilder builder = holder.newAnnotation(severity, message).range(getTextRange(element))
				.textAttributes(NewerVersionSeveritiesProvider.NEWER_VERSION_KEY);

		if (action != null) {
			builder = builder.withFix(action);
		}

		builder.create();
	}

	protected abstract VersionUpgradeLookupSupport getVersionLookupSupport(PsiElement element);

	protected TextRange getTextRange(PsiElement element) {
		return element.getTextRange();
	}

}
