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

package biz.paluch.dap.artifact;

import java.util.List;

import com.intellij.openapi.util.TextRange;

/**
 * Translates a pre-edit caret offset into the offset just behind the version a
 * single-update writer rewrote.
 *
 * <p>An {@code Update*File} writer that rewrites a version is the only code
 * that knows exactly which substring(s) it wrote, so it returns a remap from
 * {@code applyUpdate(PsiElement, DependencyUpdate)} that carries the pre-edit
 * occurrence ranges and their post-edit counterparts in document order. The
 * quickfix, intention, ModCommand, and completion write paths all consume the
 * same remap, which replaces every hand-rolled caret offset computation.
 *
 * <p>The caret lands immediately behind the version digits, not at the end of
 * an enclosing URL or literal. When a value carries several version occurrences
 * (a Gradle or Maven wrapper URL rewrites every spot), {@link #translate(int)}
 * picks the occurrence under the current caret and falls back to the first. The
 * writer decides which written ranges are caret-eligible.
 *
 * <p>This type depends only on {@link TextRange}; it carries no {@code Editor}
 * or {@code ModPsiUpdater} coupling, so the caret-selection policy stays free
 * of platform UI types and the call sites own the actual caret move.
 *
 * @author Mark Paluch
 */
public class VersionCaretRemap {

	private static final VersionCaretRemap NONE = new VersionCaretRemap(List.of(), List.of());

	private final List<TextRange> oldRanges;

	private final List<TextRange> newRanges;

	private VersionCaretRemap(List<TextRange> oldRanges, List<TextRange> newRanges) {
		this.oldRanges = oldRanges;
		this.newRanges = newRanges;
	}

	/**
	 * Create a remap from the caret-eligible occurrence ranges a writer rewrote.
	 *
	 * <p>The two lists are paired positionally and must be in document order: the
	 * <em>n</em>-th old range and <em>n</em>-th new range describe the same version
	 * occurrence before and after the edit. Pass empty lists or use {@link #none()}
	 * when no occurrence is caret-eligible.
	 * @param oldRanges the pre-edit occurrence ranges, in document order; must not
	 * be {@literal null} and is retained.
	 * @param newRanges the post-edit occurrence ranges, in document order, paired
	 * with {@code oldRanges}; must not be {@literal null} and is retained.
	 * @return the remap over the given ranges.
	 */
	public static VersionCaretRemap of(List<TextRange> oldRanges, List<TextRange> newRanges) {
		return new VersionCaretRemap(oldRanges, newRanges);
	}

	/**
	 * Return an empty remap that never moves the caret.
	 * @return the shared empty remap.
	 */
	public static VersionCaretRemap none() {
		return NONE;
	}

	/**
	 * Return whether this remap can move the caret behind a written version.
	 * @return {@literal true} if at least one new range exists; {@literal false}
	 * otherwise.
	 */
	public boolean canTranslate() {
		return !newRanges.isEmpty();
	}

	/**
	 * Translate a pre-edit caret offset into the offset just behind the version the
	 * writer rewrote.
	 *
	 * <p>Picks the occurrence whose pre-edit range contains or is adjacent to
	 * {@code currentCaret}, falling back to the first occurrence, then returns that
	 * occurrence's post-edit end offset. Call only when {@link #canTranslate()}
	 * returns {@literal true}.
	 * @param currentCaret the pre-edit caret offset, captured before the writer
	 * mutated the document.
	 * @return the post-edit offset just behind the chosen version occurrence.
	 */
	public int translate(int currentCaret) {

		int occurrence = 0;
		for (int i = 0; i < oldRanges.size(); i++) {
			if (oldRanges.get(i).containsOffset(currentCaret)) {
				occurrence = i;
				break;
			}
		}

		return newRanges.get(occurrence).getEndOffset();
	}

}
