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

package biz.paluch.dap.gradle;

import java.util.List;

import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.kotlin.psi.*;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Kotlin string content with property references retained as {@code ${name}}.
 * <p>Supports literals, interpolation, property references,
 * {@code property(...)} and {@code extra["..."]}. Unsupported shapes contribute
 * no content. Use {@link #nameOf(KtElement)} for keys without placeholder
 * decoration.
 *
 * @author Mark Paluch
 */
class KtLiterals {

	private static final KtLiterals EMPTY = new KtLiterals(List.of());

	private final List<Segment> segments;

	private final @Nullable PropertySegment property;

	private final String text;

	private KtLiterals(Segment segment) {
		this(List.of(segment));
	}

	private KtLiterals(List<Segment> segments) {
		this.segments = segments;

		PropertySegment property = null;
		StringBuilder builder = new StringBuilder();
		for (Segment segment : segments) {
			if (property == null && segment instanceof PropertySegment propertySegment) {
				property = propertySegment;
			}
			builder.append(segment.render());
		}

		this.property = property;
		this.text = builder.toString();
	}

	/**
	 * Read string content and property references, ignoring unsupported PSI shapes.
	 */
	public static KtLiterals from(@Nullable KtElement element) {

		switch (element) {
		case null -> {
			return EMPTY;
		}
		case KtStringTemplateExpression template -> {

			List<Segment> segments = JBIterable.of(template.getEntries())
					.flatMap(entry -> from(entry).segments).toList();
			return new KtLiterals(segments);
		}
		case KtBlockStringTemplateEntry block -> {

			List<Segment> segments = JBIterable.from(block.getExpressions())
					.flatMap(expression -> from(expression).segments).toList();
			return new KtLiterals(segments);
		}
		case KtStringTemplateEntryWithExpression entry -> {
			return from(entry.getExpression());
		}
		case KtLiteralStringTemplateEntry entry -> {
			return new KtLiterals(new TextSegment(entry.getText()));
		}
		case KtEscapeStringTemplateEntry entry -> {
			return new KtLiterals(new TextSegment(entry.getUnescapedValue()));
		}
		case KtCallExpression call when isPropertyCall(call) -> {
			return fromPropertyCall(call);
		}
		case KtDotQualifiedExpression dotQualified when dotQualified
				.getSelectorExpression() instanceof KtCallExpression selector
				&& isPropertyCall(selector) -> {
			return fromPropertyCall(selector);
		}
		case KtArrayAccessExpression arrayAccess when isExtraAccess(arrayAccess) -> {
			return fromExtraAccess(arrayAccess);
		}
		case KtParenthesizedExpression parenthesized -> {
			return from(parenthesized.getExpression());
		}
		case KtNameReferenceExpression nameRef -> {
			return property(nameRef.getReferencedName());
		}
		default -> {
			return EMPTY;
		}
		}
	}

	/**
	 * Render value text with {@code ${name}} placeholders.
	 * @return an empty string for unsupported shapes.
	 * @throws IllegalArgumentException if the element is {@literal null}.
	 */
	public static String getText(KtElement element) {
		Assert.notNull(element, "Element must not be null");
		return KtLiterals.from(element).getText();
	}

	/**
	 * Read a property name or key without placeholder decoration.
	 * @return an empty string for unsupported shapes.
	 */
	public static String nameOf(@Nullable KtElement element) {

		if (element == null) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		for (Segment segment : from(element).segments) {
			builder.append(segment instanceof PropertySegment property ? property.name() : segment.render());
		}

		return builder.toString();
	}

	private static boolean isPropertyCall(KtCallExpression call) {
		return "property".equals(KotlinDslUtils.getKotlinCallName(call));
	}

	private static KtLiterals fromPropertyCall(KtCallExpression call) {
		return property(nameOf(KotlinDslUtils.getFirstValueArgument(call)));
	}

	private static boolean isExtraAccess(KtArrayAccessExpression arrayAccess) {

		KtExpression array = arrayAccess.getArrayExpression();
		if (array instanceof KtNameReferenceExpression reference) {
			return "extra".equals(reference.getReferencedName());
		}

		return array instanceof KtDotQualifiedExpression dotQualified
				&& dotQualified.getSelectorExpression() instanceof KtNameReferenceExpression selector
				&& "extra".equals(selector.getReferencedName());
	}

	private static KtLiterals fromExtraAccess(KtArrayAccessExpression arrayAccess) {

		for (KtExpression index : arrayAccess.getIndexExpressions()) {

			String propertyName = nameOf(index);
			if (StringUtils.hasText(propertyName)) {
				return property(propertyName);
			}
		}

		return EMPTY;
	}

	private static KtLiterals property(@Nullable String propertyName) {
		return StringUtils.hasText(propertyName) ? new KtLiterals(new PropertySegment(propertyName)) : EMPTY;
	}

	public boolean hasProperty() {
		return property != null;
	}

	/**
	 * Return whether the value contains non-whitespace text or a property
	 * placeholder.
	 */
	public boolean hasText() {
		return StringUtils.hasText(text);
	}

	/**
	 * Return the first referenced property name.
	 * @throws IllegalStateException if no property is referenced.
	 */
	public String getProperty() {
		if (property != null) {
			return property.name();
		}
		throw new IllegalStateException("No property found");
	}

	public String getText() {
		return text;
	}

	/**
	 * Return the first property as an expression, or the literal text if none
	 * exists.
	 */
	public Expression toExpression() {
		return property != null ? Expression.property(property.name()) : Expression.from(text);
	}

	@Override
	public String toString() {
		return text;
	}

	/**
	 * Resolve property references, leaving unknown values as {@code ${name}}
	 * placeholders.
	 */
	public String toString(PropertyResolver propertyResolver) {

		StringBuilder builder = new StringBuilder();

		for (Segment segment : segments) {

			if (segment instanceof PropertySegment property) {
				String value = propertyResolver.getProperty(property.name());
				builder.append(value != null ? value : property.render());
			} else {
				builder.append(segment.render());
			}
		}

		return builder.toString();
	}

	private sealed interface Segment permits TextSegment, PropertySegment {

		String render();

	}

	private record TextSegment(String text) implements Segment {

		@Override
		public String render() {
			return text;
		}

	}

	private record PropertySegment(String name) implements Segment {

		@Override
		public String render() {
			return "${" + name + "}";
		}

	}

}
