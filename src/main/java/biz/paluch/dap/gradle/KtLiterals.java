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
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.kotlin.psi.KtArrayAccessExpression;
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.KtReferenceExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Canonical representation of a Kotlin DSL string-template value, decomposed
 * into ordered {@link Segment fragments} of literal text and {@code $property}
 * references.
 *
 * <p>Supported inputs include plain string literals, interpolated fragments,
 * direct property references, {@code property(...)} lookups, and
 * {@code extra["..."]} access. Unsupported PSI shapes are represented as an
 * empty instance.
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
	 * @return the empty {@link KtLiterals} instance, never {@literal null}.
	 */
	public static KtLiterals empty() {
		return EMPTY;
	}

	/**
	 * Create a {@link KtLiterals} view for the supplied Kotlin PSI element.
	 * @param element the Kotlin PSI element to inspect.
	 * @return a {@link KtLiterals} representing the supported fragments contained
	 * in the element.
	 */
	public static KtLiterals from(@Nullable KtElement element) {

		switch (element) {
		case null -> {
			return EMPTY;
		}
		case KtCallExpression call when "property".equals(KotlinDslUtils.getKotlinCallName(call)) -> {
			return KtLiterals.fromPropertyCall(call);
		}
		case KtStringTemplateExpression expression -> {

			List<Segment> segments = JBIterable.of(expression.getEntries())
					.map(KtLiterals::fromPropertyCandidate)
					.flatMap(KtLiterals::getSegments).toList();
			return new KtLiterals(segments);
		}
		case KtBlockStringTemplateEntry block -> {
			List<Segment> segments = JBIterable.from(block.getExpressions())
					.map(KtLiterals::fromPropertyCandidate)
					.flatMap(KtLiterals::getSegments).toList();
			return new KtLiterals(segments);
		}
		case KtLiteralStringTemplateEntry entry -> {
			return new KtLiterals(new TextSegment(entry.getText(), entry));
		}
		case KtNameReferenceExpression nameRef -> {
			if (StringUtils.isEmpty(nameRef.getReferencedName())) {
				return KtLiterals.empty();
			}

			return new KtLiterals(new TextSegment(nameRef.getReferencedName(), nameRef));
		}
		case KtArrayAccessExpression arrayAccess when arrayAccess.getArrayExpression() != null
				&& "extra".equals(arrayAccess.getArrayExpression().getText()) -> {
			List<KtExpression> indices = arrayAccess.getIndexExpressions();

			for (KtExpression index : indices) {
				KtLiterals literals = from(index);
				if (literals.hasText()) {
					return literals.asProperty();
				}
			}
		}
		case KtReferenceExpression nameRef -> {
			if (StringUtils.isEmpty(nameRef.getName())) {
				return KtLiterals.empty();
			}

			return KtLiterals.fromProperty(nameRef);
		}
		default -> {
		}
		}

		if (element instanceof KtDotQualifiedExpression dotQualified
				&& dotQualified.getSelectorExpression() instanceof KtCallExpression selectorCall
				&& "property".equals(KotlinDslUtils.getKotlinCallName(selectorCall))) {
			return KtLiterals.from(selectorCall);
		}

		List<Segment> segments = SyntaxTraverser.psiTraverser(element)
				.expand(it -> !(it instanceof KtStringTemplateExpression
						|| it instanceof KtNameReferenceExpression))
				.filter(KtElement.class)
				.flatMap(it -> {

					if (it instanceof KtNameReferenceExpression referenceExpression) {
						if (StringUtils.hasText(referenceExpression.getReferencedName())) {
							return KtLiterals.property(referenceExpression.getReferencedName(),
									referenceExpression).getSegments();
						}

						if (StringUtils.hasText(referenceExpression.getName())) {
							return KtLiterals.property(referenceExpression.getName(), referenceExpression)
									.getSegments();
						}
					}

					if (it instanceof KtStringTemplateExpression kse) {

						KtStringTemplateExpression parentOfType = PsiTreeUtil.getParentOfType(it,
								KtStringTemplateExpression.class);
						if (parentOfType == null) {
							return from(kse).getSegments();
						}
						return JBIterable.empty();
					}

					return JBIterable.empty();
				}).toList();

		return new KtLiterals(segments);
	}

	private static KtLiterals fromPropertyCandidate(KtElement it) {
		KtLiterals inner = from(it);
		return it instanceof KtReferenceExpression ? inner.asProperty() : inner;
	}

	/**
	 * Extract text from supported Kotlin DSL literal forms.
	 * <p>Used for property keys, dependency coordinates, and simple synthesized
	 * string values. Unsupported element shapes yield an empty string rather than
	 * an exception.
	 * @param element the Kotlin PSI element to inspect; must not be
	 * {@literal null}.
	 * @return the rendered text, or the empty string for unsupported shapes.
	 * @throws IllegalArgumentException if {@code element} is {@literal null}.
	 */
	public static String getText(KtElement element) {
		Assert.notNull(element, "Element must not be null");
		return KtLiterals.from(element).getText();
	}

	/**
	 * Create a property-backed {@link KtLiterals} from a Kotlin DSL
	 * {@code property(...)} call.
	 *
	 * @param call the {@code property(...)} call to inspect.
	 * @return a {@link KtLiterals} containing a single property fragment.
	 */
	public static KtLiterals fromPropertyCall(KtCallExpression call) {
		KtExpression arg = KotlinDslUtils.getFirstValueArgument(call);
		if (arg == null) {
			return KtLiterals.empty();
		}
		String propertyName = getText(arg);
		return property(propertyName, arg);
	}

	/**
	 * Create a {@link KtLiterals} instance representing a single property
	 * placeholder.
	 *
	 * @param propertyName the property name to expose.
	 * @param element the source element associated with the property.
	 * @return a {@link KtLiterals} containing one property fragment.
	 */
	public static KtLiterals property(String propertyName, KtElement element) {
		return new KtLiterals(new PropertySegment(propertyName, element));
	}

	/**
	 * Create a property-backed {@link KtLiterals} from a Kotlin reference
	 * expression.
	 *
	 * @param ref the property reference.
	 * @return a {@link KtLiterals} containing a single property fragment.
	 */
	public static KtLiterals fromProperty(KtReferenceExpression ref) {
		String name = ref.getName();
		Assert.hasText(name, "Reference must have a name");
		return KtLiterals.property(name, ref);
	}

	private List<Segment> getSegments() {
		return segments;
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
	 * Create a {@link KtLiterals} instance that represents a single property.
	 */
	public KtLiterals asProperty() {
		if (property != null) {
			return new KtLiterals(property);
		}
		if (segments.isEmpty()) {
			return EMPTY;
		}
		Segment first = segments.getFirst();
		if (first instanceof TextSegment(String text, KtElement element)) {
			return new KtLiterals(new PropertySegment(text, element));
		}
		return new KtLiterals(first);
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
