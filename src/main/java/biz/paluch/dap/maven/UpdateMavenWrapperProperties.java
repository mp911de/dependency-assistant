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

package biz.paluch.dap.maven;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyKeyValueFormat;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Applies dependency updates to {@code .mvn/wrapper/maven-wrapper.properties}.
 *
 * @author Mark Paluch
 */
class UpdateMavenWrapperProperties {

	/**
	 * Apply a single update at the given wrapper version literal.
	 * @param versionLiteral the {@link PropertyValueImpl} that owns the URL value;
	 * must not be {@literal null}.
	 * @param update the update to apply; must not be {@literal null}.
	 */
	public static void applyUpdate(PsiElement versionLiteral, DependencyUpdate update) {

		PropertyImpl property = MavenWrapperUtils.findProperty(versionLiteral);
		if (property == null) {
			return;
		}

		Set<String> toCommentOut = new HashSet<>();
		MavenWrapperParser.parse(property, it -> {
			applyUpdate(property, it, update, toCommentOut);
		});

		postProcess(property.getContainingFile(), toCommentOut);
	}

	/**
	 * Apply updates to the given wrapper PSI file.
	 * @param psiFile the wrapper PSI file; must not be {@literal null}.
	 * @param updates the updates to apply; must not be {@literal null}.
	 */
	public static void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {

		if (!(psiFile instanceof PropertiesFile properties)) {
			return;
		}

		Set<String> toCommentOut = new HashSet<>();
		Properties.from(properties).filterMap(MavenWrapperParser::parse).forEach(it -> {
			for (DependencyUpdate update : updates) {
				applyUpdate(it.propertyLiteral(), it, update, toCommentOut);
			}
		});

		postProcess(psiFile, new HashSet<>(toCommentOut));
	}

	private static void applyUpdate(PropertyImpl property, WrapperEntry it, DependencyUpdate update,
			Collection<String> toCommentOut) {

		if (it.hasArtifactId(update.coordinate())) {

			List<TextRange> ranges = MavenWrapperUtils.getVersionRanges(property);
			if (ranges.isEmpty()) {
				return;
			}

			ASTNode valueNode = property.getValueNode();
			if (valueNode == null) {
				return;
			}

			toCommentOut.add(it.property().shaKey());

			int propertyStart = property.getTextRange().getStartOffset();
			int valueStart = valueNode.getTextRange().getStartOffset() - propertyStart;
			String updatedText = property.getText();

			for (int i = ranges.size() - 1; i >= 0; i--) {
				TextRange rangeInProperty = ranges.get(i).shiftLeft(propertyStart);
				updatedText = rangeInProperty.replace(updatedText, update.version().toString());
			}

			property.setValue(updatedText.substring(valueStart), PropertyKeyValueFormat.FILE);
		}
	}

	private static void postProcess(PsiFile psiFile, Collection<String> toCommentOut) {

		if (toCommentOut.isEmpty()) {
			return;
		}

		for (PropertyImpl property : PsiTreeUtil.findChildrenOfType(psiFile, PropertyImpl.class)) {
			if (toCommentOut.contains(property.getUnescapedKey())) {
				commentOut(property);
			}
		}
	}

	private static void commentOut(PropertyImpl property) {

		String commentedText = commentEveryPhysicalLine(property.getText());
		PsiFile dummyFile = PsiFileFactory.getInstance(property.getProject()).createFileFromText(
				"dummy.properties", PropertiesFileType.INSTANCE, commentedText);

		List<PsiComment> list = SyntaxTraverser.psiTraverser(dummyFile)
				.filter(PsiComment.class)
				.toList();

		if (list.isEmpty()) {
			return;
		}

		PsiElement parent = property.getParent();
		parent.addRangeBefore(list.getFirst(), list.getLast(), property);
		property.delete();
	}

	private static String commentEveryPhysicalLine(String text) {
		String[] lines = text.split("\n", -1);
		StringBuilder result = new StringBuilder();

		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				result.append('\n');
			}
			result.append("# ").append(lines[i]);
		}

		return result.toString();
	}

	public static @Nullable String getRewrittenUrl(@Nullable String url, ArtifactVersion version) {

		if (StringUtils.isEmpty(url)) {
			return null;
		}

		Matcher matcher = MavenWrapperParser.MAVEN_ARTIFACT_PATTERN.matcher(url);
		if (!matcher.find()) {
			return null;
		}

		String start = url.substring(0, matcher.start("version1"));
		String middle = url.substring(matcher.end("version1"), matcher.start("version2"));
		String tail = url.substring(matcher.end("version2"));

		return start + version + middle + version + tail;
	}

}
