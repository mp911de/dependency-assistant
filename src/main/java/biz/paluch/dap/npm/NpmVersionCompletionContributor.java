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

package biz.paluch.dap.npm;

import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.support.ReleasesCompletionProvider;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.editor.Document;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jspecify.annotations.Nullable;

/**
 * Completion contributor for NPM dependency version strings.
 *
 * <p>{@link Intent#from(CompletionParameters)} inspects the {@link LiteralCaret
 * view of the literal being edited} and resolves a {@link PrefixMatcher matcher
 * the framework should filter on} together with an {@link Adjustment strategy
 * that fixes up the document} once the framework has written the chosen lookup
 * string.
 *
 * @author Mark Paluch
 */
public class NpmVersionCompletionContributor extends CompletionContributor {

	private static final Pattern HEX_REF = Pattern.compile("^[0-9a-f]{4,40}$");

	public NpmVersionCompletionContributor() {
		extend(CompletionType.BASIC,
				PlatformPatterns.psiElement().with(new PatternCondition<>("inNpmDependencyValue") {

					@Override
					public boolean accepts(PsiElement element, ProcessingContext context) {
						return NpmPsiUtils.findDependencyLiteral(element) != null;
					}

				}),
				new ReleasesCompletionProvider(ReleasesCompletionProvider.resolver()) {

					@Override
					protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters,
							CompletionResultSet result) {
						return result.withPrefixMatcher(Intent.from(parameters).matcher());
					}

					@Override
					protected LookupElementBuilder postProcess(CompletionParameters parameters,
							LookupElementBuilder element, PsiElement position, ArtifactRelease option) {
						Intent intent = Intent.from(parameters);
						return element.withInsertHandler((context, lookupElement) -> intent.apply(context, option));
					}

				});
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		return Character.isLetterOrDigit(typeChar) && NpmPsiUtils.findDependencyLiteral(position) != null;
	}

	private static boolean isVersionChar(char c) {
		return Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == '+';
	}

	/**
	 * View of the NPM dependency literal containing the completion caret. Bundles
	 * the unquoted content, the caret offset within that content, and whether the
	 * literal is properly terminated.
	 *
	 * @param content the unquoted literal content.
	 * @param caret the caret offset within {@link #content}, clamped to
	 * {@code [0, content.length()]}.
	 * @param closed {@literal true} if the literal terminates with an unescaped
	 * closing quote; {@literal false} when the user is mid-typing.
	 */
	private record LiteralCaret(String content, int caret, boolean closed) {

		/**
		 * Capture the literal containing the caret described by {@code parameters}.
		 * @return the view, or {@literal null} when the caret is not inside an NPM
		 * dependency value.
		 */
		static @Nullable LiteralCaret from(CompletionParameters parameters) {

			PsiElement position = parameters.getOriginalPosition() != null ? parameters.getOriginalPosition()
					: parameters.getPosition();
			JsonStringLiteral literal = NpmPsiUtils.findDependencyLiteral(position);
			if (literal == null) {
				return null;
			}

			boolean closed = NpmPsiUtils.isClosed(literal);
			String literalText = literal.getText();
			String content = literalText.substring(1, closed ? literalText.length() - 1 : literalText.length());
			int caret = Math.max(0, Math.min(content.length(),
					parameters.getOffset() - literal.getTextRange().getStartOffset() - 1));
			return new LiteralCaret(content, caret, closed);
		}

		/**
		 * Return the run of {@link #isVersionChar(char) version characters} that ends
		 * at the caret. Empty when the character before the caret is not a version
		 * character.
		 */
		String versionPrefix() {
			int start = caret;
			while (start > 0 && isVersionChar(content.charAt(start - 1))) {
				start--;
			}
			return content.substring(start, caret);
		}

	}

	/**
	 * Resolved completion-session decisions: the prefix matcher the framework
	 * should apply, the post-insertion {@link Adjustment}, and whether to restore a
	 * missing closing quote.
	 */
	private record Intent(PrefixMatcher matcher, Adjustment adjustment, boolean addClosingQuote) {

		private static final Intent EMPTY = new Intent(new PlainPrefixMatcher(""), Adjustment.NONE, false);

		/**
		 * Compute the {@code Intent} from the caret position in the literal being
		 * edited. Returns {@link #EMPTY} when the caret is not inside an NPM dependency
		 * value.
		 */
		static Intent from(CompletionParameters parameters) {

			LiteralCaret view = LiteralCaret.from(parameters);
			if (view == null) {
				return EMPTY;
			}
			Intent gitSha = gitShaIntent(view);
			return gitSha != null ? gitSha
					: new Intent(new PlainPrefixMatcher(view.versionPrefix()),
							Adjustment.NONE, !view.closed());
		}

		/**
		 * Match a SHA-styled Git committish: relax the matcher so cached version
		 * strings still surface for hex-only typed text and post-process the inserted
		 * version into a short SHA.
		 */
		private static @Nullable Intent gitShaIntent(LiteralCaret view) {

			int hash = view.content().indexOf('#');
			if (hash < 0 || view.caret() <= hash) {
				return null;
			}
			int committishStart = NpmGitUrlParser.committishStart(view.content(), hash);
			if (view.caret() < committishStart
					|| !HEX_REF.matcher(view.content().substring(committishStart)).matches()) {
				return null;
			}
			String prefix = view.content().substring(committishStart, view.caret());
			return new Intent(new RelaxedPrefixMatcher(prefix), Adjustment.GIT_SHA, !view.closed());
		}

		/**
		 * Adjust the document state after the framework's lookup-string insertion.
		 * @param context the insertion context provided by the framework.
		 * @param option the release the user selected.
		 */
		void apply(InsertionContext context, ArtifactRelease option) {

			Document document = context.getDocument();
			int start = context.getStartOffset();
			int tail = trimTrailingVersionRun(document, context.getTailOffset());

			tail = adjustment.apply(document, start, tail, option);

			if (addClosingQuote) {
				document.insertString(tail, "\"");
			}
			context.getEditor().getCaretModel().moveToOffset(tail);
		}

		private static int trimTrailingVersionRun(Document document, int tailOffset) {

			String text = document.getText();
			int end = tailOffset;
			while (end < text.length() && isVersionChar(text.charAt(end))) {
				end++;
			}
			if (end > tailOffset) {
				document.deleteString(tailOffset, end);
			}
			return tailOffset;
		}

	}

	/**
	 * Strategy that fixes up the document after the framework's lookup-string
	 * insertion and returns the new tail offset.
	 */
	private enum Adjustment {

		/** Leave the framework insertion unchanged. */
		NONE {

			@Override
			int apply(Document document, int startOffset, int tailOffset, ArtifactRelease option) {
				return tailOffset;
			}

		},

		/**
		 * Replace the inserted lookup string with the option's resolved short SHA.
		 * Falls back to no-op when the option carries no SHA metadata.
		 */
		GIT_SHA {

			@Override
			int apply(Document document, int startOffset, int tailOffset, ArtifactRelease option) {

				if (!(option.getVersion() instanceof GitVersion gitVersion)) {
					return tailOffset;
				}
				String shortSha = gitVersion.getShortSha();
				if (shortSha == null) {
					return tailOffset;
				}
				document.replaceString(startOffset, tailOffset, shortSha);
				return startOffset + shortSha.length();
			}

		};

		abstract int apply(Document document, int startOffset, int tailOffset, ArtifactRelease option);

	}

	private static final class RelaxedPrefixMatcher extends PrefixMatcher {

		RelaxedPrefixMatcher(String prefix) {
			super(prefix);
		}

		@Override
		public boolean prefixMatches(String name) {
			return true;
		}

		@Override
		public PrefixMatcher cloneWithPrefix(String prefix) {
			return new RelaxedPrefixMatcher(prefix);
		}

	}

}
