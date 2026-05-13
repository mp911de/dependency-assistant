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
import java.util.regex.MatchResult;
import java.util.regex.Matcher;

import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.maven.MavenWrapperParser.WrapperEntry;
import biz.paluch.dap.maven.MavenWrapperParser.WrapperProperty;
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

import org.springframework.util.Assert;

/**
 * Detection, project-id, and release-source helpers for
 * {@code .mvn/wrapper/maven-wrapper.properties} files.
 *
 * @author Mark Paluch
 */
class MavenWrapperUtil {

	/**
	 * Return whether the given element is the value of a recognized wrapper URL
	 * property in a wrapper-rule file.
	 */
	static boolean isVersionElement(@Nullable PsiElement element) {

		if (element == null) {
			return false;
		}

		PropertyValueImpl value = element instanceof PropertyValueImpl pv ? pv
				: PsiTreeUtil.getParentOfType(element, PropertyValueImpl.class, false);
		if (value == null) {
			return false;
		}
		if (!MavenUtils.isWrapperFile(value.getContainingFile())) {
			return false;
		}

		if (!(value.getParent() instanceof IProperty property)) {
			return false;
		}

		String key = property.getUnescapedKey();
		if (key == null) {
			return false;
		}
		for (WrapperProperty wp : WrapperProperty.values()) {
			if (wp.key().equals(key)) {
				return true;
			}
		}
		return false;
	}

	interface MatchFunction {


		MatchResult find(String text, int startIndex);

		static MatchResult group(String group, Matcher matcher) {
			return new DefaultMatchResult(matcher.group(group), matcher.start(group), matcher.end(group));
		}

		static MatchFunction indexOf(String str) {

			Assert.hasText(str, "Search string must not be empty");

			return (text, startIndex) -> {
				int index = text.indexOf(str, startIndex);
				if (index < 0) {
					return AbsentMatchResult.ABSENT;
				}
				return new DefaultMatchResult(str, index, index + str.length());
			};
		}

		static MatchResult noMatch() {
			return AbsentMatchResult.ABSENT;
		}

	}

	public static TextRange findTextRange(
			PropertyImpl property, PsiElement fallbackElement,
			MatchFunction matchFunction) {
		return findTextRanges(property, fallbackElement, matchFunction).getFirst();
	}

	/**
	 * Locate {@link TextRange ranges} in {@code property}'s value that correspond
	 * to matches produced by {@code matchFunction} against the decoded value text.
	 * <p>Decoding is restricted to the {@link PropertyImpl#getValueNode() value
	 * node}: the default {@link LiteralTextEscaper#getRelevantTextRange()} would
	 * cover the entire property (key, separator, and value) and would shift every
	 * decoded position by the key length.
	 * @param property the wrapper property; must not be {@literal null}.
	 * @param fallbackElement element whose range is returned when decoding or
	 * mapping fails; must not be {@literal null}.
	 * @param matchFunction the match function evaluated against the decoded value.
	 * @return file-absolute ranges of each match; never empty (falls back to
	 * {@code fallbackElement}'s range when no match maps cleanly).
	 */
	public static List<TextRange> findTextRanges(
			PropertyImpl property, PsiElement fallbackElement,
			MatchFunction matchFunction) {

		List<TextRange> ranges = findTextRanges(property, matchFunction);

		return ranges.isEmpty() ? List.of(fallbackElement.getTextRange()) : ranges;
	}

	/**
	 * Locate {@link TextRange ranges} in {@code property}'s value that correspond
	 * to matches produced by {@code matchFunction} against the decoded value text.
	 *
	 * @param property the wrapper property; must not be {@literal null}.
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
	 */
	static List<ReleaseSource> collectReleaseSources(PsiFile wrapperFile) {

		List<WrapperEntry> entries = new MavenWrapperParser().parse(wrapperFile);
		Set<RemoteRepository> repositories = new LinkedHashSet<>();
		for (WrapperEntry entry : entries) {
			repositories.add(entry.repository());
		}
		return MavenUtils.getReleaseSources(repositories);
	}

	private MavenWrapperUtil() {
	}

}
