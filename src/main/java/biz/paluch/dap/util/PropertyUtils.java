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

package biz.paluch.dap.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;

import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.Property;
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
 * Utilities for Java {@link Property properties} in IntelliJ PSI.
 * <p>Reading live PSI requires a read action.
 *
 * @author Mark Paluch
 */
public class PropertyUtils {

	/**
	 * Map decoded value ranges to offsets relative to the property element.
	 * <p>Use these ranges when reporting an inspection problem on the property.
	 * @param decodedRanges non-empty, non-overlapping ranges in ascending order.
	 * The list itself may be empty.
	 * @return property-relative ranges in the supplied order. Ranges outside the
	 * value or without a corresponding source range are omitted.
	 */
	public static List<TextRange> mapDecodedRanges(Property property, List<TextRange> decodedRanges) {

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
	 * Return the element itself if it is a property, or its enclosing property.
	 * @return {@literal null} if the element is absent or no property encloses it.
	 */
	public static @Nullable Property findProperty(@Nullable PsiElement element) {

		if (element instanceof Property property) {
			return property;
		}
		if (element != null) {
			return PsiTreeUtil.getParentOfType(element, Property.class, false);
		}
		return null;
	}

	/**
	 * Return the property's value element, or the element itself if it is a value.
	 * @return {@literal null} if no value element is available.
	 */
	public static @Nullable PsiElement findPropertyValue(PsiElement element) {

		if (element.getNode().getElementType() == PropertiesTokenTypes.VALUE_CHARACTERS) {
			return element;
		}

		if (element instanceof Property property) {
			List<PsiElement> values = SyntaxTraverser.psiTraverser(property)
					.filter(it -> it.getNode().getElementType() == PropertiesTokenTypes.VALUE_CHARACTERS)
					.toList();
			return values.isEmpty() ? null : values.getFirst();
		}

		return null;
	}

	/**
	 * Return the value range relative to the property element, or {@literal null}
	 * if the property has no value element.
	 */
	public static @Nullable TextRange valueRangeInElement(Property property) {

		PsiElement value = findPropertyValue(property);
		if (value == null) {
			return null;
		}
		int propertyStart = property.getTextRange().getStartOffset();
		return value.getTextRange().shiftLeft(propertyStart);
	}

	/**
	 * Detect line continuations in raw property value text.
	 * <p>A continuation is an unescaped backslash followed by CR or LF.
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
	 * Return the first range from
	 * {@link #findTextRanges(Property, PsiElement, MatchFunction)}.
	 */
	public static TextRange findTextRange(Property property, PsiElement fallbackElement,
			MatchFunction matchFunction) {
		return findTextRanges(property, fallbackElement, matchFunction).getFirst();
	}

	/**
	 * Find ranges as in {@link #findTextRanges(Property, MatchFunction)}.
	 * <p>If none are found, return only {@code fallbackElement}'s file-absolute
	 * range.
	 */
	public static List<TextRange> findTextRanges(Property property, PsiElement fallbackElement,
			MatchFunction matchFunction) {

		List<TextRange> ranges = findTextRanges(property, matchFunction);
		return ranges.isEmpty() ? List.of(fallbackElement.getTextRange()) : ranges;
	}

	/**
	 * Find matches in the decoded property value and map them to source ranges.
	 * <p>Match offsets start at the decoded value, excluding the key and separator.
	 * Matches that cannot be mapped to source offsets are omitted.
	 * @return file-absolute ranges in match order. Empty if the value cannot be
	 * decoded or no matches can be mapped.
	 */
	public static List<TextRange> findTextRanges(Property property, MatchFunction matchFunction) {

		if (!(property instanceof PsiLanguageInjectionHost host)) {
			return List.of();
		}
		LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = host.createLiteralTextEscaper();
		PsiElement first = findPropertyValue(property);

		if (first == null) {
			return List.of();
		}
		int startOffset = property.getTextRange().getStartOffset();
		// The escaper's default range includes the key and separator, shifting match
		// offsets.
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
	 * Comment out every physical line of the property, including continued lines.
	 * <p>Requires a write action.
	 */
	public static void commentOut(Property property) {

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
		PsiElement replaced = property.replace(list.getFirst());
		if (list.size() > 1) {
			parent.addRangeBefore(list.get(1), list.getLast(), replaced);
		}
	}

	/**
	 * Prefix each LF-delimited line with {@code "# "}.
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
