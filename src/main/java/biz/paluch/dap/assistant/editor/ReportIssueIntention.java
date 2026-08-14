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

import javax.swing.Icon;

import biz.paluch.dap.assistant.ArtifactReferenceContext;
import biz.paluch.dap.metadata.ProjectMetadata;
import biz.paluch.dap.metadata.ProjectMetadataService;
import biz.paluch.dap.util.HttpClientUtil;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.PsiElements;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jspecify.annotations.Nullable;

/**
 * Intention that opens the issue tracker of the dependency declared at the
 * caret in the browser.
 *
 * @author Mark Paluch
 */
public class ReportIssueIntention extends BaseIntentionAction implements PriorityAction, Iconable {

	@Override
	public String getFamilyName() {
		return MessageBundle.message("intention.ReportIssue.family");
	}

	@Override
	public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {

		ArtifactReferenceContext context = resolveContext(editor, psiFile);
		if (context == null) {
			return false;
		}
		ProjectMetadata metadata = ProjectMetadataService.getMetadata(context.getDeclaration());
		if (metadata.getIssueTrackerUrl() == null) {
			return false;
		}

		setText(MessageBundle.message("intention.ReportIssue.text", context.getPresentation()
				.getDisplayName()));
		return true;
	}

	@Override
	public boolean startInWriteAction() {
		return false;
	}

	@Override
	public IntentionPreviewInfo generatePreview(Project project, Editor editor, PsiFile file) {
		return IntentionPreviewInfo.EMPTY;
	}

	@Override
	public void invoke(Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {

		ArtifactReferenceContext context = resolveContext(editor, psiFile);
		if (context == null) {
			return;
		}

		ProjectMetadata metadata = ProjectMetadataService.getMetadata(context.getDeclaration());
		String url = metadata.getIssueTrackerUrl();
		if (url != null) {
			HttpClientUtil.openBrowser(url);
		}
	}

	@Override
	public Priority getPriority() {
		return Priority.BOTTOM;
	}

	@Override
	public Icon getIcon(int flags) {
		return AllIcons.Actions.Report;
	}

	/**
	 * Resolve the declaration at the caret; {@literal null} when the caret is not
	 * on a resolved, version-defined dependency declaration.
	 */
	static @Nullable ArtifactReferenceContext resolveContext(Editor editor, PsiFile psiFile) {

		PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
		if (element == null) {
			return null;
		}

		ArtifactReferenceContext context = ArtifactReferenceContext.from(PsiElements.unleaf(element));
		return context.isAbsent() ? null : context;
	}

}
