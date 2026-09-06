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
import biz.paluch.dap.util.Sequence;
import org.jspecify.annotations.Nullable;

/**
 * Immutable release history for one artifact.
 * <p>Projects can change versioning schemes. The scheme with the latest dated
 * release ranks first. Dates rank whole schemes rather than individual
 * releases. Within each scheme, versions appear newest first.
 * <p>Without dates, enum order provides a deterministic fallback. It does not
 * establish a real scheme migration.
 *
 * @author Mark Paluch
 * @see VersioningScheme
 * @see UpgradeStrategy
 */
public class Releases implements Sequence<Release> {

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

	public static Releases empty() {
		return EMPTY;
	}

	public static Releases just(Release release) {
		return of(List.of(release));
	}

	public static Releases just(ArtifactVersion version) {
		return of(Release.from(version));
	}

	/**
	 * Copy releases for one artifact into a history.
	 */
	public static Releases of(Release... releases) {
		return of(List.of(releases));
	}

	/**
	 * Copy releases for one artifact, ordering them by scheme and version.
	 */
	public static Releases of(Iterable<Release> releases) {

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

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Return a history including the additional release in version order.
	 * Duplicates are retained.
	 */
	public Releases withRelease(Release release) {

		List<Release> releases = new ArrayList<>(ordered.size() + 1);
		releases.add(release);
		releases.addAll(ordered);
		return of(releases);
	}

	/**
	 * Include the version as an undated release if absent.
	 * @return this history if the version is already present.
	 */
	public Releases withVersion(ArtifactVersion version) {

		if (getRelease(version) != null) {
			return this;
		}

		return withRelease(Release.of(version));
	}

	/**
	 * Return a filtered history, recomputing scheme precedence for the retained
	 * releases.
	 */
	public Releases filter(Predicate<Release> predicate) {
		return of(ordered.stream().filter(predicate).toList());
	}

	/**
	 * Return releases in the given scheme, newest first, or an empty list if none
	 * are known.
	 */
	public List<Release> inScheme(VersioningScheme scheme) {
		return partitions.getOrDefault(scheme, List.of());
	}

	/**
	 * Return the highest-ranked scheme, or {@literal null} if this history is
	 * empty.
	 */
	public @Nullable VersioningScheme successorScheme() {
		return successorScheme;
	}

	/**
	 * Find a matching version within its scheme, or return {@literal null} if
	 * absent.
	 */
	public @Nullable Release getRelease(ArtifactVersion version) {
		for (Release release : inScheme(version.scheme())) {
			if (release.version().matches(version)) {
				return release;
			}
		}
		return null;
	}

	@Override
	public boolean isEmpty() {
		return ordered.isEmpty();
	}

	public boolean contains(Release release) {
		return unique.contains(release);
	}

	public boolean containsAll(Collection<Release> releases) {
		return unique.containsAll(releases);
	}

	@Override
	public Iterator<Release> iterator() {
		return ordered.iterator();
	}

	@Override
	public Stream<Release> stream() {
		return ordered.stream();
	}

	/**
	 * Return the immutable releases in artifact-level order.
	 */
	@Override
	public List<Release> toList() {
		return ordered;
	}

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

		public Builder add(ArtifactVersion version) {
			return add(Release.of(version));
		}

		public Builder add(Release release) {
			this.releases.add(release);
			return this;
		}

		public Releases build() {
			return Releases.of(this.releases);
		}

	}

}
