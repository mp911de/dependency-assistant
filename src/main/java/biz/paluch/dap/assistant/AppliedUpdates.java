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

import biz.paluch.dap.assistant.presentation.DependencyPresentation;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.DependencyUpdates;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.Sequence;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Mutable accounting for dependency updates that changed build files.
 *
 * <p>Callers record an update only after verifying a file-text change. Summary
 * entries are sorted and deduplicated by display label. Flagged entries also
 * retain their applied update and the supplied reverse-application files.
 * Consume {@link #getReverse()} together with {@link #getReverseFiles()} to
 * rewrite those entries to their source versions through the normal per-file
 * update path.
 *
 * @author Mark Paluch
 * @see AppliedDependencyUpdate
 */
public class AppliedUpdates implements Sequence<AppliedDependencyUpdate> {

	private final Set<AppliedDependencyUpdate> applied = new TreeSet<>();

	private final List<Reversible> outOfBounds = new ArrayList<>();

	/**
	 * Record an update that changed the given file and classify it against its
	 * governing rule.
	 *
	 * @param file the changed file eligible for reverse application.
	 * @param update the applied dependency update.
	 * @param rule the rule governing the dependency.
	 * @param presentation the source of the user-facing dependency label.
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
	 * Record several applied updates under one display label.
	 *
	 * <p>No governing rule is available through this overload. Major version
	 * crossings are therefore the only flagged entries.
	 *
	 * @param files the files eligible for reverse application.
	 * @param updates the applied dependency updates.
	 * @param displayName the user-facing dependency label shared by the updates.
	 */
	public void record(Iterable<VirtualFile> files, List<DependencyUpdate> updates, String displayName) {
		for (DependencyUpdate update : updates) {
			record(files, update, displayName);
		}
	}

	/**
	 * Record an applied update under the given display label.
	 *
	 * <p>No governing rule is available through this overload. A major version
	 * crossing is therefore the only flagged outcome.
	 *
	 * @param files the files eligible for reverse application.
	 * @param update the applied dependency update.
	 * @param displayName the user-facing dependency label.
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
	 *
	 * <p>The returned set is the live, mutable summary set.
	 *
	 * @return the applied-update summaries.
	 */
	public Set<AppliedDependencyUpdate> applied() {
		return applied;
	}

	/**
	 * Return reverse updates for the flagged entries.
	 *
	 * <p>The result is intended for the files from {@link #getReverseFiles()}.
	 *
	 * @return a new dependency-update sequence that exchanges each flagged entry's
	 * source and target versions.
	 */
	public DependencyUpdates getReverse() {
		return new DependencyUpdates(outOfBounds.stream().map(Reversible::reverse).toList());
	}

	/**
	 * Return the files supplied for reverse application of flagged entries.
	 *
	 * @return a new scope corresponding to {@link #getReverse()}.
	 */
	public FileScope getReverseFiles() {
		return FileScope.of(outOfBounds.stream().map(Reversible::file).toList());
	}

	/**
	 * Render flagged summaries as an HTML heading followed by a list.
	 *
	 * @param heading the trusted HTML heading to prepend without escaping.
	 * @param entries the summaries to render. Entry labels and versions are escaped
	 * as text.
	 * @return the notification HTML fragment.
	 */
	public String renderOutOfBounds(String heading,
			Collection<AppliedDependencyUpdate> entries) {

		HtmlChunk.Element ul = HtmlChunk.ul();

		for (AppliedDependencyUpdate update : entries) {
			ul = ul.children(HtmlChunk.li()
					.addText(MessageBundle.message("notification.out-of-bounds.entry",
							update.displayName(), update.getTargetVersion())));
		}

		return heading + ul;
	}

	/**
	 * Render the summary entries as an HTML list of upgrades, downgrades, or
	 * same-order updates.
	 *
	 * @return the notification HTML fragment.
	 */
	public String renderApplied() {

		HtmlChunk.Element ul = HtmlChunk.ul();
		for (AppliedDependencyUpdate update : this) {

			HtmlChunk li;
			if (update.getTargetVersion().isNewer(update.getFromVersion())) {
				li = HtmlChunk.li().addText(MessageBundle.message("notification.upgrade",
						update.displayName(), update.getTargetVersion()));
			} else if (update.getFromVersion().isNewer(update.getTargetVersion())) {
				li = HtmlChunk.li().addText(MessageBundle.message("notification.downgrade",
						update.displayName(), update.getTargetVersion()));
			} else {
				li = HtmlChunk.li().addText(MessageBundle.message("notification.update",
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
