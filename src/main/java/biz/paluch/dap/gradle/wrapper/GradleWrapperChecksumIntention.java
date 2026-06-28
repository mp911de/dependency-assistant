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

import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jspecify.annotations.Nullable;

/**
 * Intention that computes and inserts a missing Gradle wrapper checksum.
 *
 * @author Mark Paluch
 */
public class GradleWrapperChecksumIntention extends BaseIntentionAction implements DumbAware {

	private final WrapperProperty property;

	public GradleWrapperChecksumIntention() {
		this.property = WrapperProperty.DISTRIBUTION;
	}

	@Override
	public String getFamilyName() {
		return MessageBundle.message("gradle.wrapper.checksum.intention-family");
	}

	@Override
	public String getText() {
		return MessageBundle.message("wrapper.checksum.intention.add.text", property.key());

	}

	@Override
	public boolean isAvailable(Project project, Editor editor, PsiFile file) {

		PropertyImpl urlProperty = findUrlProperty(project, file, property);
		if (urlProperty == null) {
			return false;
		}

		return StringUtils.hasText(findSha(urlProperty));
	}

	@Override
	public IntentionPreviewInfo generatePreview(Project project, Editor editor, PsiFile file) {

		PropertyImpl urlProperty = findUrlProperty(project, file, property);
		if (urlProperty == null) {
			return IntentionPreviewInfo.EMPTY;
		}

		GradleWrapperEntry entry = GradleWrapperParser.parse(urlProperty);
		if (entry == null || entry.version() == null) {
			return IntentionPreviewInfo.EMPTY;
		}
		StateService stateService = StateService.getInstance(project);
		String sha = GradleWrapperUtils.findSha(property.artifactId(), entry.getVersion(), stateService);
		if (sha == null) {
			return IntentionPreviewInfo.EMPTY;
		}

		Document document = editor.getDocument();
		String modified = insertedText(document, editor.getCaretModel().getOffset(),
				property.shaKey() + "=" + sha);
		return new IntentionPreviewInfo.CustomDiff(PropertiesFileType.INSTANCE, file.getName(), document.getText(),
				modified);
	}

	@Override
	public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {

		PropertyImpl urlProperty = findUrlProperty(project, file, property);
		if (urlProperty == null) {
			return;
		}
		String sha = findSha(urlProperty);

		if (!StringUtils.hasText(sha)) {
			return;
		}

		Document document = editor.getDocument();
		WriteCommandAction.runWriteCommandAction(project, MessageBundle.message("wrapper.checksum.command"), null,
				() -> {
					insert(document, editor.getCaretModel().getOffset(), property.shaKey() + "=" + sha);
					PsiDocumentManager.getInstance(project).commitDocument(document);
				});
	}

	private @Nullable String findSha(PropertyImpl property) {

		GradleWrapperEntry entry = GradleWrapperParser.parse(property);
		if (entry == null || entry.version() == null) {
			return null;
		}

		StateService stateService = StateService.getInstance(property.getProject());
		return GradleWrapperUtils.findSha(this.property.artifactId(), entry.getVersion(), stateService);
	}

	private static @Nullable PropertyImpl findUrlProperty(Project project, PsiFile file, WrapperProperty property) {

		if (!GradleWrapperUtils.isWrapperFile(file)
				|| !(file instanceof PropertiesFile properties)
				|| properties.findPropertyByKey(property.shaKey()) != null) {
			return null;
		}

		IProperty candidate = properties.findPropertyByKey(property.key());
		if (!(candidate instanceof PropertyImpl urlProperty)) {
			return null;
		}

		String decodedValue = urlProperty.getUnescapedValue();
		if (StringUtils.isEmpty(decodedValue)
				|| !GradleWrapperUrlAnalyzer.analyze(decodedValue, urlProperty.getText()).isEmpty()) {
			return null;
		}
		return urlProperty;
	}

	static String insertedText(Document document, int caretOffset, String propertyText) {

		StringBuilder text = new StringBuilder(document.getText());
		int line = document.getLineNumber(caretOffset);
		int lineStart = document.getLineStartOffset(line);
		int lineEnd = document.getLineEndOffset(line);
		String lineText = document.getText().substring(lineStart, lineEnd);
		if (lineText.isBlank()) {
			int replaceEnd = lineEnd < document.getTextLength() ? lineEnd + 1 : lineEnd;
			text.replace(lineStart, replaceEnd, propertyText + "\n");
		} else if (lineEnd < document.getTextLength()) {
			text.insert(lineEnd + 1, propertyText + "\n");
		} else {
			text.insert(lineEnd, "\n" + propertyText + "\n");
		}
		return text.toString();
	}

	private static void insert(Document document, int caretOffset, String propertyText) {

		int line = document.getLineNumber(caretOffset);
		int lineStart = document.getLineStartOffset(line);
		int lineEnd = document.getLineEndOffset(line);
		String lineText = document.getText().substring(lineStart, lineEnd);
		if (lineText.isBlank()) {
			int replaceEnd = lineEnd < document.getTextLength() ? lineEnd + 1 : lineEnd;
			document.replaceString(lineStart, replaceEnd, propertyText + "\n");
		} else if (lineEnd < document.getTextLength()) {
			document.insertString(lineEnd + 1, propertyText + "\n");
		} else {
			document.insertString(lineEnd, "\n" + propertyText + "\n");
		}
	}

}
