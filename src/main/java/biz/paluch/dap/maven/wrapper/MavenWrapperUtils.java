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

package biz.paluch.dap.maven.wrapper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.util.MatchFunction;
import biz.paluch.dap.util.PropertyUtils;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * Package-local utilities for Maven Wrapper property files.
 *
 * <p>This class keeps Maven wrapper PSI concerns in one place, in particular
 * decoded property-value matching, range mapping, and wrapper-file checks.
 *
 * @author Mark Paluch
 */
class MavenWrapperUtils {

	/**
	 * Completion marker inserted by IntelliJ while calculating property-value
	 * completions.
	 */
	public static final String COMPLETION_PLACEHOLDER = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED;

	/*
	 * version1 is greedy because a literal "/" terminates it; version2 is reluctant
	 * so the trailing tail (e.g. "-bin.zip") can absorb the classifier instead of
	 * being eaten by the version. Possessive quantifiers on the optional version
	 * fragments prevent super-linear backtracking on hostile input that mixes many
	 * "." and "-" characters between slashes.
	 */
	public static final Pattern MAVEN_ARTIFACT_PATTERN = Pattern.compile(
			"(?<groupId>[\\w/]+)/(?<artifactId1>[\\w.-]+)/(?<version1>(\\d[\\w.-]*+)?("
					+ Pattern.quote(COMPLETION_PLACEHOLDER) + ")?(\\d[\\w.-]*+)?)/"
					+ "(?<artifactId2>[\\w.-]+?)-"
					+ "(?<version2>(?:\\d[\\w.-]*?)?(" + Pattern.quote(COMPLETION_PLACEHOLDER) + ")?(?:\\d[\\w.-]*?)?)"
					+ "(?<tail>-(?!(?:SNAPSHOT|rc-\\d)[-.])[A-Za-z][\\w-]*(?:\\.[^/]*)?|\\.[A-Za-z][^/]*|(?=$))");

	/**
	 * Maximum decoded value length accepted by {@link #MAVEN_ARTIFACT_PATTERN}.
	 * Longer values are treated as no match to bound regex evaluation cost.
	 */
	private static final int MAX_MATCH_LENGTH = 2048;

	public static final String REPOSITORY_ID = "maven-wrapper";

	public static final String WRAPPER_FILENAME = "maven-wrapper.properties";

	/**
	 * Return file-absolute ranges for the two version segments in a Maven
	 * coordinate-shaped URL value.
	 *
	 * <p>This method inspects only the property value. It does not require a
	 * supported wrapper property key or a Maven Wrapper file.
	 *
	 * @param property the wrapper property to inspect.
	 * @return the version ranges, or an empty list if the property value does not
	 * match the bounded Maven artifact pattern.
	 */
	public static List<TextRange> getVersionRanges(Property property) {

		String value = property.getUnescapedValue();
		if (value == null || value.length() > MAX_MATCH_LENGTH) {
			return List.of();
		}

		String stripped = value.replace(COMPLETION_PLACEHOLDER, "");
		Matcher matcher = MAVEN_ARTIFACT_PATTERN.matcher(stripped);
		if (!matcher.find()) {
			return List.of();
		}

		int v1Start = expandStrippedPosition(matcher.start("version1"), value);
		int v1End = expandStrippedPosition(matcher.end("version1"), value);
		int v2Start = expandStrippedPosition(matcher.start("version2"), value);
		int v2End = expandStrippedPosition(matcher.end("version2"), value);

		return PropertyUtils.findTextRanges(property, (str, index) -> {
			if (index < v1Start) {

				return MatchFunction.match(value.substring(v1Start, v1End), v1Start, v1End);
			}
			if (index < v2Start) {
				return MatchFunction.match(value.substring(v2Start, v2End), v2Start, v2End);
			}
			return MatchFunction.noMatch();
		});
	}

	/**
	 * Map a position from {@link #COMPLETION_PLACEHOLDER}-stripped text back to the
	 * corresponding position in the original (placeholder-bearing) text.
	 *
	 * <p>A position that coincides with a stripped occurrence is treated as being
	 * before that occurrence in the original text.
	 */
	private static int expandStrippedPosition(int strippedPos, String original) {

		int placeholderLength = COMPLETION_PLACEHOLDER.length();
		int pos = strippedPos;
		int from = 0;
		while (from < pos) {
			int hit = original.indexOf(COMPLETION_PLACEHOLDER, from);
			if (hit < 0 || hit >= pos) {
				break;
			}
			pos += placeholderLength;
			from = hit + placeholderLength;
		}
		return pos;
	}

	private MavenWrapperUtils() {
	}

	static boolean isWrapperFile(PropertiesFile file) {
		return WRAPPER_FILENAME.equals(file.getName());
	}

	static boolean isWrapperFile(@Nullable VirtualFile file) {
		return file != null && WRAPPER_FILENAME.equals(file.getName());
	}

	static boolean isWrapperFile(@Nullable PsiFile file) {
		return file instanceof PropertiesFile && WRAPPER_FILENAME.equals(file.getName());
	}

	static boolean isVersionElement(PsiElement element) {

		Property property = PropertyUtils.findProperty(element);
		PsiElement value = property != null ? PropertyUtils.findPropertyValue(property) : null;
		return value == element && isWrapperFile(element.getContainingFile())
				&& WrapperProperty.isWrapperProperty(property);
	}

	public static ProjectId createProjectId(VirtualFile virtualFile) {
		return new ProjectId("org.apache.maven", "apache-maven", virtualFile.getPath());
	}

}
