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
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.artifact.VersioningScheme;
import org.jspecify.annotations.Nullable;

/**
 * Curated release proposals over the {@link DevelopmentLines} of a release
 * history: the rows a first completion invocation shows.
 *
 * <p>Selection composes three policies: the <em>corridor</em> anchored on a
 * current version drops lines older than the current line and always keeps the
 * current line and the current release; a {@link VersionStem} adds the newest
 * matching version per steered line, the matching members when the stem pins a
 * single line, or the matching pre-releases under
 * {@linkplain VersionStem#isSuffixIntent() suffix intent}; without either, a
 * window over the newest lines applies. Stable lines surface their newest
 * stable version up to a line budget that also caps lines per major version, so
 * histories with many minors in one major still surface the recent majors.
 * Pre-release-only lines surface their newest pre-release up to a smaller cap.
 *
 * <p>Selections degrade rather than vanish: an opaque current version (branch,
 * bare SHA) and a current version ahead of the cached history both fall back to
 * the window, and a single-line history lists its members. Proposals keep the
 * history's canonical order so the curated invocation and the full-history
 * invocation list rows consistently.
 *
 * @author Mark Paluch
 * @see DevelopmentLines
 * @see VersionStem
 */
class ReleaseProposals implements Iterable<Release> {

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
	 * Select the proposals for the given release history.
	 *
	 * @param history the release history in canonical newest-first order.
	 * @param currentVersion the currently declared version anchoring the corridor,
	 * or {@literal null} when no current version is available.
	 * @param stem the typed-prefix stem steering line selection, or {@literal null}
	 * without a usable prefix.
	 * @return the proposals in the history's canonical order; never
	 * {@literal null}.
	 */
	public static ReleaseProposals select(Releases history, @Nullable ArtifactVersion currentVersion,
			@Nullable VersionStem stem) {

		Selection selection = new Selection(DevelopmentLines.of(history), corridorAnchor(currentVersion));
		return new ReleaseProposals(history, inHistoryOrder(history, selection.rows(stem)));
	}

	/**
	 * Return proposals that additionally include the given release.
	 *
	 * <p>The result keeps the history's canonical order. A release outside the
	 * underlying history is ignored: proposals only contain history releases.
	 *
	 * @param release the release to include; must not be {@literal null}.
	 * @return these proposals when the release is already included, extended
	 * proposals otherwise.
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
	 * Return the proposed releases as a list, in the history's canonical order.
	 *
	 * @return the proposed releases; never {@literal null}.
	 */
	public List<Release> getReleases() {
		return releases;
	}

	/**
	 * Return the number of proposed releases.
	 */
	public int size() {
		return releases.size();
	}

	/**
	 * Return whether no releases are proposed.
	 *
	 * @return {@literal true} if there are no proposals; {@literal false}
	 * otherwise.
	 */
	public boolean isEmpty() {
		return releases.isEmpty();
	}

	@Override
	public Iterator<Release> iterator() {
		return releases.iterator();
	}

	/**
	 * Return the proposed releases as a stream, in the history's canonical order.
	 */
	public Stream<Release> stream() {
		return releases.stream();
	}

	@Override
	public String toString() {
		return "ReleaseProposals" + releases;
	}

	/**
	 * Return the corridor anchor for the given current version: the unwrapped form,
	 * or {@literal null} for opaque refs (branches, bare SHAs) that cannot anchor a
	 * corridor.
	 */
	private static @Nullable ArtifactVersion corridorAnchor(@Nullable ArtifactVersion currentVersion) {

		if (currentVersion == null) {
			return null;
		}

		ArtifactVersion unwrapped = Versioned.of(currentVersion).unwrap();
		return unwrapped.scheme() == VersioningScheme.OPAQUE ? null : unwrapped;
	}

	/**
	 * Map the selected versions onto their releases, preserving the canonical
	 * history order so the curated invocation and the full-history invocation list
	 * rows consistently.
	 */
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

	/**
	 * One selection pass over the development lines of a history, anchored on the
	 * current version when one exists.
	 */
	private static class Selection {

		private final DevelopmentLines lines;

		private final @Nullable ArtifactVersion anchor;

		Selection(DevelopmentLines lines, @Nullable ArtifactVersion anchor) {
			this.lines = lines;
			this.anchor = anchor;
		}

		/**
		 * Return the selected versions, steered by the given stem when one exists.
		 */
		Set<ArtifactVersion> rows(@Nullable VersionStem stem) {

			Set<ArtifactVersion> rows = new LinkedHashSet<>();
			if (stem != null) {
				rows.addAll(steeredRows(stem));
			}

			// A single-line history has nothing to curate; list its members.
			if (lines.size() == 1) {
				rows.addAll(lines.getLines().getFirst().stream().limit(MAX_LINES).toList());
			}

			List<ArtifactVersion> anchored = lineRows(anchor);
			if (anchored.isEmpty() && anchor != null) {
				// The current version is ahead of the cached history; fall back to
				// the window rather than an empty selection.
				anchored = lineRows(null);
			}
			rows.addAll(anchored);

			return rows;
		}

		/**
		 * Return the corridor rows when {@code anchor} is present, the window rows
		 * otherwise. Both spend the same {@link LineBudget}; the current line bypasses
		 * it.
		 */
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

		/**
		 * Return the rows the stem steers to: the matching pre-releases under suffix
		 * intent, the matching members when the stem pins a single line (the user is
		 * drilling into that line), and the newest matching version per line otherwise.
		 */
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
	 * Admission budget for one pass over the development lines: at most
	 * {@link #MAX_LINES} stable lines overall, at most {@link #MAX_LINES_PER_MAJOR}
	 * stable lines per major version so long histories still surface the recent
	 * majors, and at most {@link #MAX_PREVIEWS} pre-release-only lines.
	 */
	private static class LineBudget {

		private int stableLines;

		private int previewLines;

		private int majorLines;

		private @Nullable ArtifactVersion major;

		boolean admitStableLine(ArtifactVersion version) {

			ArtifactVersion unwrapped = Versioned.of(version).unwrap();
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
