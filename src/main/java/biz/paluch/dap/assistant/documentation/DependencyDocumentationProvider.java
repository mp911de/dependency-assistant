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

package biz.paluch.dap.assistant.documentation;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.ArtifactReferenceContext;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.util.PsiElements;
import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jspecify.annotations.Nullable;

/**
 * Quick Documentation targets for dependency declarations and version
 * properties.
 * <p>Targets can be resolved again after a version update.
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
	 * Resolve a documentation target from the live declaration.
	 * @return {@literal null} if no declaration or indexed property artifacts are
	 * available.
	 */
	static @Nullable DocumentationTarget createTarget(PsiElement target) {

		ArtifactReferenceContext context = ArtifactReferenceContext.from(target);
		if (context.isAbsent()) {
			return null;
		}

		ArtifactDeclaration declaration = context.getDeclaration();
		boolean linkable = declaration.getVersionLiteral() != null;
		DependencyDocumentationRenderer documentation = DependencyDocumentationRenderer.from(context, linkable);

		if (declaration.getVersionSource() instanceof VersionSource.VersionProperty propertySource) {

			VersionUpgradeLookup lookup = context.getDependencyContext().getLookup(target,
					target.getContainingFile().getVirtualFile());
			VersionProperty property = lookup.findProperty(propertySource.getProperty());
			if (property == null || property.artifacts().isEmpty()) {
				return null;
			}

			return new PropertyDocumentationTarget(target, documentation, property);
		}

		return new DependencyVersionTarget(target, documentation, context.getPackageIdentity());
	}

	private abstract static class DocumentationTargetSupport implements DocumentationTarget, DependencyUpgradeTarget {

		final PsiElement target;

		final SmartPsiElementPointer<PsiElement> pointer;

		final DependencyDocumentationRenderer documentation;

		DocumentationTargetSupport(PsiElement target, DependencyDocumentationRenderer documentation) {
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
		public @Nullable PsiFile getDeclarationFile() {

			PsiElement element = pointer.getElement();
			return element != null ? element.getContainingFile() : null;
		}

		@Override
		public final Pointer<? extends DocumentationTarget> createPointer() {
			SmartPsiElementPointer<PsiElement> pointer = this.pointer;
			return () -> {
				PsiElement element = pointer.getElement();
				return element != null ? createTarget(element) : null;
			};
		}

		@Override
		public void applyVersion(String version) {

			PsiElement element = pointer.getElement();
			if (element == null) {
				return;
			}

			ArtifactReferenceContext context = ArtifactReferenceContext.from(element);
			if (context.isAbsent()) {
				return;
			}

			ArtifactDeclaration declaration = context.getDeclaration();
			PsiElement versionLiteral = declaration.getVersionLiteral();
			if (versionLiteral == null) {
				return;
			}

			Release release = context.getReleases().stream()
					.filter(it -> it.getVersion().toString().equals(version))
					.findFirst().orElse(null);
			if (release == null) {
				return;
			}
			DependencyUpdate update = DependencyUpdate.from(declaration, release);
			context.getDependencyContext().applyUpdate(versionLiteral, update);
		}

		@Override
		public @Nullable DocumentationResult computeDocumentation() {

			String html = buildHtmlBody(true);
			if (html == null) {
				return null;
			}
			return DocumentationResult.documentation(html);
		}

		@Override
		public @Nullable String computeDocumentationHint() {
			return buildHtmlBody(false);
		}

		/**
		 * Build the HTML body, or return {@literal null} if unavailable.
		 */
		protected abstract @Nullable String buildHtmlBody(boolean withIcons);

	}

	private static class PropertyDocumentationTarget extends DocumentationTargetSupport {

		private final VersionProperty property;

		PropertyDocumentationTarget(PsiElement target, DependencyDocumentationRenderer documentation,
				VersionProperty property) {
			super(target, documentation);
			this.property = property;
		}

		@Override
		public ArtifactId getArtifactId() {
			return property.artifacts().getFirst().toArtifactId();
		}

		@Override
		public PackageIdentity getPackageIdentity() {
			return property.artifacts().getFirst().toPackageIdentity();
		}

		@Override
		protected @Nullable String buildHtmlBody(boolean withIcons) {
			return documentation.render(property, withIcons);
		}

	}

	protected static class DependencyVersionTarget extends DocumentationTargetSupport {

		private final PackageIdentity pkg;

		DependencyVersionTarget(PsiElement target, DependencyDocumentationRenderer documentation,
				PackageIdentity pkg) {
			super(target, documentation);
			this.pkg = pkg;
		}

		@Override
		public ArtifactId getArtifactId() {
			return pkg.getArtifactId();
		}

		@Override
		public PackageIdentity getPackageIdentity() {
			return pkg;
		}

		@Override
		protected @Nullable String buildHtmlBody(boolean withIcons) {
			return documentation.render(pkg, withIcons);
		}

	}

}
