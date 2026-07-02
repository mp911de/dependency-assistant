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

import javax.swing.Icon;

import biz.paluch.dap.lookup.DependencySiteSearchHit;
import biz.paluch.dap.lookup.SiteRole;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jspecify.annotations.Nullable;

/**
 * Display representation of a single located {@link DependencySiteSearchHit}
 * for the Dependency Sites popup: the precomputed display strings and preview
 * text, so list rendering and preview updates touch no PSI.
 *
 * <p>{@link #from(DependencySiteSearchHit, Project)} performs all PSI access
 * and must be called inside a read action; the resulting value object is then
 * safe to hand to Swing rendering on the EDT. The located element travels as a
 * {@link SmartPsiElementPointer} because the popup outlives the read action
 * that produced it; navigation and Find-window handoff re-resolve the pointer
 * and skip entries whose element did not survive.
 *
 * @param role the site role the row is classified under.
 * @param element pointer to the located element, resolved again at navigation
 * time.
 * @param label the one-line display label, the version or property expression
 * trimmed to a snippet.
 * @param location the project-relative {@code path:line}, or empty when the
 * file has no backing path.
 * @param previewText the dedented declaration preview shown beside the list.
 * @param matchRange the located element's text range within
 * {@code previewText}, or {@literal null} when the element text does not occur
 * verbatim in the preview.
 * @param icon the file type icon shown in the list, may be {@literal null}.
 * @param fileType the file type used to syntax-highlight the preview.
 * @author Mark Paluch
 */
record DependencySitePresentation(SiteRole role, SmartPsiElementPointer<PsiElement> element, String label,
		String location, String previewText, @Nullable TextRange matchRange, @Nullable Icon icon, FileType fileType) {

	private static final int SNIPPET_LIMIT = 60;

	/**
	 * Assemble the presentation for the given hit, resolving its location and
	 * declaration preview from PSI.
	 *
	 * <p>Must be called inside a read action.
	 *
	 * @param hit the located site.
	 * @param project the project owning the hit's file; must not be
	 * {@literal null}.
	 * @return the assembled presentation.
	 */
	static DependencySitePresentation from(DependencySiteSearchHit hit, Project project) {

		PsiElement element = hit.element();
		PsiFile psiFile = element.getContainingFile();
		VirtualFile file = psiFile.getVirtualFile();
		Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);

		int line = document != null ? document.getLineNumber(element.getTextOffset()) : 0;
		String location = file != null ? relativeLocation(project, file, line) : "";
		String preview = document != null ? dedentedLines(element, document) : element.getText();
		FileType fileType = psiFile.getFileType();
		SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.getInstance(project)
				.createSmartPsiElementPointer(element);

		return new DependencySitePresentation(hit.role(), pointer, snippet(hit.label()), location, preview,
				matchRange(element.getText(), preview), fileType.getIcon(), fileType);
	}

	/**
	 * Recover the element's range within the preview textually, since dedenting
	 * shifts offsets; no verbatim occurrence yields no highlight.
	 */
	private static @Nullable TextRange matchRange(String elementText, String preview) {

		if (elementText.isBlank()) {
			return null;
		}

		int index = preview.indexOf(elementText);
		return index >= 0 ? TextRange.from(index, elementText.length()) : null;
	}

	/**
	 * Render the declaration around the element with its common leading indentation
	 * stripped, so a deeply nested declaration previews cleanly.
	 *
	 * <p>The preview starts on the element's own line and ends exactly at the end
	 * of the enclosing statement, so a single-line site (a catalog accessor, an
	 * inline coordinate) that carries a trailing configuration block previews the
	 * whole block, while trailing same-line punctuation that is not part of the
	 * statement (such as a JSON element-separator comma) is excluded.
	 */
	private static String dedentedLines(PsiElement element, Document document) {

		int startLine = document.getLineNumber(element.getTextRange().getStartOffset());
		PsiElement statement = enclosingStatement(element, document, startLine);

		int from = document.getLineStartOffset(startLine);
		int to = statement.getTextRange().getEndOffset();
		return dedent(document.getText(new TextRange(from, to)));
	}

	/**
	 * Return the widest ancestor that still begins on the element's start line.
	 * That ancestor is the declaration statement owning the element; an enclosing
	 * block (the surrounding {@code dependencies} or {@code buildscript} closure, a
	 * parent XML tag) begins on an earlier line and stops the ascent.
	 */
	private static PsiElement enclosingStatement(PsiElement element, Document document, int startLine) {

		PsiElement statement = element;
		for (PsiElement parent = element.getParent(); parent != null
				&& !(parent instanceof PsiFile); parent = parent.getParent()) {

			if (isPreviewBoundary(parent, statement, document, startLine)) {
				break;
			}
			statement = parent;
		}
		return statement;
	}

	private static boolean isPreviewBoundary(PsiElement parent, PsiElement child, Document document, int startLine) {

		if (parent instanceof PsiFile) {
			return true;
		}

		TextRange parentRange = parent.getTextRange();
		if (document.getLineNumber(parentRange.getStartOffset()) != startLine) {
			return true;
		}

		TextRange childRange = child.getTextRange();
		return parentRange.getStartOffset() == childRange.getStartOffset()
				&& parentRange.getEndOffset() > childRange.getEndOffset();
	}

	private static String dedent(String text) {

		String[] lines = text.split("\n", -1);
		int common = Integer.MAX_VALUE;
		for (String line : lines) {

			if (line.isBlank()) {
				continue;
			}

			int indent = 0;
			while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
				indent++;
			}
			common = Math.min(common, indent);
		}

		if (common <= 0 || common == Integer.MAX_VALUE) {
			return text;
		}

		StringBuilder result = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {

			String line = lines[i];
			result.append(line.length() >= common ? line.substring(common) : line);
			if (i < lines.length - 1) {
				result.append('\n');
			}
		}

		return result.toString();
	}

	/**
	 * Render the file path relative to the project root with a one-based line, so
	 * entries in different directories are unambiguous.
	 */
	private static String relativeLocation(Project project, VirtualFile file, int line) {

		String basePath = project.getBasePath();
		String path = file.getPath();
		if (basePath != null && path.startsWith(basePath)) {
			path = path.substring(basePath.length());
			if (path.startsWith("/")) {
				path = path.substring(1);
			}
		}

		return path + ":" + (line + 1);
	}

	private static String snippet(String text) {

		String firstLine = text.strip();
		int newline = firstLine.indexOf('\n');
		if (newline >= 0) {
			firstLine = firstLine.substring(0, newline).strip();
		}

		return firstLine.length() > SNIPPET_LIMIT ? firstLine.substring(0, SNIPPET_LIMIT - 3) + "..." : firstLine;
	}

}
