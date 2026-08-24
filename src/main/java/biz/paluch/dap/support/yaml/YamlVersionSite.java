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
 * A version-bearing YAML scalar and its enclosing {@code key: value} pair.
 *
 * <p>The site retains the live PSI elements together with snapshots of the
 * scalar's decoded text value and quote style. Ecosystem-specific code remains
 * responsible for interpreting the text as an artifact and version.
 *
 * <p>Use {@link #locate(PsiElement, Predicate)} to find the site from any PSI
 * element produced by a caret position, completion parameter, or annotator
 * visit. The locator walks up to the nearest {@link YAMLScalar}, stops before
 * crossing an enclosing {@link YAMLMapping}, requires the scalar's direct
 * parent to be a {@link YAMLKeyValue}, and applies the caller-supplied
 * predicate to that key-value pair.
 *
 * <p>{@link #replaceRawValue(String, YAMLElementGenerator)} recreates and
 * replaces the enclosing key-value pair. The returned PSI element owns the
 * replacement scalar, and this site must not be reused after replacement.
 *
 * @author Mark Paluch
 * @param scalar the version-bearing scalar.
 * @param keyValue the scalar's direct enclosing key-value pair.
 * @param quoteStyle the rendering style captured from the scalar.
 * @param rawValue the decoded scalar text captured when the site was created.
 */
public record YamlVersionSite(YAMLScalar scalar, YAMLKeyValue keyValue, QuoteStyle quoteStyle, String rawValue) {

	/**
	 * Locate the nearest version-bearing scalar accepted by the key-value
	 * predicate.
	 * @param element the PSI element at the cursor or completion position; may be
	 * {@literal null}.
	 * @param keyMatcher predicate applied to the enclosing {@link YAMLKeyValue}.
	 * @return a site containing the scalar's current decoded value and quote style,
	 * or {@literal null} if the element is absent, no scalar is found before a
	 * mapping boundary, the scalar is not a direct key-value value, or the
	 * predicate rejects its key-value pair.
	 */
	public static @Nullable YamlVersionSite locate(@Nullable PsiElement element, Predicate<YAMLKeyValue> keyMatcher) {

		if (element == null) {
			return null;
		}

		YAMLScalar scalar = element instanceof YAMLScalar s ? s
				: PsiTreeUtil.getParentOfType(element, YAMLScalar.class, false, YAMLMapping.class);

		if (scalar == null || !(scalar.getParent() instanceof YAMLKeyValue keyValue) || !keyMatcher.test(keyValue)) {
			return null;
		}

		return new YamlVersionSite(scalar, keyValue, QuoteStyle.of(scalar), scalar.getTextValue());
	}

	/**
	 * Replace the enclosing key-value pair with one containing the given scalar
	 * value in the detected {@link QuoteStyle}.
	 *
	 * <p>This method must be invoked from a write command. The replacement
	 * invalidates the PSI elements held by this site.
	 * @param newRawValue the decoded scalar value to render.
	 * @param generator the YAML element generator used to build the replacement
	 * key/value pair.
	 * @return the replacement {@link YAMLKeyValue}, which owns the new scalar.
	 */
	public YAMLKeyValue replaceRawValue(String newRawValue, YAMLElementGenerator generator) {

		String wrapped = quoteStyle.wrap(newRawValue);
		YAMLKeyValue replacement = generator.createYamlKeyValue(keyValue.getKeyText(), wrapped);
		return (YAMLKeyValue) keyValue.replace(replacement);
	}

}
