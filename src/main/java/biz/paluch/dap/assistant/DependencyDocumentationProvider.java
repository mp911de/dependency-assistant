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

import java.awt.Image;
import java.util.LinkedHashMap;
import java.util.Map;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.util.PsiElements;
import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jspecify.annotations.Nullable;

/**
 * Provides Quick Documentation for supported dependency build files.
 *
 * <p>Resolution and target lifecycle live here; HTML rendering is owned by
 * {@link DocumentationContext}.
 *
 * @author Mark Paluch
 */
public class DependencyDocumentationProvider
		implements PsiDocumentationTargetProvider {

	@Override
	public @Nullable DocumentationTarget documentationTarget(PsiElement element, @Nullable PsiElement originalElement) {
		return createTarget(PsiElements.unleaf(originalElement != null ? originalElement : element));
	}

	/**
	 * Resolve the given element into a documentation target, or {@literal null}
	 * when the element does not resolve to a dependency declaration.
	 * <p>Used both for the initial documentation request and for re-resolving the
	 * target after an upgrade has rewritten the version literal, so the re-rendered
	 * popup reflects the live declaration state.
	 */
	static @Nullable DocumentationTarget createTarget(PsiElement target) {

		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(target.getProject(),
				target.getContainingFile());
		if (context.isAbsent()) {
			return null;
		}

		VersionUpgradeLookup lookup = context.getLookup(target, target.getContainingFile().getVirtualFile());
		ArtifactReference artifactReference = lookup.resolveArtifactReference(target);
		if (!artifactReference.isResolved()) {
			return null;
		}

		ArtifactDeclaration declaration = artifactReference.getDeclaration();
		ArtifactVersion currentVersion = declaration.isVersionDefined() ? declaration.getVersion() : null;
		boolean linkable = declaration.getVersionLiteral() != null;
		DocumentationContext documentation = new DocumentationContext(context.getInterfaceAssistant(),
				lookup.getCache(),
				currentVersion, linkable);

		if (declaration.getVersionSource() instanceof VersionSource.VersionProperty propertySource) {

			VersionProperty property = lookup.findProperty(propertySource.getProperty());
			if (property == null || property.artifacts().isEmpty()) {
				return null;
			}

			return new PropertyDocumentationTarget(target, documentation, property);
		}

		return new DependencyVersionTarget(target, documentation, artifactReference.getArtifactId());
	}

	private abstract static class DocumentationTargetSupport implements DocumentationTarget, DependencyUpgradeTarget {

		final PsiElement target;

		final SmartPsiElementPointer<PsiElement> pointer;

		final DocumentationContext documentation;

		DocumentationTargetSupport(PsiElement target, DocumentationContext documentation) {
			this.target = target;
			this.pointer = SmartPointerManager.createPointer(target);
			this.documentation = documentation;
		}

		@Override
		public TargetPresentation computePresentation() {
			return TargetPresentation.builder(target.toString()).presentation();
		}

		@Override
		public Project getProject() {
			return target.getProject();
		}

		@Override
		public final Pointer<? extends DocumentationTarget> createPointer() {
			SmartPsiElementPointer<PsiElement> pointer = this.pointer;
			return () -> {
				PsiElement element = pointer.getElement();
				return element != null ? createTarget(element) : null;
			};
		}

		/**
		 * Re-resolve the live declaration and rewrite its version literal through the
		 * shared update path. Runs inside the write action opened by the link handler.
		 */
		@Override
		public void applyVersion(String version) {

			PsiElement element = pointer.getElement();
			if (element == null) {
				return;
			}

			ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(element);
			if (context.isAbsent()) {
				return;
			}

			VersionUpgradeLookup lookup = context.getLookup(element, element.getContainingFile().getVirtualFile());
			ArtifactReference reference = lookup.resolveArtifactReference(element);
			if (!reference.isResolved()) {
				return;
			}

			PsiElement versionLiteral = reference.getDeclaration().getVersionLiteral();
			if (versionLiteral == null) {
				return;
			}

			Release release = DocumentationContext.findRelease(lookup.getCache(), reference.getArtifactId(), version);
			context.applyUpdate(versionLiteral, DependencyUpdate.from(reference.toDependency(), release));
		}

		/**
		 * Full documentation shown in the Quick Documentation popup ({@code Ctrl+Q}).
		 * Version rows include {@link VersionAge} icons rendered relative to the tag's
		 * current value; upgradeable rows wrap the icon in an upgrade link.
		 */
		@Override
		public @Nullable DocumentationResult computeDocumentation() {

			Map<String, Image> iconImages = new LinkedHashMap<>();
			String html = buildHtmlBody(iconImages);
			if (html == null) {
				return null;
			}
			return DocumentationResult.documentation(html).images(iconImages);
		}

		/**
		 * Simplified content shown in the hover tooltip (no icons and no links - plain
		 * HTML).
		 */
		@Override
		public @Nullable String computeDocumentationHint() {
			return buildHtmlBody(null);
		}

		/**
		 * Builds the documentation HTML body, or {@literal null} when nothing can be
		 * rendered.
		 *
		 * @param iconImages sink for the {@link VersionAge} icons referenced by the
		 * HTML, or {@literal null} to render plain HTML without icons or links (hover
		 * hint).
		 * @return the HTML body, or {@literal null} if no documentation is available.
		 */
		protected abstract @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages);

	}

	private static class PropertyDocumentationTarget extends DocumentationTargetSupport {

		private final VersionProperty property;

		PropertyDocumentationTarget(PsiElement target, DocumentationContext documentation, VersionProperty property) {
			super(target, documentation);
			this.property = property;
		}

		@Override
		public ArtifactId getArtifactId() {
			return property.artifacts().getFirst().toArtifactId();
		}

		@Override
		protected @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages) {
			return documentation.render(property, iconImages);
		}

	}

	/**
	 * Documentation target for a concrete dependency version.
	 */
	protected static class DependencyVersionTarget extends DocumentationTargetSupport {

		private final ArtifactId artifactId;

		DependencyVersionTarget(PsiElement target, DocumentationContext documentation, ArtifactId artifactId) {
			super(target, documentation);
			this.artifactId = artifactId;
		}

		@Override
		public ArtifactId getArtifactId() {
			return artifactId;
		}

		@Override
		protected @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages) {
			return documentation.render(artifactId, iconImages);
		}

	}

}
