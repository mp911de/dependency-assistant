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
 * Canonical representation of Kotlin DSL literal fragments.
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

	private final List<KtLiteral> literals;

	private final @Nullable KtLiteral property;

	private final String text;

	private KtLiterals(KtLiteral literal) {
		this(List.of(literal));
	}

	private KtLiterals(List<KtLiteral> literals) {
		this.literals = literals;

		KtLiteral property = null;
		StringBuilder builder = new StringBuilder();
		for (KtLiteral literal : literals) {
			if (property == null && literal.isProperty()) {
				property = literal;
			}
			builder.append(literal);
		}

		this.property = property;
		this.text = builder.toString();
	}

	/**
	 * Return an empty instance.
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

			List<KtLiteral> literals = JBIterable.of(expression.getEntries())
					.map(KtLiterals::fromPropertyCandidate)
					.flatMap(KtLiterals::getLiterals).toList();
			return new KtLiterals(literals);
		}
		case KtBlockStringTemplateEntry block -> {
			List<KtLiteral> literals = JBIterable.from(block.getExpressions())
					.map(KtLiterals::fromPropertyCandidate)
					.flatMap(KtLiterals::getLiterals).toList();
			return new KtLiterals(literals);
		}
		case KtLiteralStringTemplateEntry entry -> {
			return new KtLiterals(new KtLiteral(entry.getText(), null, entry));
		}
		case KtNameReferenceExpression nameRef -> {
			if (StringUtils.isEmpty(nameRef.getReferencedName())) {
				return KtLiterals.empty();
			}

			return new KtLiterals(new KtLiteral(nameRef.getReferencedName(), null, nameRef));
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

		List<KtLiteral> literals = SyntaxTraverser.psiTraverser(element)
				.expand(it -> !(it instanceof KtStringTemplateExpression
						|| it instanceof KtNameReferenceExpression))
				.filter(KtElement.class)
				.flatMap(it -> {

					if (it instanceof KtNameReferenceExpression referenceExpression) {
						if (StringUtils.hasText(referenceExpression.getReferencedName())) {
							return KtLiterals.property(referenceExpression.getReferencedName(),
									referenceExpression).getLiterals();
						}

						if (StringUtils.hasText(referenceExpression.getName())) {
							return KtLiterals.property(referenceExpression.getName(), referenceExpression)
									.getLiterals();
						}
					}

					if (it instanceof KtStringTemplateExpression kse) {

						KtStringTemplateExpression parentOfType = PsiTreeUtil.getParentOfType(it,
								KtStringTemplateExpression.class);
						if (parentOfType == null) {
							return from(kse).getLiterals();
						}
						return JBIterable.empty();
					}

					return JBIterable.empty();
				}).toList();

		return new KtLiterals(literals);
	}

	private static KtLiterals fromPropertyCandidate(KtElement it) {
		KtLiterals inner = from(it);
		return it instanceof KtReferenceExpression ? inner.asProperty() : inner;
	}

	/**
	 * Extract text from supported Kotlin DSL literal forms.
	 * <p>Used for property keys, dependency coordinates, and simple synthesized
	 * string values.
	 * @throws IllegalArgumentException if the element type is not supported.
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
		return new KtLiterals(new KtLiteral(null, propertyName, element));
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

	private List<KtLiteral> getLiterals() {
		return literals;
	}

	/**
	 * Return whether any fragment resolves to a property reference.
	 *
	 * @return {@literal true} if at least one fragment is property-backed.
	 */
	public boolean hasProperty() {
		return property != null && property.isProperty();
	}

	/**
	 * Return whether this instance contains any renderable content.
	 * <p>VersionProperty references count as content because they participate in
	 * the rendered form returned by {@link #toString()}.
	 *
	 * @return {@literal true} if at least one fragment contributes text or a
	 * property placeholder.
	 */
	public boolean hasText() {

		if (literals.isEmpty()) {
			return false;
		}

		for (KtLiteral literal : literals) {
			if (literal.hasText()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Return the first property reference represented by this instance.
	 * <p>If multiple property fragments are present, the first fragment in
	 * encounter order is returned.
	 *
	 * @return the referenced property name without decoration.
	 * @throws IllegalStateException if {@link #hasProperty()} is {@literal false}.
	 */
	public String getProperty() {
		if (property != null) {
			return property.getProperty();
		}
		throw new IllegalStateException("No property found");
	}

	/**
	 * Return the first concrete text fragment represented by this instance.
	 */
	public String getText() {
		return text;
	}

	/**
	 * Create a {@link KtLiterals} instance that represents a single property.
	 */
	public KtLiterals asProperty() {
		if (property != null && property.isProperty()) {
			return new KtLiterals(property);
		}
		if (literals.isEmpty()) {
			return EMPTY;
		}
		KtLiteral first = literals.getFirst();
		return new KtLiterals(new KtLiteral(null, first.value(), first.expression()));
	}

	/**
	 * Return the number of normalized fragments contributing to this instance.
	 *
	 * @return the fragment count; {@code 0} for an empty instance.
	 */
	public int size() {
		return literals.size();
	}

	/**
	 * Render the collected fragments in encounter order.
	 * <p>Plain literals are concatenated as-is. VersionProperty fragments are
	 * rendered as {@code ${property}}.
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
	 * @param propertyResolver the property resolver to use.
	 * @return the rendered literal content, or the empty string if there are no
	 * literal fragments.
	 */
	public String toString(PropertyResolver propertyResolver) {

		StringBuilder builder = new StringBuilder();

		for (KtLiteral literal : literals) {

			if (literal.isProperty()) {
				String value = propertyResolver.getProperty(literal.getProperty());
				if (value != null) {
					builder.append(value);
				} else {
					builder.append(literal);
				}
			} else {
				builder.append(literal);
			}
		}

		return builder.toString();
	}

	/**
	 * Single normalized literal fragment.
	 * <p>A fragment represents either concrete text or a property placeholder
	 * together with its source PSI element.
	 */
	private record KtLiteral(@Nullable String value, @Nullable String property, KtElement expression) {

		/**
		 * Return whether this fragment represents a property placeholder.
		 *
		 * @return {@literal true} if the fragment is property-backed.
		 */
		public boolean isProperty() {
			return StringUtils.hasText(property);
		}

		/**
		 * Return whether this fragment contributes any renderable content.
		 *
		 * @return {@literal true} if text or a property name is present.
		 */
		public boolean hasText() {
			return StringUtils.hasText(value) || StringUtils.hasText(property);
		}

		/**
		 * Return the property name for this fragment.
		 *
		 * @return the referenced property name.
		 * @throws IllegalArgumentException if this fragment is not property-backed.
		 */
		public String getProperty() {
			Assert.isTrue(StringUtils.hasText(property), "VersionProperty must be set");
			return property;
		}

		/**
		 * Render this fragment as concrete text or a property placeholder.
		 *
		 * @return the rendered fragment.
		 */
		@Override
		public String toString() {
			return property != null ? "${" + property + "}" : value;
		}

	}

}
