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

package biz.paluch.dap.maven.wrapper;

import java.io.IOException;

import biz.paluch.dap.assistant.Notifications;
import biz.paluch.dap.maven.wrapper.MavenWrapperChecksumQuickFix.ChecksumComputer;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.trustedProjects.TrustedProjects;
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
 * Intention that computes and inserts a missing Maven wrapper checksum.
 *
 * @author Mark Paluch
 */
public class MavenWrapperChecksumIntention extends BaseIntentionAction implements DumbAware {

	private static final String PREVIEW_VALUE = "<computing...>";

	private final WrapperProperty property;

	private final ChecksumComputer checksumComputer;

	MavenWrapperChecksumIntention(WrapperProperty property) {
		this(property, WrapperChecksumDownloader::downloadAndComputeSha);
	}

	MavenWrapperChecksumIntention(WrapperProperty property, ChecksumComputer checksumComputer) {
		this.property = property;
		this.checksumComputer = checksumComputer;
	}

	@Override
	public String getFamilyName() {
		return MessageBundle.message("maven.wrapper.checksum.intention-family");
	}

	@Override
	public String getText() {
		return MessageBundle.message("wrapper.checksum.intention.text", property.key());
	}

	@Override
	public boolean isAvailable(Project project, Editor editor, PsiFile file) {
		return findUrlProperty(project, file, property) != null;
	}

	@Override
	public IntentionPreviewInfo generatePreview(Project project, Editor editor, PsiFile file) {

		if (findUrlProperty(project, file, property) == null) {
			return IntentionPreviewInfo.EMPTY;
		}

		Document document = editor.getDocument();
		String modified = insertedText(document, editor.getCaretModel().getOffset(),
				property.shaKey() + "=" + PREVIEW_VALUE);
		return new IntentionPreviewInfo.CustomDiff(PropertiesFileType.INSTANCE, file.getName(), document.getText(),
				modified);
	}

	@Override
	public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {

		PropertyImpl urlProperty = findUrlProperty(project, file, property);
		if (urlProperty == null) {
			return;
		}

		String url = urlProperty.getUnescapedValue();
		String sha;
		try {
			sha = checksumComputer.compute(project, url);
		} catch (IOException ex) {
			Notifications.error(project, MessageBundle.message("wrapper.checksum.error.title"),
					MessageBundle.message("wrapper.checksum.error", url, Notifications.errorMessage(ex)));
			return;
		}

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

	private static @Nullable PropertyImpl findUrlProperty(Project project, PsiFile file, WrapperProperty property) {

		if (!TrustedProjects.isProjectTrusted(project) || !MavenWrapperUtils.isWrapperFile(file)
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
				|| !MavenWrapperUrlAnalyzer.isChecksumCandidate(decodedValue, urlProperty.getText())
				|| !MavenWrapperUrlAnalyzer.analyze(property, decodedValue, urlProperty.getText()).isEmpty()) {
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

	public static class Distribution extends MavenWrapperChecksumIntention {

		public Distribution() {
			super(WrapperProperty.DISTRIBUTION);
		}

		Distribution(ChecksumComputer checksumComputer) {
			super(WrapperProperty.DISTRIBUTION, checksumComputer);
		}

	}

	public static class Wrapper extends MavenWrapperChecksumIntention {

		public Wrapper() {
			super(WrapperProperty.WRAPPER);
		}

		Wrapper(ChecksumComputer checksumComputer) {
			super(WrapperProperty.WRAPPER, checksumComputer);
		}

	}

}
