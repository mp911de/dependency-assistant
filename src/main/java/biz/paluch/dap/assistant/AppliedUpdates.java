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

package biz.paluch.dap.assistant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import biz.paluch.dap.DependencyPresentation;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.Sequence;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Accumulator for the updates a bulk apply wrote, retaining enough
 * per-occurrence detail to summarize them and to reverse-apply only the flagged
 * entries.
 *
 * <p>The flagged entries keep their originating {@link DependencyUpdate} and
 * target file so the undo action ({@link #getReverse()} applied to
 * {@link #getReverseFiles()}) can rewrite each back to its {@code from} version
 * through the same per-file build-file update path.
 *
 * @author Mark Paluch
 * @see AppliedDependencyUpdate
 */
public class AppliedUpdates implements Sequence<AppliedDependencyUpdate> {

	private final Set<AppliedDependencyUpdate> applied = new TreeSet<>();

	private final List<Reversible> outOfBounds = new ArrayList<>();

	/**
	 * Record an applied update with its governing rule, flagging it for undo when
	 * the applied version is out of bounds.
	 */
	public void record(VirtualFile file, DependencyUpdate update, DependencyRule rule,
			DependencyPresentation presentation) {

		AppliedDependencyUpdate summary = AppliedDependencyUpdate.from(update, rule, presentation);
		applied.add(summary);
		if (summary.isFlagged()) {
			outOfBounds.add(new Reversible(file, update));
		}
	}

	/**
	 * Record an applied update with its governing rule, flagging it for undo when
	 * the applied version is out of bounds.
	 */
	public void record(Iterable<VirtualFile> files, List<DependencyUpdate> updates, String displayName) {
		for (DependencyUpdate update : updates) {
			record(files, update, displayName);
		}
	}

	/**
	 * Record an applied update with its governing rule, flagging it for undo when
	 * the applied version is out of bounds.
	 */
	public void record(Iterable<VirtualFile> files, DependencyUpdate update, String displayName) {
		AppliedDependencyUpdate summary = AppliedDependencyUpdate.from(update, displayName);
		applied.add(summary);
		if (summary.isFlagged()) {
			for (VirtualFile file : files) {
				outOfBounds.add(new Reversible(file, update));
			}
		}
	}

	/**
	 * Return the applied updates ordered by display label.
	 */
	public Set<AppliedDependencyUpdate> applied() {
		return applied;
	}

	public List<DependencyUpdate> getReverse() {
		return outOfBounds.stream().map(Reversible::reverse).toList();
	}

	public List<VirtualFile> getReverseFiles() {
		return outOfBounds.stream().map(Reversible::file).toList();
	}

	public String renderOutOfBounds(String heading,
			Collection<AppliedDependencyUpdate> entries) {

		HtmlChunk.Element ul = HtmlChunk.ul();

		for (AppliedDependencyUpdate update : entries) {
			ul = ul.children(HtmlChunk.li()
					.addText(MessageBundle.message("notification.dependencies-updates.out-of-bounds.entry",
							update.displayName(), update.getTargetVersion())));
		}

		return heading + ul;
	}

	public String renderApplied() {

		HtmlChunk.Element ul = HtmlChunk.ul();
		for (AppliedDependencyUpdate update : this) {

			HtmlChunk li;
			if (update.getTargetVersion().isNewer(update.getFromVersion())) {
				li = HtmlChunk.li().addText(MessageBundle.message("notification.dependencies-updates.upgrade",
						update.displayName(), update.getTargetVersion()));
			} else if (update.getFromVersion().isNewer(update.getTargetVersion())) {
				li = HtmlChunk.li().addText(MessageBundle.message("notification.dependencies-updates.downgrade",
						update.displayName(), update.getTargetVersion()));
			} else {
				li = HtmlChunk.li().addText(MessageBundle.message("notification.dependencies-updates.update",
						update.displayName(), update.getTargetVersion()));
			}
			ul = ul.children(li);
		}

		return ul.toString();
	}

	@Override
	public Iterator<AppliedDependencyUpdate> iterator() {
		return applied.iterator();
	}

	@Override
	public boolean isEmpty() {
		return applied.isEmpty();
	}

	public int size() {
		return applied.size();
	}

	@Override
	public List<AppliedDependencyUpdate> toList() {
		return List.copyOf(applied);
	}

	@Override
	public String toString() {
		return stream().map(it -> "%s %s".formatted(it.displayName(), it.getTargetVersion()))
				.collect(Collectors.joining(", "));
	}

	record Reversible(VirtualFile file, DependencyUpdate update) {

		DependencyUpdate reverse() {
			return new DependencyUpdate(update.artifactId(), update.to(), update.from().getVersion(),
					update.declarationSources(), update.versionSources());
		}

	}

}
