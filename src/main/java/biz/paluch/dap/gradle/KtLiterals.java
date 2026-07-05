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

package biz.paluch.dap.gradle;

import java.util.List;

import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.kotlin.psi.*;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Canonical representation of a Kotlin DSL string-template value, decomposed
 * into ordered {@link Segment fragments} of literal text and {@code $property}
 * references.
 *
 * <p>Supported inputs include plain string literals, interpolated fragments,
 * bare property references, {@code property(...)} lookups, {@code extra["..."]}
 * access (also qualified as {@code project.extra["..."]}), and argument lists
 * whose arguments are of the former shapes. Unsupported PSI shapes are
 * represented as an empty instance.
 *
 * <p>The value position is served by {@link #from(KtElement)} and
 * {@link #getText(KtElement)}, rendering property references as {@code ${name}}
 * placeholders. Name positions (property keys, {@code extra} indices) are
 * served by {@link #nameOf(KtElement)}, rendering property references as their
 * bare identifier.
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
	 * Return the shared instance that carries no fragments and renders to the empty
	 * string.
	 *
	 * @return the empty {@link KtLiterals} instance.
	 */
	public static KtLiterals empty() {
		return EMPTY;
	}

	/**
	 * Create a {@link KtLiterals} view for the supplied Kotlin PSI element in value
	 * position: references, {@code property(...)} lookups, and {@code extra["..."]}
	 * access contribute property fragments; string-template text contributes
	 * literal fragments. Unsupported shapes yield {@link #empty()}.
	 * @param element the Kotlin PSI element to inspect.
	 * @return a {@link KtLiterals} representing the supported fragments contained
	 * in the element.
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
			return new KtLiterals(new TextSegment(entry.getText(), entry));
		}
		case KtEscapeStringTemplateEntry entry -> {
			return new KtLiterals(new TextSegment(entry.getUnescapedValue(), entry));
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
			return property(nameRef.getReferencedName(), nameRef);
		}
		case KtReferenceExpression reference -> {
			return property(reference.getName(), reference);
		}
		case KtValueArgumentList argumentList -> {

			List<Segment> segments = JBIterable.from(argumentList.getArguments())
					.map(KtValueArgument::getArgumentExpression)
					.flatMap(expression -> from(expression).segments).toList();
			return new KtLiterals(segments);
		}
		default -> {
			return EMPTY;
		}
		}
	}

	/**
	 * Extract text from supported Kotlin DSL literal forms in value position,
	 * rendering property references as {@code ${name}} placeholders.
	 * <p>Unsupported element shapes yield an empty string rather than an exception.
	 * @param element the Kotlin PSI element to inspect .
	 * @return the rendered text, or the empty string for unsupported shapes.
	 * @throws IllegalArgumentException if {@code element} is {@literal null}.
	 */
	public static String getText(KtElement element) {
		Assert.notNull(element, "Element must not be null");
		return KtLiterals.from(element).getText();
	}

	/**
	 * Extract a property name or key from a Kotlin DSL name position such as a
	 * {@code property(...)} argument, an {@code extra["..."]} index, or an
	 * assignment target. References render as their bare identifier instead of a
	 * {@code ${name}} placeholder.
	 * @param element the Kotlin PSI element to inspect.
	 * @return the extracted name, or the empty string for unsupported shapes.
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

		KtExpression argument = KotlinDslUtils.getFirstValueArgument(call);
		if (argument == null) {
			return EMPTY;
		}

		return property(nameOf(argument), argument);
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
				return new KtLiterals(new PropertySegment(propertyName, index));
			}
		}

		return EMPTY;
	}

	private static KtLiterals property(@Nullable String propertyName, KtElement element) {

		if (StringUtils.isEmpty(propertyName)) {
			return EMPTY;
		}

		return new KtLiterals(new PropertySegment(propertyName, element));
	}

	/**
	 * Return whether any fragment resolves to a property reference.
	 *
	 * @return {@literal true} if at least one fragment is property-backed.
	 */
	public boolean hasProperty() {
		return property != null;
	}

	/**
	 * Return whether this instance contains any renderable content.
	 * <p>Property references count as content because they participate in the
	 * rendered form returned by {@link #toString()}.
	 *
	 * @return {@literal true} if at least one fragment contributes text or a
	 * property placeholder.
	 */
	public boolean hasText() {

		for (Segment segment : segments) {
			if (segment.hasText()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Return the first property reference represented by this instance.
	 * <p>
	 * If multiple property fragments are present, the first fragment in
	 * encounter order is returned.
	 *
	 * @return the referenced property name without decoration.
	 * @throws IllegalStateException if {@link #hasProperty()} is {@literal false}.
	 */
	public String getProperty() {
		if (property != null) {
			return property.name();
		}
		throw new IllegalStateException("No property found");
	}

	/**
	 * Return the rendered content of all fragments in encounter order, with
	 * property references rendered as {@code ${name}}. Equivalent to
	 * {@link #toString()}.
	 */
	public String getText() {
		return text;
	}

	/**
	 * Render the collected fragments in encounter order.
	 * <p>Plain literals are concatenated as-is. Property fragments are rendered as
	 * {@code ${property}}.
	 *
	 * @return the rendered literal content, or the empty string if no fragments are
	 * present.
	 */
	@Override
	public String toString() {
		return text;
	}

	/**
	 * Render the literals to {@code String} and resolve any property references.
	 * <p>Property fragments that the resolver cannot resolve fall back to their
	 * {@code ${name}} placeholder, so a property-only value still renders content.
	 * @param propertyResolver the property resolver to use.
	 * @return the rendered literal content, or the empty string only when no
	 * fragments are present.
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

	/**
	 * Single normalized fragment of a Kotlin DSL string-template value: either
	 * concrete {@link TextSegment text} or a {@link PropertySegment property
	 * reference}, together with its source PSI element.
	 */
	private sealed interface Segment permits TextSegment, PropertySegment {

		/**
		 * The PSI element this fragment was derived from.
		 */
		KtElement element();

		/**
		 * Return whether this fragment contributes any renderable content.
		 */
		boolean hasText();

		/**
		 * Render this fragment as concrete text or a {@code ${property}} placeholder.
		 */
		String render();

	}

	private record TextSegment(String text, KtElement element) implements Segment {

		@Override
		public boolean hasText() {
			return StringUtils.hasText(text);
		}

		@Override
		public String render() {
			return text;
		}

	}

	private record PropertySegment(String name, KtElement element) implements Segment {

		@Override
		public boolean hasText() {
			return StringUtils.hasText(name);
		}

		@Override
		public String render() {
			return "${" + name + "}";
		}

	}

}
