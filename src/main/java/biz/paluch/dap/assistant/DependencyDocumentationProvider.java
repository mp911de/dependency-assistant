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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.ReleaseDateFormatter;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import biz.paluch.dap.util.PsiElements;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
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
 * @author Mark Paluch
 */
public class DependencyDocumentationProvider
		implements PsiDocumentationTargetProvider {

	static final int MAX_VERSIONS = 10;

	@Override
	public @Nullable DocumentationTarget documentationTarget(PsiElement element, @Nullable PsiElement originalElement) {

		PsiElement target = PsiElements.unleaf(originalElement != null ? originalElement : element);
		Project project = target.getProject();
		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(project,
				target.getContainingFile());
		if (context.isAbsent()) {
			return null;
		}

		VersionUpgradeLookupSupport lookup = context.getLookup(target, target.getContainingFile().getVirtualFile());
		ArtifactReference artifactReference = lookup.resolveArtifactReference(target);
		if (!artifactReference.isResolved()) {
			return null;
		}

		Cache cache = lookup.getCache();
		ArtifactDeclaration declaration = artifactReference.getDeclaration();
		ArtifactVersion currentVersion = declaration.isVersionDefined() ? declaration.getVersion() : null;
		if (lookup.hasCachedState()
				&& declaration
						.getVersionSource() instanceof VersionSource.VersionProperty propertySource) {

			ProjectState projectState = lookup.getProjectState();
			VersionProperty property = projectState.findProperty(propertySource.getProperty());
			if (property == null || property.artifacts().isEmpty()) {
				return null;
			}

			return new PropertyDocumentationTarget(target, cache, currentVersion,
					element.getText(), property, context.getInterfaceAssistant());
		}

		return new DependencyVersionTarget(target, cache, artifactReference.getArtifactId(),
				currentVersion, element.getText(), context.getInterfaceAssistant());
	}

	private abstract static class DocumentationTargetSupport implements DocumentationTarget {

		final PsiElement target;

		final Cache cache;

		final @Nullable ArtifactVersion currentVersion;

		final @Nullable String currentValue;

		public DocumentationTargetSupport(PsiElement target, Cache cache, @Nullable ArtifactVersion currentVersion,
				@Nullable String currentValue) {
			this.target = target;
			this.cache = cache;
			this.currentVersion = currentVersion;
			this.currentValue = currentValue;
		}

		@Override
		public TargetPresentation computePresentation() {
			return TargetPresentation.builder(target.toString()).presentation();
		}

		/**
		 * Full documentation shown in the Quick Documentation popup ({@code Ctrl+Q}).
		 * Version rows include {@link VersionAge} icons rendered relative to the tag's
		 * current value.
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
		 * Simplified content shown in the hover tooltip (no icons - plain HTML).
		 */
		@Override
		public @Nullable String computeDocumentationHint() {
			return buildHtmlBody(null);
		}

		/**
		 * Builds the HTML body.
		 */
		protected abstract @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages);

	}

	private static class PropertyDocumentationTarget extends DocumentationTargetSupport {

		private final VersionProperty property;

		private final InterfaceAssistant interfaceAssistant;

		private final SmartPsiElementPointer<PsiElement> pointer;

		public PropertyDocumentationTarget(PsiElement target, Cache cache, @Nullable ArtifactVersion currentVersion,
				@Nullable String currentValue, VersionProperty property, InterfaceAssistant interfaceAssistant) {
			super(target, cache, currentVersion, currentValue);
			this.property = property;
			this.interfaceAssistant = interfaceAssistant;
			this.pointer = SmartPointerManager.createPointer(target);
		}

		@Override
		public Pointer<? extends DocumentationTarget> createPointer() {
			return () -> {
				PsiElement element = pointer.getElement();
				return element != null
						? new PropertyDocumentationTarget(element, cache, currentVersion, currentValue, property,
								interfaceAssistant)
						: null;
			};
		}

		/**
		 * Builds the HTML body.
		 */
		protected @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages) {
			return DependencyDocumentationProvider.buildHtmlBody(interfaceAssistant, cache, property, currentVersion,
					iconImages);
		}

	}

	/**
	 * Documentation target for a concrete dependency version.
	 */
	protected static class DependencyVersionTarget extends DocumentationTargetSupport {

		private final ArtifactId artifactId;

		private final InterfaceAssistant interfaceAssistant;

		private final SmartPsiElementPointer<PsiElement> pointer;

		public DependencyVersionTarget(PsiElement target, Cache cache, ArtifactId artifactId,
				@Nullable ArtifactVersion currentVersion, @Nullable String currentValue,
				InterfaceAssistant interfaceAssistant) {
			super(target, cache, currentVersion, currentValue);
			this.artifactId = artifactId;
			this.interfaceAssistant = interfaceAssistant;
			this.pointer = SmartPointerManager.createPointer(target);
		}

		@Override
		public Pointer<? extends DocumentationTarget> createPointer() {
			return () -> {
				PsiElement element = pointer.getElement();
				return element != null
						? new DependencyVersionTarget(element, cache, artifactId, currentVersion, currentValue,
								interfaceAssistant)
						: null;
			};
		}

		/**
		 * Builds the HTML body.
		 */
		protected @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages) {
			return DependencyDocumentationProvider.buildHtmlBody(interfaceAssistant, cache, artifactId, currentVersion,
					iconImages);
		}

	}

	/**
	 * Builds the HTML body.
	 */
	private static @Nullable String buildHtmlBody(InterfaceAssistant interfaceAssistant, Cache cache,
			VersionProperty property,
			@Nullable ArtifactVersion artifactVersion, @Nullable Map<String, Image> iconImages) {

		if (property.artifacts().isEmpty()) {
			return null;
		}

		ReleaseDateFormatter formatter = ReleaseDateFormatter.create();
		StringBuilder sb = new StringBuilder();

		sb.append(DocumentationMarkup.DEFINITION_START);
		sb.append(property.name());
		sb.append(DocumentationMarkup.DEFINITION_END);

		sb.append(DocumentationMarkup.CONTENT_START);

		if (artifactVersion != null) {
			sb.append("<p>%s: <code>%s</code></p>".formatted(MessageBundle.message("documentation.current-value"),
					StringUtil.escapeXmlEntities(interfaceAssistant.getDocumentationText(artifactVersion))));
		}

		for (CachedArtifact artifact : property.artifacts()) {

			ArtifactId artifactId = artifact.toArtifactId();
			sb.append("<p>%s <code>%s</code></p>".formatted(MessageBundle.message("documentation.property-for"),
					artifactId));

			List<Release> versions = cache.getReleases(artifactId);
			if (versions.isEmpty()) {
				continue;
			}
			Set<String> seen = new HashSet<>();

			sb.append("<table>");
			int count = 0;
			for (Release v : versions) {

				if (!seen.add(v.version().getVersion().getVersion().toString())) {
					continue;
				}

				if (count++ >= MAX_VERSIONS) {
					break;
				}
				sb.append("<tr>");

				if (iconImages != null && artifactVersion != null) {
					VersionAge age = VersionAge.between(artifactVersion, v.getVersion());
					sb.append("<td>" + HtmlChunk.icon(age.getIconName(), age.getIcon()) + "</td>");
				}

				sb.append("<td>");
				boolean preview = v.isPreview();
				boolean current = v.version().equals(artifactVersion);
				if (preview) {
					sb.append("<i>");
				}
				if (current) {
					sb.append("<b>");
				}
				sb.append(v.version().getVersion().getVersion());
				if (current) {
					sb.append("</b>");
				}
				if (preview) {
					sb.append("</i>");
				}
				sb.append("</td><td>");
				if (v.releaseDate() != null) {
					sb.append(formatter.formatLong(v.releaseDate()));
				}
				sb.append("</td></tr>");
			}
			sb.append("</table>");
		}

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

	/**
	 * Builds the HTML body.
	 */
	protected static String buildHtmlBody(InterfaceAssistant interfaceAssistant, Cache cache, ArtifactId artifactId,
			@Nullable ArtifactVersion artifactVersion,
			@Nullable Map<String, Image> iconImages) {

		StringBuilder sb = new StringBuilder();
		ReleaseDateFormatter formatter = ReleaseDateFormatter.create();

		sb.append(DocumentationMarkup.DEFINITION_START);
		sb.append(artifactId);
		sb.append(DocumentationMarkup.DEFINITION_END);

		sb.append(DocumentationMarkup.CONTENT_START);

		if (artifactVersion != null) {
			sb.append("<p>%s: <code>%s</code></p>".formatted(MessageBundle.message("documentation.current-value"),
					StringUtil.escapeXmlEntities(interfaceAssistant.getDocumentationText(artifactVersion))));
		}

		List<Release> versions = cache.getReleases(artifactId);
		Set<String> seen = new HashSet<>();
		if (!versions.isEmpty()) {
			sb.append("<table>");
			int count = 0;
			for (Release v : versions) {

				if (!seen.add(v.version().getVersion().getVersion().toString())) {
					continue;
				}
				if (count++ >= MAX_VERSIONS) {
					break;
				}
				sb.append("<tr>");

				if (iconImages != null && artifactVersion != null) {
					VersionAge age = VersionAge.between(artifactVersion, v.getVersion());
					sb.append("<td>" + HtmlChunk.icon(age.getIconName(), age.getIcon()) + "</td>");
				}

				sb.append("<td>");
				boolean preview = v.isPreview();
				boolean current = v.version().equals(artifactVersion);
				if (preview) {
					sb.append("<i>");
				}
				if (current) {
					sb.append("<b>");
				}
				sb.append(v.version().getVersion().getVersion());
				if (current) {
					sb.append("</b>");
				}
				if (preview) {
					sb.append("</i>");
				}
				sb.append("</td><td>");
				if (v.releaseDate() != null) {
					sb.append(formatter.formatLong(v.releaseDate()));
				}
				sb.append("</td></tr>");
			}
			sb.append("</table>");
		}

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

}
