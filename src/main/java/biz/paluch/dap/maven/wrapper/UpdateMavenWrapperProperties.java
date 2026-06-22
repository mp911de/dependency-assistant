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

package biz.paluch.dap.maven.wrapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.util.Properties;
import biz.paluch.dap.util.PropertyUtils;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyKeyValueFormat;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Applies dependency updates to {@code .mvn/wrapper/maven-wrapper.properties}.
 *
 * @author Mark Paluch
 */
class UpdateMavenWrapperProperties {

	/**
	 * Apply a single update at the given wrapper version literal.
	 * @param versionLiteral the {@link PropertyValueImpl} that owns the URL value;
	 * must not be {@literal null}.
	 * @param update the update to apply.
	 */
	public static void applyUpdate(PsiElement versionLiteral, DependencyUpdate update) {

		PropertyImpl property = PropertyUtils.findProperty(versionLiteral);
		if (property == null) {
			return;
		}

		Set<String> toCommentOut = new HashSet<>();
		WrapperEntry entry = MavenWrapperParser.parse(property);
		if (entry != null) {
			applyUpdate(property, entry, update, toCommentOut);
		}

		postProcess(property.getContainingFile(), toCommentOut);
	}

	/**
	 * Apply updates to the given wrapper PSI file.
	 * @param psiFile the wrapper PSI file.
	 * @param updates the updates to apply.
	 */
	public static void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {

		if (!(psiFile instanceof PropertiesFile properties)) {
			return;
		}

		Set<String> toCommentOut = new HashSet<>();
		Properties.from(properties).filterMap(MavenWrapperParser::parse).toList().forEach(it -> {
			for (DependencyUpdate update : updates) {
				applyUpdate(it.propertyLiteral(), it, update, toCommentOut);
			}
		});

		postProcess(psiFile, new HashSet<>(toCommentOut));
	}

	private static void applyUpdate(PropertyImpl property, WrapperEntry entry, DependencyUpdate update,
			Collection<String> toCommentOut) {

		if (!entry.hasArtifactId(update.coordinate())) {
			return;
		}

		List<TextRange> ranges = MavenWrapperUtils.getVersionRanges(property);
		if (ranges.isEmpty()) {
			return;
		}

		ASTNode valueNode = property.getValueNode();
		if (valueNode == null) {
			return;
		}

		toCommentOut.add(entry.property().shaKey());

		int propertyStart = property.getTextRange().getStartOffset();
		int valueStart = valueNode.getTextRange().getStartOffset() - propertyStart;
		String updatedText = property.getText();

		for (int i = ranges.size() - 1; i >= 0; i--) {
			TextRange rangeInProperty = ranges.get(i).shiftLeft(propertyStart);
			updatedText = rangeInProperty.replace(updatedText, update.version().toString());
		}

		property.setValue(updatedText.substring(valueStart), PropertyKeyValueFormat.FILE);
	}

	private static void postProcess(PsiFile psiFile, Collection<String> toCommentOut) {

		if (toCommentOut.isEmpty()) {
			return;
		}

		for (PropertyImpl property : PsiTreeUtil.findChildrenOfType(psiFile, PropertyImpl.class)) {
			if (toCommentOut.contains(property.getUnescapedKey())) {
				PropertyUtils.commentOut(property);
			}
		}
	}

}
