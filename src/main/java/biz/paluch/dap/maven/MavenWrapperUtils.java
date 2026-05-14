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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;

import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.RemoteRepository;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
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
	public static final String COMPLETION_PLACEHOLDER = "IntellijIdeaRulezzz";

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

		StrippedValue stripped = StrippedValue.of(value, COMPLETION_PLACEHOLDER);
		Matcher matcher = MavenWrapperParser.MAVEN_ARTIFACT_PATTERN.matcher(stripped.text());
		if (!matcher.find()) {
			return List.of();
		}

		int v1Start = stripped.toOriginal(matcher.start("version1"));
		int v1End = stripped.toOriginal(matcher.end("version1"));
		int v2Start = stripped.toOriginal(matcher.start("version2"));
		int v2End = stripped.toOriginal(matcher.end("version2"));

		return findTextRanges(property, (str, index) -> {
			if (index < v1Start) {
				return new DefaultMatchResult(value.substring(v1Start, v1End), v1Start, v1End);
			}
			if (index < v2Start) {
				return new DefaultMatchResult(value.substring(v2Start, v2End), v2Start, v2End);
			}
			return MatchFunction.noMatch();
		});
	}

	/**
	 * Decoded property value with every {@link #COMPLETION_PLACEHOLDER} occurrence
	 * removed. {@link #toOriginal(int)} maps a position in the stripped text back
	 * to the corresponding position in the original (placeholder-bearing) text.
	 */
	private record StrippedValue(String text, int[] strippedPositions, int placeholderLength) {

		static StrippedValue of(String value, String placeholder) {

			int first = value.indexOf(placeholder);
			if (first < 0) {
				return new StrippedValue(value, new int[0], placeholder.length());
			}

			StringBuilder stripped = new StringBuilder(value.length());
			List<Integer> positions = new ArrayList<>();
			int idx = 0;
			int found = first;
			while (found >= 0) {
				stripped.append(value, idx, found);
				positions.add(stripped.length());
				idx = found + placeholder.length();
				found = value.indexOf(placeholder, idx);
			}
			stripped.append(value, idx, value.length());

			int[] array = new int[positions.size()];
			for (int i = 0; i < array.length; i++) {
				array[i] = positions.get(i);
			}
			return new StrippedValue(stripped.toString(), array, placeholder.length());
		}

		int toOriginal(int strippedPos) {

			int shift = 0;
			for (int position : strippedPositions) {
				if (position < strippedPos) {
					shift += placeholderLength;
				}
			}
			return strippedPos + shift;
		}

	}

	/**
	 * Return whether the given element is the value of a recognized wrapper URL
	 * property in a wrapper-rule file.
	 * @param element the PSI element to inspect.
	 * @return {@code true} if the element represents a supported wrapper version.
	 */
	static boolean isVersionElement(@Nullable PsiElement element) {

		if (!(element instanceof PropertyValueImpl value)) {
			return false;
		}

		if (!MavenUtils.isWrapperFile(value.getContainingFile())) {
			return false;
		}

		if (!(value.getParent() instanceof IProperty property)) {
			return false;
		}

		return WrapperProperty.isWrapperProperty(property);
	}

	/**
	 * Return the property value element for the given property or value element.
	 * @param element the property or value element to inspect.
	 * @return the property value element, or {@code null} if none can be found.
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
	 * @return the enclosing property, or {@code null} if the element is not part of
	 * a property.
	 */
	public static @Nullable PropertyImpl findProperty(@Nullable PsiElement element) {
		return element instanceof PropertyImpl property ? property
				: (element != null && element.getParent() instanceof PropertyImpl parent ? parent : null);
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

	/**
	 * Return the wrapper-derived release sources for the given wrapper file,
	 * deduplicated by repository URL.
	 * @param wrapperFile the wrapper properties file.
	 * @return the release sources declared by supported wrapper URL properties.
	 */
	public static List<ReleaseSource> collectReleaseSources(PsiFile wrapperFile) {

		List<WrapperEntry> entries = new MavenWrapperParser().parse(wrapperFile);
		Set<RemoteRepository> repositories = new LinkedHashSet<>();
		for (WrapperEntry entry : entries) {
			repositories.add(entry.repository());
		}
		return MavenUtils.getReleaseSources(repositories);
	}

	private MavenWrapperUtils() {
	}

}
