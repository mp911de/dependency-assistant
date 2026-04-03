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
package biz.paluch.dap.support;

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.Property;

import java.awt.Image;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;

/**
 * Support class for documentation providers that contributes hover and Quick Documentation ({@code Ctrl+Q}) content for
 * dependency elements whose name maps to a known dependency artifact in the {@link Cache}.
 */
public abstract class VersionDocumentationProviderSupport implements PsiDocumentationTargetProvider {

	static final int MAX_VERSIONS = 10;

	/**
	 * Builds the HTML body.
	 */
	protected static @Nullable String buildHtmlBody(Cache cache, Property property, String propertyName,
			String currentValue, @Nullable Map<String, Image> iconImages) {

		if (StringUtil.isEmpty(currentValue) || property.artifacts().isEmpty()) {
			return null;
		}

		ArtifactVersion currentVersion = tryParseVersion(currentValue);

		StringBuilder sb = new StringBuilder();

		sb.append(DocumentationMarkup.DEFINITION_START);
		sb.append(propertyName);
		sb.append(DocumentationMarkup.DEFINITION_END);

		sb.append(DocumentationMarkup.CONTENT_START);

		if (!currentValue.isEmpty()) {
			sb.append("<p>%s: <code>%s</code></p>".formatted(MessageBundle.message("documentation.current-value"),
					StringUtil.escapeXmlEntities(currentValue)));
		}

		for (CachedArtifact artifact : property.artifacts()) {

			ArtifactId artifactId = artifact.toArtifactId();
			sb.append("<p>%s: <code>%s</code></p>".formatted(MessageBundle.message("documentation.controls"), artifactId));

			List<Release> versions = cache.getReleases(artifactId, false);
			if (versions.isEmpty()) {
				continue;
			}

			sb.append("<table>");
			int count = 0;
			for (Release v : versions) {
				if (count++ >= MAX_VERSIONS) {
					break;
				}
				sb.append("<tr>");

				if (iconImages != null && currentVersion != null) {
					VersionAge age = VersionAge.fromVersions(currentVersion, v);
					sb.append("<td>" + HtmlChunk.icon(age.getIconName(), age.getIcon()) + "</td>");
				}

				sb.append("<td>");
				boolean preview = v.isPreview();
				boolean current = v.version().equals(currentVersion);
				if (preview) {
					sb.append("<i>");
				}
				if (current) {
					sb.append("<b>");
				}
				sb.append(v.version());
				if (current) {
					sb.append("</b>");
				}
				if (preview) {
					sb.append("</i>");
				}
				sb.append("</td><td>");
				if (v.releaseDate() != null) {
					sb.append(v.releaseDate().toLocalDate());
				}
				sb.append("</td></tr>");
			}
			sb.append("</table>");
		}

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

	private static @Nullable ArtifactVersion tryParseVersion(String versionString) {

		if (versionString.isEmpty()) {
			return null;
		}
		try {
			return ArtifactVersion.of(versionString);
		} catch (Exception e) {
			return null;
		}
	}

}
