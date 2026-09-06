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
 * Live YAML version site with captured decoded text and quote style.
 * <p>The integration interprets the text as an artifact and version. Do not
 * reuse the site after replacement invalidates its PSI elements.
 *
 * @author Mark Paluch
 */
public record YamlVersionSite(YAMLScalar scalar, YAMLKeyValue keyValue, QuoteStyle quoteStyle, String rawValue) {

	/**
	 * Locate the nearest scalar accepted by the key-value predicate.
	 * <p>Search stops at a mapping boundary. The scalar must directly belong to a
	 * key-value pair. Absent or rejected sites return {@literal null}.
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
	 * Replace the key-value pair using the captured quote style.
	 * <p>The caller must hold a write command. This site becomes invalid.
	 * @param newRawValue decoded scalar content.
	 * @return the new key-value pair owning the replacement scalar.
	 */
	public YAMLKeyValue replaceRawValue(String newRawValue, YAMLElementGenerator generator) {

		String wrapped = quoteStyle.wrap(newRawValue);
		YAMLKeyValue replacement = generator.createYamlKeyValue(keyValue.getKeyText(), wrapped);
		return (YAMLKeyValue) keyValue.replace(replacement);
	}

}
