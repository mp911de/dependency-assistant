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
import biz.paluch.dap.util.Sequence;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Mutable accounting for updates that changed build files.
 * <p>Record an update only after verifying a file-text change. Summaries are
 * sorted and deduplicated by display label. Flagged updates can be reversed
 * using {@link #getReverse()} with {@link #getReverseFiles()}.
 *
 * @author Mark Paluch
 */
public class AppliedUpdates implements Sequence<AppliedUpdate> {

	private final Set<AppliedUpdate> applied = new TreeSet<>();

	private final List<Reversible> outOfBounds = new ArrayList<>();

	/**
	 * Record an applied update and classify it against its governing rule.
	 * @param file the changed file eligible for reverse application.
	 */
	public void record(VirtualFile file, DependencyUpdate update, DependencyRule rule,
			DependencyPresentation presentation) {

		AppliedUpdate summary = AppliedUpdate.from(update, rule, presentation);
		applied.add(summary);
		if (summary.isFlagged()) {
			outOfBounds.add(new Reversible(file, update));
		}
	}

	/**
	 * Record several updates with the same label and reverse-application files.
	 * @see #record(Iterable, DependencyUpdate, String)
	 */
	public void record(Iterable<VirtualFile> files, List<DependencyUpdate> updates, String displayName) {
		for (DependencyUpdate update : updates) {
			record(files, update, displayName);
		}
	}

	/**
	 * Record an applied update without a governing rule.
	 * <p>Only major version crossings are flagged.
	 * @param files the files eligible for reverse application.
	 */
	public void record(Iterable<VirtualFile> files, DependencyUpdate update, String displayName) {
		AppliedUpdate summary = AppliedUpdate.from(update, displayName);
		applied.add(summary);
		if (summary.isFlagged()) {
			for (VirtualFile file : files) {
				outOfBounds.add(new Reversible(file, update));
			}
		}
	}

	/**
	 * Return the live, mutable summary set, ordered by display label.
	 */
	public Set<AppliedUpdate> applied() {
		return applied;
	}

	public AppliedUpdate first() {
		return applied.iterator().next();
	}

	/**
	 * Return updates that restore the source versions of flagged entries.
	 * <p>Apply them to the files from {@link #getReverseFiles()}.
	 */
	public DependencyUpdates getReverse() {
		return new DependencyUpdates(outOfBounds.stream().map(Reversible::reverse).toList());
	}

	/**
	 * Return the files to which {@link #getReverse()} applies.
	 */
	public FileScope getReverseFiles() {
		return FileScope.of(outOfBounds.stream().map(Reversible::file).toList());
	}

	@Override
	public Iterator<AppliedUpdate> iterator() {
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
	public List<AppliedUpdate> toList() {
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
