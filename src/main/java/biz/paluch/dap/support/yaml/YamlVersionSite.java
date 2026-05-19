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

package biz.paluch.dap.support.yaml;

import java.util.function.Predicate;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * A YAML scalar value that holds a versioned reference and the surrounding
 * {@code key: value} pair it lives in.
 *
 * <p>Use {@link #locate(PsiElement, Predicate)} to find the site from any PSI
 * element produced by a caret position, completion parameter, or annotator
 * visit. The locator walks up to the nearest {@link YAMLScalar}, stops at any
 * enclosing {@link YAMLMapping}, requires the scalar's parent to be a
 * {@link YAMLKeyValue}, and applies the caller-supplied predicate to the key.
 *
 * <p>Use {@link #replaceRawValue(String, YAMLElementGenerator)} from inside a
 * write command to swap the scalar value while preserving the original
 * {@link QuoteStyle}.
 *
 * @author Mark Paluch
 */
public record YamlVersionSite(YAMLScalar scalar, YAMLKeyValue keyValue, QuoteStyle quoteStyle, String rawValue) {

	/**
	 * Walk up from the given element to the nearest version site whose key
	 * matches the predicate.
	 * @param element the PSI element at the cursor or completion position; may be
	 * {@literal null}.
	 * @param keyMatcher predicate applied to the enclosing {@link YAMLKeyValue};
	 * must not be {@literal null}.
	 * @return the site, or {@literal null} if no matching site exists.
	 */
	public static @Nullable YamlVersionSite locate(@Nullable PsiElement element, Predicate<YAMLKeyValue> keyMatcher) {

		if (element == null) {
			return null;
		}

		YAMLScalar scalar = element instanceof YAMLScalar s ? s
				: PsiTreeUtil.getParentOfType(element, YAMLScalar.class, false, YAMLMapping.class);
		if (scalar == null) {
			return null;
		}

		if (!(scalar.getParent() instanceof YAMLKeyValue keyValue)) {
			return null;
		}

		if (!keyMatcher.test(keyValue)) {
			return null;
		}

		return new YamlVersionSite(scalar, keyValue, QuoteStyle.of(scalar), scalar.getTextValue());
	}

	/**
	 * Replace the scalar value with the given raw text while preserving the
	 * detected {@link QuoteStyle}. Must be invoked from a write command.
	 * @param newRawValue the unwrapped replacement value; must not be {@literal null}.
	 * @param generator the YAML element generator used to build the replacement
	 * key/value pair; must not be {@literal null}.
	 * @return the replacement {@link YAMLKeyValue}.
	 */
	public YAMLKeyValue replaceRawValue(String newRawValue, YAMLElementGenerator generator) {

		String wrapped = quoteStyle.wrap(newRawValue);
		YAMLKeyValue replacement = generator.createYamlKeyValue(keyValue.getKeyText(), wrapped);
		return (YAMLKeyValue) keyValue.replace(replacement);
	}

}
