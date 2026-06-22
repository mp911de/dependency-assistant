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

import java.util.List;

import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionCaretRemap;
import biz.paluch.dap.state.CachedRelease;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.Properties;
import biz.paluch.dap.util.PropertyUtils;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyKeyValueFormat;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Applies dependency updates to
 * {@code gradle/wrapper/gradle-wrapper.properties}.
 *
 * @author Mark Paluch
 */
class UpdateGradleWrapperProperties {

	/**
	 * Apply a single update at the given wrapper version literal.
	 * @param versionLiteral the property value that owns the distribution URL; must
	 * not be {@literal null}.
	 * @param update the update to apply; must not be {@literal null}.
	 * @return the caret remap with one range per rewritten version occurrence in
	 * document order, computed after the SHA post-process, or
	 * {@link VersionCaretRemap#none()} when no occurrence is rewritten.
	 */
	public static VersionCaretRemap applyUpdate(PsiElement versionLiteral, DependencyUpdate update) {

		PropertyImpl property = PropertyUtils.findProperty(versionLiteral);
		if (property == null) {
			return VersionCaretRemap.none();
		}

		GradleWrapperEntry entry = GradleWrapperParser.parse(property);
		List<TextRange> oldRanges = List.of();
		if (entry != null && entry.hasArtifactId(update.coordinate())) {
			oldRanges = GradleWrapperUtils.getVersionRanges(property);
			applyUpdate(property, entry, update);
		}

		List<TextRange> newRanges = GradleWrapperUtils.getVersionRanges(property);
		if (oldRanges.isEmpty() || oldRanges.size() != newRanges.size()) {
			return VersionCaretRemap.none();
		}

		return VersionCaretRemap.of(oldRanges, newRanges);
	}

	public static void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {

		if (!(psiFile instanceof PropertiesFile properties)) {
			return;
		}

		List<GradleWrapperEntry> entries = Properties.from(properties).filterMap(GradleWrapperParser::parse)
				.toList();
		for (GradleWrapperEntry entry : entries) {
			for (DependencyUpdate update : updates) {
				applyUpdate(entry.propertyLiteral(), entry, update);
			}
		}
	}

	private static void applyUpdate(PropertyImpl property, GradleWrapperEntry entry, DependencyUpdate update) {

		if (!entry.hasArtifactId(update.coordinate())) {
			return;
		}

		String currentValue = property.getUnescapedValue();
		if (!StringUtils.hasText(currentValue)) {
			return;
		}

		List<TextRange> ranges = GradleWrapperUtils.getVersionRanges(property);
		if (ranges.isEmpty()) {
			return;
		}

		ASTNode valueNode = property.getValueNode();
		if (valueNode == null) {
			return;
		}

		int propertyStart = property.getTextRange().getStartOffset();
		int valueStart = valueNode.getTextRange().getStartOffset() - propertyStart;
		String updatedText = property.getText();

		for (int i = ranges.size() - 1; i >= 0; i--) {
			TextRange rangeInProperty = ranges.get(i).shiftLeft(propertyStart);
			updatedText = rangeInProperty.replace(updatedText, update.version().toString());
		}

		property.setValue(updatedText.substring(valueStart), PropertyKeyValueFormat.FILE);

		String sha = resolveSha(property, update);
		postProcessSha(property.getContainingFile(), entry, sha);
	}

	private static void postProcessSha(PsiFile file, GradleWrapperEntry entry, @Nullable String sha) {

		for (PropertyImpl property : PsiTreeUtil.findChildrenOfType(file, PropertyImpl.class)) {
			if (entry.property().shaKey().equals(property.getUnescapedKey())) {
				if (StringUtils.hasText(sha)) {
					property.setValue(sha, PropertyKeyValueFormat.FILE);
				} else {
					PropertyUtils.commentOut(property);
				}
			}
		}
	}

	private static @Nullable String resolveSha(PropertyImpl property, DependencyUpdate update) {

		if (update.version() instanceof GitVersion gitVersion && StringUtils.hasText(gitVersion.getSha())) {
			return gitVersion.getSha();
		}

		for (CachedRelease release : StateService.getInstance(property.getProject()).getCache()
				.getCachedReleases(update.coordinate())) {
			if (update.versionAsString().equals(release.version())) {
				return release.sha();
			}
		}
		return null;
	}

}
