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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

/**
 * Value object encapsulating releases for one artifact.
 *
 * <p>Instances are immutable; {@link #withRelease(Release)} returns a new
 * instance including the additional release.
 *
 * <p>An artifact can change how it names versions over time. For example, a
 * project can move from release train names to calendar versions or later move
 * back. Individual versions are comparable only within their
 * {@link VersioningScheme}; this type gives callers the artifact-level view
 * needed by {@link UpgradeStrategy upgrade strategies} and completion lists.
 *
 * <p>The scheme with the most recent dated release is the successor scheme. Its
 * releases form past releases and appear before releases from superseded
 * schemes. Release dates choose scheme precedence; they do not interleave
 * releases from different schemes. Within each scheme, releases follow the
 * scheme's version order, newest first.
 *
 * @author Mark Paluch
 * @see Release
 * @see VersioningScheme
 * @see UpgradeStrategy
 * @see ReleaseResolver
 */
public class Releases implements Iterable<Release> {

	private static final Releases EMPTY = new Releases(Map.of(), List.of(), List.of());

	private final Map<VersioningScheme, List<Release>> partitions;

	private final Map<VersioningScheme, Map<ArtifactVersion, Release>> lookup = new HashMap<>();

	private final List<VersioningScheme> schemesByRank;

	private final List<Release> ordered;

	private final Set<Release> unique = new HashSet<>();

	private Releases(Map<VersioningScheme, List<Release>> partitions, List<VersioningScheme> schemesByRank,
			List<Release> ordered) {
		this.partitions = partitions;
		this.schemesByRank = schemesByRank;
		this.ordered = ordered;

		partitions.forEach((scheme, releases) -> {
			Map<ArtifactVersion, Release> map = new TreeMap<>();
			for (Release release : releases) {
				map.put(release.version(), release);
			}
			lookup.put(scheme, map);
		});
		this.unique.addAll(ordered);
	}

	/**
	 * Return the shared empty {@code Releases} instance.
	 *
	 * @return the empty {@code Releases} instance (shared, immutable).
	 */
	public static Releases empty() {
		return EMPTY;
	}

	/**
	 * Create {@code Releases} for a single release.
	 *
	 * @param release the release to include; must not be {@literal null}.
	 * @return a single-element {@code Releases} instance containing the given
	 * release.
	 */
	public static Releases just(Release release) {
		return of(List.of(release));
	}

	/**
	 * Create {@code Releases} from an array of releases.
	 *
	 * @param releases releases for the same artifact; must not be {@literal null}
	 * and must contain no {@literal null} elements.
	 * @return a new {@code Releases} instance containing the given releases.
	 */
	public static Releases of(Release... releases) {
		return of(List.of(releases));
	}

	/**
	 * Create {@code Releases} from a collection of releases.
	 *
	 * <p>The collection's iteration order is irrelevant; releases are partitioned
	 * by {@link VersioningScheme} and ordered as described in the class-level
	 * Javadoc.
	 *
	 * @param releases releases for the same artifact; must not be {@literal null}
	 * and must contain no {@literal null} elements.
	 * @return a new {@code Releases} instance containing the given releases.
	 * @see #just(Release)
	 */
	public static Releases of(Collection<Release> releases) {

		Map<VersioningScheme, List<Release>> partitions = new EnumMap<>(VersioningScheme.class);
		for (Release release : releases) {
			partitions.computeIfAbsent(release.version().scheme(), scheme -> new ArrayList<>()).add(release);
		}

		for (Map.Entry<VersioningScheme, List<Release>> entry : partitions.entrySet()) {
			entry.getValue().sort(Comparator.reverseOrder());
			entry.setValue(entry.getValue());
		}

		List<VersioningScheme> schemesByRank = partitions.keySet().stream()
				.sorted(Comparator.comparing((VersioningScheme scheme) -> lastActivity(partitions.get(scheme)))
						.reversed())
				.toList();

		List<Release> ordered = new ArrayList<>(releases.size());
		for (VersioningScheme scheme : schemesByRank) {
			ordered.addAll(partitions.get(scheme));
		}

		return new Releases(partitions, schemesByRank, ordered);
	}

	private static LocalDateTime lastActivity(List<Release> partition) {

		LocalDateTime latest = LocalDateTime.MIN;
		for (Release release : partition) {
			LocalDateTime date = release.releaseDate();
			if (date != null && date.isAfter(latest)) {
				latest = date;
			}
		}
		return latest;
	}

	/**
	 * Create a new {@code Releases} instance that also includes the given release.
	 *
	 * <p>The release is ordered into its {@link VersioningScheme} partition rather
	 * than appended; an already-contained release is not deduplicated.
	 *
	 * @param release the release to include; must not be {@literal null}.
	 * @return a new {@code Releases} instance containing all existing releases and
	 * the given release.
	 */
	public Releases withRelease(Release release) {
		List<Release> releases = new ArrayList<>(ordered);
		releases.add(release);
		return of(releases);
	}

	/**
	 * Return the releases within the given {@link VersioningScheme}, newest first.
	 *
	 * <p>Upgrade strategies can use this view when the current version already
	 * identifies the scheme to compare against.
	 *
	 * @param scheme the versioning scheme to select; must not be {@literal null}.
	 * @return the releases in the given scheme, newest first, or an empty list when
	 * none are known.
	 */
	public List<Release> inScheme(VersioningScheme scheme) {
		return partitions.getOrDefault(scheme, List.of());
	}

	/**
	 * Return the successor scheme, i.e. the scheme with the most recent dated
	 * release.
	 *
	 * <p>If an artifact switched schemes more than once, the most recently active
	 * scheme wins again. Date metadata is what makes a cross-scheme successor
	 * meaningful; without dates, callers should avoid treating a multi-scheme
	 * history as evidence of a real migration.
	 *
	 * @return the successor scheme, or {@literal null} if there are no releases.
	 */
	public @Nullable VersioningScheme successorScheme() {
		return schemesByRank.isEmpty() ? null : schemesByRank.getFirst();
	}

	public @Nullable Release getRelease(ArtifactVersion version) {
		Map<ArtifactVersion, Release> byScheme = lookup.get(version.scheme());
		return byScheme != null ? byScheme.get(version) : null;
	}

	/**
	 * Return whether this object contains no releases.
	 *
	 * @return {@literal true} if this object contains no releases; {@literal false}
	 * otherwise.
	 */
	public boolean isEmpty() {
		return ordered.isEmpty();
	}

	/**
	 * Return whether this {@code Releases} instance contains the given release.
	 *
	 * @param release the release to look up; must not be {@literal null}.
	 * @return {@literal true} if the release is contained; {@literal false}
	 * otherwise.
	 */
	public boolean contains(Release release) {
		return unique.contains(release);
	}

	/**
	 * Iterate over the releases in the same order as {@link #toList()}.
	 */
	@Override
	public Iterator<Release> iterator() {
		return ordered.iterator();
	}

	/**
	 * Return a {@link Stream} of the {@link Release}s in the same order as
	 * {@link #toList()}.
	 *
	 * @return a stream using the same order as {@link #toList()}.
	 */
	public Stream<Release> stream() {
		return ordered.stream();
	}

	/**
	 * Return the releases in artifact-level release order.
	 *
	 * @return the ordered releases as an unmodifiable list; an empty list if
	 * {@link #isEmpty() empty}.
	 */
	public List<Release> toList() {
		return ordered;
	}

}
