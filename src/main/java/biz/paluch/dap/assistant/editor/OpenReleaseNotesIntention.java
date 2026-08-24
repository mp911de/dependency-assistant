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

import java.net.URI;

import javax.swing.Icon;

import biz.paluch.dap.assistant.ArtifactReferenceContext;
import biz.paluch.dap.metadata.ProjectMetadata;
import biz.paluch.dap.metadata.ProjectMetadataService;
import biz.paluch.dap.util.HttpClientUtil;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

/**
 * Intention that opens the release notes of the declared dependency version in
 * the browser.
 *
 * <p>Availability is cache-only and version-specific: the intention shows up
 * only when the {@link ProjectMetadataService} facade finds a cached repository
 * tag representing the declared version and the hosting platform can render a
 * release-notes URL for that tag.
 *
 * @author Mark Paluch
 * @see ReportIssueIntention
 */
public class OpenReleaseNotesIntention extends BaseIntentionAction implements PriorityAction, Iconable {

	@Override
	public String getFamilyName() {
		return MessageBundle.message("intention.OpenReleaseNotes.family");
	}

	@Override
	public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {

		ArtifactReferenceContext context = ReportIssueIntention.resolveContext(editor, psiFile);
		if (context == null) {
			return false;
		}

		ProjectMetadata projectMetadata = context.getProjectMetadata();
		if (projectMetadata.findReleaseNotesUrl(context.getVersion()) == null) {
			return false;
		}

		setText(MessageBundle.message("intention.OpenReleaseNotes.text",
				context.getPresentation().getDisplayName(), context.getVersion()));
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

		ArtifactReferenceContext context = ReportIssueIntention.resolveContext(editor, psiFile);
		if (context == null) {
			return;
		}
		ProjectMetadata projectMetadata = context.getProjectMetadata();
		URI releaseNotesUrl = projectMetadata.findReleaseNotesUrl(context.getVersion());
		if (releaseNotesUrl == null) {
			return;
		}

		HttpClientUtil.openBrowser(releaseNotesUrl);
	}

	@Override
	public Priority getPriority() {
		return Priority.BOTTOM;
	}

	@Override
	public Icon getIcon(int flags) {
		return AllIcons.Toolwindows.Documentation;
	}

}
