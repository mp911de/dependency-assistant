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

package biz.paluch.dap.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;

import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Helpers for inspecting and editing Java {@link PropertyImpl property}
 * elements in IntelliJ {@code .properties} PSI trees.
 *
 * <p>Mainly for internal use within the plugin. Methods cover three concerns
 * that recur across inspections, completion contributors, and quick-fixes:
 * navigating from an arbitrary PSI element to its enclosing property,
 * translating ranges expressed against the
 * {@linkplain PropertyImpl#createLiteralTextEscaper() decoded value text} into
 * offsets the editor can highlight, and commenting out a property without
 * breaking up its physical lines.
 *
 * @author Mark Paluch
 * @see PropertyImpl
 * @see PropertyValueImpl
 * @see MatchFunction
 */
public class PropertyUtils {

	/**
	 * Translate ranges within the decoded value of {@code property} into ranges
	 * relative to the property element.
	 *
	 * <p>Decoded ranges address offsets in the property's
	 * {@linkplain PropertyImpl#getUnescapedValue() decoded value text}. Returned
	 * ranges are mapped back through the literal-text escaper and shifted to
	 * offsets relative to {@code property}, suitable for
	 * {@code ProblemsHolder.problem(property, ...).range(range)} where the range
	 * must be expressed relative to the reported element.
	 *
	 * @param property the property whose decoded value the ranges refer to; must
	 * not be {@literal null}.
	 * @param decodedRanges decoded-text ranges to translate; an empty list yields
	 * an empty result.
	 * @return property-relative ranges in match order. Decoded ranges that fall
	 * outside the value or fail to map back to host offsets are silently dropped.
	 */
	public static List<TextRange> mapDecodedRanges(PropertyImpl property, List<TextRange> decodedRanges) {

		if (decodedRanges.isEmpty()) {
			return List.of();
		}

		List<TextRange> absolute = findTextRanges(property, (text, startIndex) -> {
			for (TextRange range : decodedRanges) {
				if (range.getStartOffset() >= startIndex && range.getEndOffset() <= text.length()) {
					return MatchFunction.match(range.substring(text), range.getStartOffset(), range.getEndOffset());
				}
			}
			return MatchFunction.noMatch();
		});
		int propertyStart = property.getTextRange().getStartOffset();
		return absolute.stream().map(it -> it.shiftLeft(propertyStart)).toList();
	}

	/**
	 * Return the property enclosing the supplied element.
	 *
	 * @param element the element to inspect; can be {@literal null}.
	 * @return the enclosing property, or {@literal null} if {@code element} is
	 * {@literal null} or no enclosing property exists.
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
	 * Return the value child of the supplied property, or {@code element} itself
	 * when it is already a value element.
	 *
	 * @param element the property element to descend into, or a value element to
	 * return directly; must not be {@literal null}.
	 * @return the property value element, or {@literal null} when {@code element}
	 * is neither a property nor a value.
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
	 * Return the property-relative range of the property's value node, or
	 * {@literal null} when the property does not have a value (e.g. a bare key line
	 * such as {@code foo=}).
	 *
	 * @param property the property to inspect; must not be {@literal null}.
	 * @return the value range relative to the property element, or {@literal null}
	 * when no value node exists.
	 */
	public static @Nullable TextRange valueRangeInElement(PropertyImpl property) {

		ASTNode valueNode = property.getValueNode();
		if (valueNode == null) {
			return null;
		}
		int propertyStart = property.getTextRange().getStartOffset();
		return valueNode.getTextRange().shiftLeft(propertyStart);
	}

	/**
	 * Return whether the raw property value text contains an unescaped trailing
	 * backslash followed by a CR/LF the Java {@code .properties} line continuation
	 * idiom.
	 *
	 * <p>Wrapper parsers silently reject such values because PSI decoding joins
	 * multi-line values into a single logical string that does not round-trip
	 * cleanly through the regex-based URL matchers.
	 *
	 * @param rawText the raw PSI text of a {@link PropertyValueImpl}; must not be
	 * {@literal null}.
	 * @return {@literal true} if {@code rawText} contains a line continuation.
	 */
	public static boolean containsLineContinuation(String rawText) {

		int index = 0;
		while (index < rawText.length()) {

			char c = rawText.charAt(index);
			if (c != '\\') {
				index++;
				continue;
			}

			int run = 0;
			int i = index;
			while (i < rawText.length() && rawText.charAt(i) == '\\') {
				run++;
				i++;
			}
			if (run % 2 == 1 && i < rawText.length()) {
				char next = rawText.charAt(i);
				if (next == '\n' || next == '\r') {
					return true;
				}
			}
			index = i;
		}
		return false;
	}

	/**
	 * Locate the first range in {@code property}'s value that matches, falling back
	 * to {@code fallbackElement}'s range when no match is found.
	 *
	 * @param property the property whose value should be searched; must not be
	 * {@literal null}.
	 * @param fallbackElement the element whose range is returned when no match maps
	 * cleanly; must not be {@literal null}.
	 * @param matchFunction the match function evaluated against the decoded value;
	 * must not be {@literal null}.
	 * @return the first matching range, or {@code fallbackElement}'s range when no
	 * match is available.
	 * @see #findTextRanges(PropertyImpl, PsiElement, MatchFunction)
	 */
	public static TextRange findTextRange(PropertyImpl property, PsiElement fallbackElement,
			MatchFunction matchFunction) {
		return findTextRanges(property, fallbackElement, matchFunction).getFirst();
	}

	/**
	 * Locate ranges in {@code property}'s value, falling back to
	 * {@code fallbackElement}'s range when no match is found.
	 *
	 * @param property the property whose value should be searched; must not be
	 * {@literal null}.
	 * @param fallbackElement the element whose range is returned when no match maps
	 * cleanly; must not be {@literal null}.
	 * @param matchFunction the match function evaluated against the decoded value;
	 * must not be {@literal null}.
	 * @return file-absolute ranges in match order; never empty, falling back to a
	 * single-element list with {@code fallbackElement}'s range when no match is
	 * available.
	 * @see #findTextRanges(PropertyImpl, MatchFunction)
	 */
	public static List<TextRange> findTextRanges(PropertyImpl property, PsiElement fallbackElement,
			MatchFunction matchFunction) {

		List<TextRange> ranges = findTextRanges(property, matchFunction);
		return ranges.isEmpty() ? List.of(fallbackElement.getTextRange()) : ranges;
	}

	/**
	 * Locate ranges in {@code property}'s value that correspond to matches produced
	 * by {@code matchFunction} against the decoded value text.
	 *
	 * <p>Matching runs against the value after applying the property's
	 * {@linkplain PropertyImpl#createLiteralTextEscaper() literal text escaper}, so
	 * {@code matchFunction} expresses coordinates in terms of the logical value
	 * rather than the raw source. Decoding is restricted to the
	 * {@linkplain PropertyImpl#getValueNode() value node}; the default
	 * {@link LiteralTextEscaper#getRelevantTextRange() relevant text range} would
	 * cover the whole property (key, separator, and value) and shift every decoded
	 * position by the key length.
	 *
	 * @param property the property whose value should be searched; must not be
	 * {@literal null}.
	 * @param matchFunction the match function evaluated against the decoded value;
	 * must not be {@literal null}.
	 * @return file-absolute ranges, in match order, of each successful match; an
	 * empty list when the property has no value node, the escaper rejects the
	 * value, or the match function returns no matches.
	 */
	public static List<TextRange> findTextRanges(PropertyImpl property, MatchFunction matchFunction) {

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

		String decodedText = decoded.toString();
		int index = 0;
		MatchResult matchResult = matchFunction.find(decodedText, index);
		List<TextRange> ranges = new ArrayList<>();

		while (matchResult.hasMatch()) {
			int decodedStart = matchResult.start();
			int decodedEnd = matchResult.end();
			index = matchResult.end();
			matchResult = matchFunction.find(decodedText, index);

			TextRange rangeInHost = decodedRangeToHostRange(escaper, valueRangeInHost, decodedStart,
					decodedEnd);

			if (rangeInHost != null) {
				ranges.add(rangeInHost.shiftRight(startOffset));
			}
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
	 * Replace {@code property} with comment lines that mirror its original physical
	 * lines.
	 *
	 * <p>Each physical line of the property's source text is prefixed with
	 * {@code "# "} and the resulting comments are inserted in place of the
	 * property, preserving multi-line declarations with continuation backslashes.
	 * The edit mutates the PSI tree and must run inside a write action.
	 *
	 * @param property the property to comment out; must not be {@literal null}.
	 */
	public static void commentOut(PropertyImpl property) {

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

	/**
	 * Prefix every physical line in {@code text} with {@code "# "}.
	 */
	protected static String commentEveryPhysicalLine(String text) {

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

}
