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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.IncorrectOperationException;
import org.jspecify.annotations.Nullable;

/**
 * Intention that computes and inserts a missing Maven wrapper checksum.
 *
 * @author Mark Paluch
 */
public class MavenWrapperChecksumIntention implements IntentionAction, DumbAware {

	private static final String PREVIEW_VALUE = "<computing...>";

	private final WrapperProperty property;

	private final @Nullable ChecksumComputer checksumComputer;

	MavenWrapperChecksumIntention(WrapperProperty property) {
		this(property, null);
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

		Property urlProperty = findUrlProperty(project, file, property);
		if (urlProperty == null) {
			return;
		}

		String url = urlProperty.getUnescapedValue();
		if (url == null) {
			return;
		}

		RangeMarker marker = editor.getDocument().createRangeMarker(editor.getCaretModel().getOffset(),
				editor.getCaretModel().getOffset());
		SmartPsiElementPointer<Property> pointer = SmartPointerManager.createPointer(urlProperty);
		if (checksumComputer == null) {
			WrapperChecksumDownloader.downloadAndComputeSha(project, url,
					sha -> applyChecksum(project, editor.getDocument(), marker, pointer, url, sha),
					ex -> {
						marker.dispose();
						if (!project.isDisposed()) {
							notifyError(project, url, ex);
						}
					}, marker::dispose);
			return;
		}

		try {
			applyChecksum(project, editor.getDocument(), marker, pointer, url,
					checksumComputer.compute(project, url));
		} catch (IOException ex) {
			marker.dispose();
			notifyError(project, url, ex);
		}
	}

	@Override
	public boolean startInWriteAction() {
		return false;
	}

	private void applyChecksum(Project project, Document document, RangeMarker marker,
			SmartPsiElementPointer<Property> pointer, String expectedUrl, String sha) {

		if (project.isDisposed() || !StringUtils.hasText(sha) || !marker.isValid()) {
			marker.dispose();
			return;
		}

		int offset = marker.getStartOffset();
		WriteCommandAction.runWriteCommandAction(project, MessageBundle.message("wrapper.checksum.command"), null,
				() -> {
					Property current = pointer.getElement();
					if (current == null || !expectedUrl.equals(current.getUnescapedValue())
							|| !(current.getContainingFile() instanceof PropertiesFile properties)
							|| properties.findPropertyByKey(property.shaKey()) != null) {
						return;
					}
					insert(document, offset, property.shaKey() + "=" + sha);
					PsiDocumentManager.getInstance(project).commitDocument(document);
				});
		marker.dispose();
	}

	private static void notifyError(Project project, String url, IOException ex) {
		Notifications.error(project, MessageBundle.message("wrapper.checksum.error.title"),
				MessageBundle.message("wrapper.checksum.error", url, Notifications.errorMessage(ex)));
	}

	private static @Nullable Property findUrlProperty(Project project, PsiFile file, WrapperProperty property) {

		if (!TrustedProjects.isProjectTrusted(project) || !MavenWrapperUtils.isWrapperFile(file)
				|| !(file instanceof PropertiesFile properties)
				|| properties.findPropertyByKey(property.shaKey()) != null) {
			return null;
		}

		IProperty candidate = properties.findPropertyByKey(property.key());
		if (!(candidate instanceof Property urlProperty)) {
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
