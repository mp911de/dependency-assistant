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

package biz.paluch.dap.assistant.completion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersioningScheme;
import biz.paluch.dap.util.Sequence;
import org.jspecify.annotations.Nullable;

/**
 * Curated releases shown on the first completion invocation.
 * <p>Selection favors the current development line and recent stable lines. A
 * typed {@link VersionStem} adds matching releases. Unusable current versions
 * fall back to recent lines. Results retain history order.
 *
 * @author Mark Paluch
 */
class ReleaseProposals implements Sequence<Release> {

	private static final int MAX_LINES = 8;

	private static final int MAX_LINES_PER_MAJOR = 3;

	private static final int MAX_PREVIEWS = 2;

	private final Releases history;

	private final List<Release> releases;

	private ReleaseProposals(Releases history, List<Release> releases) {
		this.history = history;
		this.releases = releases;
	}

	/**
	 * Select completion proposals from newest-first history.
	 * @param currentVersion the current version, or {@literal null} if unknown.
	 * @param stem the typed prefix, or {@literal null} if none.
	 */
	public static ReleaseProposals select(Releases history, @Nullable ArtifactVersion currentVersion,
			@Nullable VersionStem stem) {

		Selection selection = new Selection(DevelopmentLines.of(history), corridorAnchor(currentVersion));
		return new ReleaseProposals(history, inHistoryOrder(history, selection.rows(stem)));
	}

	/**
	 * Include the release if it belongs to the underlying history.
	 * <p>Preserves history order and returns this instance if already included.
	 */
	public ReleaseProposals with(Release release) {

		if (releases.contains(release)) {
			return this;
		}

		Set<Release> chosen = new HashSet<>(releases);
		chosen.add(release);
		return new ReleaseProposals(history, history.stream().filter(chosen::contains).toList());
	}

	/**
	 * Return the unmodifiable proposals in history order.
	 */
	public List<Release> getReleases() {
		return releases;
	}

	public int size() {
		return releases.size();
	}

	@Override
	public boolean isEmpty() {
		return releases.isEmpty();
	}

	@Override
	public Iterator<Release> iterator() {
		return releases.iterator();
	}

	@Override
	public Stream<Release> stream() {
		return releases.stream();
	}

	@Override
	public String toString() {
		return "ReleaseProposals" + releases;
	}

	private static @Nullable ArtifactVersion corridorAnchor(@Nullable ArtifactVersion currentVersion) {

		if (currentVersion == null) {
			return null;
		}

		ArtifactVersion unwrapped = currentVersion.unwrap();
		return unwrapped.scheme() == VersioningScheme.OPAQUE ? null : unwrapped;
	}

	private static List<Release> inHistoryOrder(Releases history, Set<ArtifactVersion> rows) {

		Set<Release> chosen = new HashSet<>();
		for (ArtifactVersion version : rows) {

			Release release = history.getRelease(version);
			if (release != null) {
				chosen.add(release);
			}
		}

		return history.stream().filter(chosen::contains).toList();
	}

	private static class Selection {

		private final DevelopmentLines lines;

		private final @Nullable ArtifactVersion anchor;

		Selection(DevelopmentLines lines, @Nullable ArtifactVersion anchor) {
			this.lines = lines;
			this.anchor = anchor;
		}

		Set<ArtifactVersion> rows(@Nullable VersionStem stem) {

			Set<ArtifactVersion> rows = new LinkedHashSet<>();
			if (stem != null) {
				rows.addAll(steeredRows(stem));
			}

			// A single-line history needs no selection across lines.
			if (lines.size() == 1) {
				rows.addAll(lines.getLines().getFirst().stream().limit(MAX_LINES).toList());
			}

			List<ArtifactVersion> anchored = lineRows(anchor);
			if (anchored.isEmpty() && anchor != null) {
				// Keep recent releases visible when the current version is ahead of history.
				anchored = lineRows(null);
			}
			rows.addAll(anchored);

			return rows;
		}

		private List<ArtifactVersion> lineRows(@Nullable ArtifactVersion anchor) {

			List<ArtifactVersion> rows = new ArrayList<>();
			LineBudget budget = new LineBudget();

			for (DevelopmentLine line : lines) {

				boolean currentLine = anchor != null && line.contains(anchor);
				if (anchor != null && !currentLine && line.isOlderThan(anchor)) {
					continue;
				}

				ArtifactVersion stable = line.getLatestStable();
				if (stable == null) {

					if (budget.admitPreviewLine()) {
						rows.add(line.getLatest());
					}
					continue;
				}

				if (budget.admitStableLine(stable) || currentLine) {
					rows.add(stable);
				}

				if (currentLine) {
					rows.add(anchor);
				}
			}
			return rows;
		}

		private List<ArtifactVersion> steeredRows(VersionStem stem) {

			List<List<ArtifactVersion>> matches = new ArrayList<>();
			for (DevelopmentLine line : lines) {

				List<ArtifactVersion> matching = line.stream().filter(stem::matches).toList();
				if (!matching.isEmpty()) {
					matches.add(matching);
				}
			}

			if (matches.isEmpty()) {
				return List.of();
			}

			if (stem.isSuffixIntent()) {
				return matches.stream().flatMap(List::stream).filter(ArtifactVersion::isPreview).toList();
			}

			if (matches.size() == 1) {
				return matches.getFirst().stream().limit(MAX_LINES).toList();
			}

			return matches.stream()
					.map(matching -> matching.stream().filter(version -> !version.isPreview()).findFirst()
							.orElse(matching.getFirst()))
					.toList();
		}

	}

	/**
	 * Limits lines per major so older majors remain visible in long histories.
	 */
	private static class LineBudget {

		private int stableLines;

		private int previewLines;

		private int majorLines;

		private @Nullable ArtifactVersion major;

		boolean admitStableLine(ArtifactVersion version) {

			ArtifactVersion unwrapped = version.unwrap();
			if (major == null || !major.hasSameMajor(unwrapped)) {
				major = unwrapped;
				majorLines = 0;
			}

			if (stableLines >= MAX_LINES || majorLines >= MAX_LINES_PER_MAJOR) {
				return false;
			}

			stableLines++;
			majorLines++;
			return true;
		}

		boolean admitPreviewLine() {

			if (previewLines >= MAX_PREVIEWS) {
				return false;
			}

			previewLines++;
			return true;
		}

	}

}
