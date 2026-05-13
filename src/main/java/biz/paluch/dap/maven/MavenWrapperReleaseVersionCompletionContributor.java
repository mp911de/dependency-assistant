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

package biz.paluch.dap.maven;

import biz.paluch.dap.assistant.ReleasesCompletionProvider;
import biz.paluch.dap.maven.MavenWrapperParser.WrapperEntry;
import biz.paluch.dap.util.PatternConditions;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jspecify.annotations.Nullable;

/**
 * Completion contributor for Maven Wrapper {@code distributionUrl} and
 * {@code wrapperUrl} version segments.
 *
 * <p>Offers cached release versions when the caret is positioned inside either
 * the path-version or file-version occurrence of a recognized wrapper URL.
 * Selection rewrites both occurrences and removes the matching SHA-256 line in
 * a single write command via {@link UpdateMavenWrapperFile}.
 *
 * @author Mark Paluch
 */
class MavenWrapperReleaseVersionCompletionContributor extends CompletionContributor {

	private static final PatternCondition<PsiElement> INSIDE_WRAPPER_VERSION = PatternConditions.conditional(
			"insideMavenWrapperVersion", MavenWrapperReleaseVersionCompletionContributor::isInsideWrapperVersion);

	private static final PsiElementPattern.Capture<PsiElement> WRAPPER_VERSION = PlatformPatterns.psiElement()
			.with(INSIDE_WRAPPER_VERSION);

	private static final ReleasesCompletionProvider PROVIDER = new ReleasesCompletionProvider() {

		@Override
		protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
				CompletionResultSet result) {

			if (!isCaretInsideVersionRange(parameters)) {
				return;
			}
			super.addCompletions(parameters, context, result);
		}

		@Override
		protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters, CompletionResultSet result) {
			return result.withPrefixMatcher("");
		}

	};

	private static boolean isCaretInsideVersionRange(CompletionParameters parameters) {

		if (!(parameters.getOriginalFile() instanceof PropertiesFile propsFile)) {
			return false;
		}
		return findEntryAtVersionOffset(propsFile, parameters.getOffset()) != null;
	}

	MavenWrapperReleaseVersionCompletionContributor() {
		extend(CompletionType.BASIC, WRAPPER_VERSION, PROVIDER);
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {

		// Properties files tokenize the whole property value as a single leaf,
		// so the position element does not give us the caret offset. The
		// precise range check is enforced in addCompletions; here we accept
		// any version character typed inside a recognized wrapper URL value.
		return ReleasesCompletionProvider.isVersionCharacter(typeChar) && WRAPPER_VERSION.accepts(position);
	}

	private static boolean isInsideWrapperVersion(PsiElement element) {

		PsiFile file = element.getContainingFile();
		if (file == null) {
			return false;
		}
		PsiFile originalFile = file.getOriginalFile();
		if (!(originalFile instanceof PropertiesFile) || !MavenUtils.isWrapperFile(originalFile)) {
			return false;
		}

		PropertyValueImpl literal = element instanceof PropertyValueImpl pv ? pv
				: PsiTreeUtil.getParentOfType(element, PropertyValueImpl.class, false);
		return literal != null && isWrapperUrlProperty(literal);
	}

	private static boolean isWrapperUrlProperty(PropertyValueImpl literal) {

		PsiElement parent = literal.getParent();
		if (!(parent instanceof IProperty property)) {
			return false;
		}

		return MavenWrapperParser.isWrapperProperty(property.getUnescapedKey());
	}

	/**
	 * Return the wrapper entry whose path-version or file-version range contains
	 * the given caret offset, or {@literal null} if the offset falls outside both
	 * version sub-ranges of every parsed entry. The end offset of each range is
	 * treated as inside so that completion triggers at the trailing boundary of the
	 * version token.
	 */
	private static @Nullable WrapperEntry findEntryAtVersionOffset(PropertiesFile propsFile, int offset) {

		for (WrapperEntry entry : new MavenWrapperParser().getEntries(propsFile)) {
			if (containsCaret(entry.pathVersionRange(), offset) || containsCaret(entry.fileVersionRange(), offset)) {
				return entry;
			}
		}
		return null;
	}

	private static boolean containsCaret(TextRange range, int offset) {
		return offset >= range.getStartOffset() && offset <= range.getEndOffset();
	}

}
