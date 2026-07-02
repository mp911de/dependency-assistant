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

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.util.PsiElements;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.LookupElementDocumentationTargetProvider;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jspecify.annotations.Nullable;

/**
 * Provides Quick Documentation for release items in the completion lookup,
 * showing release date, commit, relation to the current version, and security
 * advisories for the highlighted release.
 *
 * <p>{@link LookupElementDocumentationTargetProvider} is experimental API as of
 * platform 2025.3; revisit on platform upgrades.
 *
 * @author Mark Paluch
 * @see ReleaseCompletionProvider
 */
public class ReleaseLookupDocumentationProvider implements LookupElementDocumentationTargetProvider {

	@Override
	public @Nullable DocumentationTarget documentationTarget(PsiFile psiFile, LookupElement element, int offset) {

		if (!(element.getObject() instanceof ArtifactRelease release)) {
			return null;
		}

		return new ReleaseDocumentationTarget(psiFile, offset, release);
	}

	static class ReleaseDocumentationTarget implements DocumentationTarget {

		private final SmartPsiElementPointer<PsiFile> file;

		private final int offset;

		private final ArtifactRelease release;

		ReleaseDocumentationTarget(PsiFile file, int offset, ArtifactRelease release) {
			this.file = SmartPointerManager.createPointer(file);
			this.offset = offset;
			this.release = release;
		}

		@Override
		public TargetPresentation computePresentation() {
			return TargetPresentation.builder(release.artifactId() + " " + release.getVersion()).presentation();
		}

		@Override
		public Pointer<ReleaseDocumentationTarget> createPointer() {

			SmartPsiElementPointer<PsiFile> file = this.file;
			int offset = this.offset;
			ArtifactRelease release = this.release;
			return () -> {
				PsiFile psiFile = file.getElement();
				return psiFile != null ? new ReleaseDocumentationTarget(psiFile, offset, release) : null;
			};
		}

		@Override
		public @Nullable DocumentationResult computeDocumentation() {

			String html = buildHtmlBody();
			return html != null ? DocumentationResult.documentation(html) : null;
		}

		/**
		 * Build the release documentation body, rendering with the declaration resolved
		 * at the completion position; {@literal null} when the file is no longer live
		 * or the position no longer resolves to a dependency declaration.
		 */
		@Nullable
		String buildHtmlBody() {

			PsiFile psiFile = file.getElement();
			if (psiFile == null) {
				return null;
			}

			PsiElement position = psiFile.findElementAt(offset);
			ArtifactReferenceContext context = ArtifactReferenceContext
					.from(PsiElements.unleaf(position != null ? position : psiFile));
			if (context.isAbsent()) {
				return null;
			}

			return DependencyDocumentationRenderer.from(context, false).render(release);
		}

	}

}
