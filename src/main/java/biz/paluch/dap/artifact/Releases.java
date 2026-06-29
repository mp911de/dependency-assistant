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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import biz.paluch.dap.support.UpgradeStrategy;
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
 * <p>The scheme with the most recent dated release is the successor scheme.
 * Releases in that scheme appear before releases from superseded schemes.
 * Release dates choose scheme precedence; they do not interleave releases from
 * different schemes. Within each scheme, releases follow the scheme's version
 * order, newest first.
 *
 * @author Mark Paluch
 * @see Release
 * @see VersioningScheme
 * @see UpgradeStrategy
 */
public class Releases implements Iterable<Release> {

	private static final Releases EMPTY = new Releases(Map.of(), null, List.of());

	private final Map<VersioningScheme, List<Release>> partitions;

	private final @Nullable VersioningScheme successorScheme;

	private final List<Release> ordered;

	private final Set<Release> unique;

	private Releases(Map<VersioningScheme, List<Release>> partitions, @Nullable VersioningScheme successorScheme,
			List<Release> ordered) {
		this.partitions = immutablePartitions(partitions);
		this.successorScheme = successorScheme;
		this.ordered = List.copyOf(ordered);
		this.unique = Set.copyOf(ordered);
	}

	private static Map<VersioningScheme, List<Release>> immutablePartitions(
			Map<VersioningScheme, List<Release>> partitions) {

		Map<VersioningScheme, List<Release>> copy = new EnumMap<>(VersioningScheme.class);
		partitions.forEach((scheme, releases) -> copy.put(scheme, List.copyOf(releases)));
		return copy;
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
	 * @param release the release to include.
	 * @return a single-element {@code Releases} instance containing the given
	 * release.
	 */
	public static Releases just(Release release) {
		return of(List.of(release));
	}

	/**
	 * Create {@code Releases} from an array of releases.
	 *
	 * @param releases releases for the same artifact and must contain no
	 * {@literal null} elements.
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
	 * @param releases releases for the same artifact and must contain no
	 * {@literal null} elements.
	 * @return a new {@code Releases} instance containing the given releases.
	 * @see #just(Release)
	 */
	public static Releases of(Collection<Release> releases) {

		if (releases.isEmpty()) {
			return empty();
		}

		Map<VersioningScheme, List<Release>> partitions = new EnumMap<>(VersioningScheme.class);
		for (Release release : releases) {
			partitions.computeIfAbsent(release.version().scheme(), scheme -> new ArrayList<>()).add(release);
		}

		for (Map.Entry<VersioningScheme, List<Release>> entry : partitions.entrySet()) {
			entry.getValue().sort(Comparator.reverseOrder());
		}

		return fromSortedPartitions(partitions);
	}

	private static Releases fromSortedPartitions(Map<VersioningScheme, List<Release>> partitions) {

		if (partitions.isEmpty()) {
			return empty();
		}

		List<VersioningScheme> schemesByRank = partitions.keySet().stream()
				.sorted(Comparator.comparing((VersioningScheme scheme) -> lastActivity(partitions.get(scheme)))
						.reversed())
				.toList();

		int size = partitions.values().stream().mapToInt(List::size).sum();
		List<Release> ordered = new ArrayList<>(size);
		for (VersioningScheme scheme : schemesByRank) {
			ordered.addAll(partitions.get(scheme));
		}

		return new Releases(partitions, schemesByRank.getFirst(), ordered);
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
	 * Create a new {@link Builder} that assembles a {@code Releases} instance from
	 * individual version strings, {@link ArtifactVersion versions}, and
	 * {@link Release releases}.
	 *
	 * @return a new, empty builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a new {@code Releases} instance that also includes the given release.
	 *
	 * <p>The release is ordered into its {@link VersioningScheme} partition rather
	 * than appended; an already-contained release is not deduplicated.
	 *
	 * @param release the release to include.
	 * @return a new {@code Releases} instance containing all existing releases and
	 * the given release.
	 */
	public Releases withRelease(Release release) {

		List<Release> releases = new ArrayList<>(ordered.size() + 1);
		releases.add(release);
		releases.addAll(ordered);
		return of(releases);
	}

	/**
	 * Create a new {@code Releases} instance that also includes the given version
	 * as an undated release when it is not already present.
	 *
	 * @param version the version to include.
	 * @return this instance if the version is already present, or a new instance
	 * containing the additional release.
	 */
	public Releases withVersion(ArtifactVersion version) {

		if (getRelease(version) != null) {
			return this;
		}

		return withRelease(Release.of(version));
	}

	/**
	 * Create a new {@code Releases} instance retaining only releases accepted by
	 * the given predicate.
	 *
	 * <p>Existing partition ordering is reused; scheme precedence is recomputed for
	 * the retained subset.
	 *
	 * @param predicate the release predicate.
	 * @return the retained releases.
	 */
	public Releases filter(Predicate<Release> predicate) {
		return of(ordered.stream().filter(predicate).toList());
	}

	/**
	 * Return the releases within the given {@link VersioningScheme}, newest first.
	 *
	 * <p>Upgrade strategies can use this view when the current version already
	 * identifies the scheme to compare against.
	 *
	 * @param scheme the versioning scheme to select.
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
		return successorScheme;
	}

	/**
	 * Return the release matching the given version within its
	 * {@link VersioningScheme}.
	 *
	 * @param version the version to look up.
	 * @return the matching release, or {@literal null} if no release matches.
	 */
	public @Nullable Release getRelease(ArtifactVersion version) {
		for (Release release : inScheme(version.scheme())) {
			if (release.version().matches(version)) {
				return release;
			}
		}
		return null;
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
	 * @param release the release to look up.
	 * @return {@literal true} if the release is contained; {@literal false}
	 * otherwise.
	 */
	public boolean contains(Release release) {
		return unique.contains(release);
	}

	/**
	 * Return whether this {@code Releases} instance contains all the given release.
	 * @return {@literal true} if all releases are contained; {@literal false}
	 * otherwise.
	 */
	public boolean containsAll(Collection<Release> releases) {
		return unique.containsAll(releases);
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
	 * @return the immutable list of ordered releases. An empty list if
	 * {@link #isEmpty() empty}.
	 */
	public List<Release> toList() {
		return ordered;
	}

	/**
	 * Return the number of releases.
	 *
	 * @return the number of releases.
	 */
	public int size() {
		return ordered.size();
	}

	@Override
	public String toString() {
		return stream().map(Release::version).map(Object::toString).collect(Collectors.joining(", "));
	}

	/**
	 * Builder for {@link Releases}.
	 */
	public static class Builder {

		private final List<Release> releases = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Add a release for the given {@link ArtifactVersion}.
		 *
		 * @param version the version to add.
		 * @return this builder.
		 */
		public Builder add(ArtifactVersion version) {
			return add(Release.of(version));
		}

		/**
		 * Add the given release.
		 *
		 * @param release the release to add.
		 * @return this builder.
		 */
		public Builder add(Release release) {
			this.releases.add(release);
			return this;
		}

		/**
		 * Build the {@code Releases} instance from the collected releases.
		 *
		 * @return a new {@code Releases} instance; {@link Releases#empty() empty} when
		 * no releases were added.
		 */
		public Releases build() {
			return Releases.of(this.releases);
		}

	}

}
