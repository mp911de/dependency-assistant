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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.util.MatchFunction;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Package-local utilities for Maven wrapper property files.
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
	 * being eaten by the version.
	 */
	public static final Pattern MAVEN_ARTIFACT_PATTERN = Pattern.compile(
			"(?<groupId>[\\w/]+)/(?<artifactId1>[\\w.-]+)/(?<version1>(\\d[\\w.-]*)?(IntellijIdeaRulezzz)?(\\d[\\w.-]*)?)/"
					+ "(?<artifactId2>[\\w.-]+?)-"
					+ "(?<version2>(?:\\d[\\w.-]*?)?(IntellijIdeaRulezzz)?(?:\\d[\\w.-]*?)?)"
					+ "(?<tail>-(?!(?:SNAPSHOT|rc-\\d)[-.])[A-Za-z][\\w-]*(?:\\.[^/]*)?|\\.[A-Za-z][^/]*|(?=$))");

	public static final String REPOSITORY_ID = "maven-wrapper";

	public static final String WRAPPER_FILENAME = "maven-wrapper.properties";

	private static final String WRAPPER_DIR = "wrapper";

	private static final String MVN_DIR = ".mvn";

	/**
	 * Return file-absolute ranges for the version segments in a wrapper URL.
	 * @param property the wrapper property to inspect.
	 * @return the version ranges, or an empty list if the property value cannot be
	 * parsed as a supported wrapper URL.
	 */
	public static List<TextRange> getVersionRanges(PropertyImpl property) {

		String value = property.getUnescapedValue();
		if (value == null) {
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

		return findTextRanges(property, (str, index) -> {
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

	/**
	 * Return the property value element for the given property or value element.
	 * @param element the property or value element to inspect.
	 * @return the property value element, or {@literal null} if none can be found.
	 */
	public static @Nullable PropertyValueImpl findPropertyValue(PsiElement element) {

		if (element instanceof PropertyValueImpl pv) {
			return pv;
		}

		if (element instanceof PropertyImpl property) {
			return PsiTreeUtil.findChildOfType(property, PropertyValueImpl.class);
		}

		return null;
	}

	/**
	 * Return the property represented by the given element.
	 * @param element the property element or one of its direct children.
	 * @return the enclosing property, or {@literal null} if the element is not part
	 * of a property.
	 */
	public static @Nullable PropertyImpl findProperty(@Nullable PsiElement element) {

		if (element instanceof PropertyImpl property) {
			return property;
		}
		if (element != null) {
			return PsiTreeUtil.getParentOfType(element, PropertyImpl.class, false);
		}
		return null;
	}

	/**
	 * Apply the mapper when the given element belongs to a property.
	 * @param element the element used to locate the property.
	 * @param mapper the function to invoke with the property.
	 * @param defaultValue the supplier used when no property is available.
	 * @return the mapper result, or the supplied default value.
	 */
	public static <T> T doWithProperty(@Nullable PsiElement element,
			Function<PropertyImpl, ? extends T> mapper, Supplier<? extends T> defaultValue) {
		PropertyImpl property = findProperty(element);
		if (property != null) {
			return mapper.apply(property);
		}
		return defaultValue.get();
	}

	/**
	 * Locate a range in {@code property}'s value, falling back to the given element
	 * when no decoded match maps cleanly.
	 * @param property the wrapper property to inspect.
	 * @param fallbackElement the element whose range is used as fallback.
	 * @param matchFunction the match function evaluated against the decoded value.
	 * @return the first matching range, or the fallback element range.
	 */
	public static TextRange findTextRange(PropertyImpl property, PsiElement fallbackElement,
			MatchFunction matchFunction) {
		return findTextRanges(property, fallbackElement, matchFunction).getFirst();
	}

	/**
	 * Locate ranges in {@code property}'s value that correspond to matches produced
	 * by {@code matchFunction} against the decoded value text.
	 *
	 * <p>Decoding is restricted to the {@link PropertyImpl#getValueNode() value
	 * node}: the default {@link LiteralTextEscaper#getRelevantTextRange()} would
	 * cover the entire property (key, separator, and value) and would shift every
	 * decoded position by the key length.
	 * @param property the wrapper property to inspect.
	 * @param fallbackElement the element whose range is returned when decoding or
	 * mapping fails.
	 * @param matchFunction the match function evaluated against the decoded value.
	 * @return file-absolute ranges of each match, never empty, falling back to
	 * {@code fallbackElement}'s range when no match maps cleanly.
	 */
	public static List<TextRange> findTextRanges(
			PropertyImpl property, PsiElement fallbackElement,
			MatchFunction matchFunction) {

		List<TextRange> ranges = findTextRanges(property, matchFunction);

		return ranges.isEmpty() ? List.of(fallbackElement.getTextRange()) : ranges;
	}

	/**
	 * Locate ranges in {@code property}'s value that correspond to matches produced
	 * by {@code matchFunction} against the decoded value text.
	 *
	 * @param property the wrapper property to inspect.
	 * @param matchFunction the match function evaluated against the decoded value.
	 * @return file-absolute ranges of each match.
	 */
	public static List<TextRange> findTextRanges(PropertyImpl property,
			MatchFunction matchFunction) {

		LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = property.createLiteralTextEscaper();
		PropertyValueImpl first = SyntaxTraverser.psiTraverser(property)
				.filter(PropertyValueImpl.class).first();

		if (first == null) {
			return List.of();
		}

		int startOffset = property.getTextRange().getStartOffset();
		TextRange valueRangeInHost = first.getTextRange().shiftLeft(startOffset);
		StringBuilder decoded = new StringBuilder();
		if (!escaper.decode(valueRangeInHost, decoded)) {
			return List.of();
		}

		int index = 0;
		MatchResult matchResult = matchFunction.find(decoded.toString(), index);
		List<TextRange> ranges = new ArrayList<>();

		while (matchResult.hasMatch()) {
			int decodedStart = matchResult.start();
			int decodedEnd = matchResult.end();
			index = matchResult.end();

			TextRange rangeInHost = decodedRangeToHostRange(escaper, valueRangeInHost, decodedStart,
					decodedEnd);

			if (rangeInHost == null) {
				continue;
			}

			ranges.add(rangeInHost.shiftRight(startOffset));
			matchResult = matchFunction.find(decoded.toString(), index);
		}

		return ranges;
	}

	private static @Nullable TextRange decodedRangeToHostRange(LiteralTextEscaper<?> escaper,
			TextRange hostRange, int decodedStart, int decodedEnd) {

		if (decodedStart < 0 || decodedEnd < decodedStart) {
			return null;
		}

		if (decodedStart == decodedEnd) {
			int hostOffset = escaper.getOffsetInHost(decodedStart, hostRange);
			if (hostOffset < 0) {
				return null;
			}
			return TextRange.create(hostOffset, hostOffset);
		}

		int hostStart = escaper.getOffsetInHost(decodedStart, hostRange);
		int hostEnd = escaper.getOffsetInHost(decodedEnd, hostRange);

		if (hostStart < 0) {
			return null;
		}

		if (hostEnd < 0) {
			/*
			 * Some escapers do not map the exclusive end offset directly. Fall back to
			 * mapping the last decoded character and advancing by one. This is not perfect
			 * for every escape sequence, but is a reasonable fallback.
			 */
			int lastHostOffset = escaper.getOffsetInHost(decodedEnd - 1, hostRange);
			if (lastHostOffset < 0) {
				return null;
			}
			hostEnd = lastHostOffset + 1;
		}

		if (hostEnd < hostStart) {
			return null;
		}

		return TextRange.create(hostStart, hostEnd);
	}

	private MavenWrapperUtils() {
	}

	/**
	 * Return whether the given file is a Maven Wrapper properties file named
	 * {@code maven-wrapper.properties}.
	 */
	static boolean isWrapperFile(PropertiesFile file) {
		return WRAPPER_FILENAME.equals(file.getName());
	}

	/**
	 * Return whether the given file is a Maven Wrapper properties file named
	 * {@code maven-wrapper.properties}.
	 */
	static boolean isWrapperFile(@Nullable PsiFile file) {
		return file instanceof PropertiesFile propertiesFile && WRAPPER_FILENAME.equals(file.getName());
	}

	/**
	 * Return whether the given file is a Maven Wrapper properties file located at
	 * {@code .mvn/wrapper/maven-wrapper.properties}.
	 */
	static boolean isWrapperFileExact(@Nullable VirtualFile file) {

		if (file == null || !WRAPPER_FILENAME.equals(file.getName())) {
			return false;
		}

		VirtualFile parent = file.getParent();
		if (parent == null || !WRAPPER_DIR.equals(parent.getName())) {
			return false;
		}

		VirtualFile grandParent = parent.getParent();
		return grandParent != null && MVN_DIR.equals(grandParent.getName());
	}

	/**
	 * Return whether the given file is a Maven Wrapper properties file located at
	 * {@code .mvn/wrapper/maven-wrapper.properties}.
	 */
	static boolean isWrapperFileExact(@Nullable PsiFile file) {

		if (!isWrapperFile(file)) {
			return false;
		}

		PsiDirectory parent = file.getParent();
		if (parent == null || !WRAPPER_DIR.equals(parent.getName())) {
			return false;
		}

		PsiDirectory grandParent = parent.getParent();
		return grandParent != null && MVN_DIR.equals(grandParent.getName());
	}

	public static ProjectId createProjectId(VirtualFile virtualFile) {
		return new ProjectId("org.apache.maven", "apache-maven", virtualFile.getPath());
	}
}
