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

package biz.paluch.dap.checker;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.jetbrains.annotations.CheckReturnValue;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Checked vulnerability result for one package version.
 *
 * <p>The result has three states:
 * <ul>
 * <li><em>absent</em>: no source returned data for the version.</li>
 * <li><em>clean</em>: the version was checked and no advisory was found.</li>
 * <li><em>vulnerable</em>: the version was checked and at least one advisory
 * was found.</li>
 * </ul>
 * Absent is intentionally distinct from clean. Only a non-absent result exposes
 * an advisory collection through {@link #get()}.
 *
 * @author Mark Paluch
 */
public class Vulnerabilities implements Iterable<Vulnerability> {

	private static final Vulnerabilities ABSENT = new Vulnerabilities(null);

	private static final Vulnerabilities CLEAN = new Vulnerabilities(List.of());

	private final @Nullable Collection<Vulnerability> vulnerabilities;

	private final @Nullable Vulnerability topVulnerability;

	/**
	 * Create a result backed by the given advisory collection.
	 *
	 * <p>A {@literal null} collection represents absent, an empty collection
	 * represents clean, and a non-empty collection represents vulnerable. The
	 * collection is retained and must not be mutated after construction.
	 *
	 * @param vulnerabilities the backing advisories, or {@literal null} for an
	 * absent result.
	 */
	protected Vulnerabilities(@Nullable Collection<Vulnerability> vulnerabilities) {
		this.vulnerabilities = vulnerabilities;
		this.topVulnerability = vulnerabilities == null || vulnerabilities.isEmpty() ? null
				: findMostSevereVulnerability(vulnerabilities);
	}

	private static Vulnerability findMostSevereVulnerability(Collection<Vulnerability> found) {

		Vulnerability top = found.iterator().next();
		for (Vulnerability vulnerability : found) {
			if (vulnerability.getSeverity().rank() > top.getSeverity().rank()) {
				top = vulnerability;
			}
		}
		return top;
	}

	/**
	 * Return the result for a version for which no source returned data.
	 *
	 * @return the absent result.
	 */
	public static Vulnerabilities absent() {
		return ABSENT;
	}

	/**
	 * Return the result for a checked version with no vulnerabilities.
	 *
	 * @return the clean result.
	 */
	public static Vulnerabilities clean() {
		return CLEAN;
	}

	/**
	 * Create a checked result from the given advisories.
	 *
	 * @param vulnerabilities the advisories found.
	 * @return a clean result when none are supplied, or a vulnerable result
	 * otherwise.
	 */
	public static Vulnerabilities of(Vulnerability... vulnerabilities) {
		return of(List.of(vulnerabilities));
	}

	/**
	 * Create a checked result from the given advisories.
	 *
	 * <p>The supplied collection is copied.
	 *
	 * @param vulnerabilities the advisories found.
	 * @return a clean result when the collection is empty, or a vulnerable result
	 * otherwise.
	 */
	public static Vulnerabilities of(Collection<Vulnerability> vulnerabilities) {
		return vulnerabilities.isEmpty() ? CLEAN : new Vulnerabilities(vulnerabilities);
	}

	/**
	 * Return the union of this result and the given result.
	 *
	 * <p>Duplicate advisory values are removed while preserving encounter order. An
	 * absent result contributes no advisory, so the union is clean when neither
	 * result contributes an advisory.
	 *
	 * @param v the result to combine with this result.
	 * @return a new result containing the combined advisories.
	 */
	@CheckReturnValue
	public Vulnerabilities addAll(Vulnerabilities v) {
		Set<Vulnerability> set = new LinkedHashSet<>(this.vulnerabilities != null ? this.vulnerabilities : Set.of());
		if (v.vulnerabilities != null) {
			set.addAll(v.vulnerabilities);
		}
		return new Vulnerabilities(set);
	}

	/**
	 * Return whether no vulnerability scan exists for the version.
	 *
	 * @return {@literal true} if absent; {@literal false} otherwise.
	 */
	public boolean isUnknown() {
		return vulnerabilities == null;
	}

	/**
	 * Return whether the version was scanned and found free of vulnerabilities.
	 *
	 * @return {@literal true} if clean; {@literal false} otherwise.
	 */
	public boolean isClean() {
		return vulnerabilities != null && vulnerabilities.isEmpty();
	}

	/**
	 * Return whether the version was scanned and found vulnerable.
	 *
	 * @return {@literal true} if at least one vulnerability is known;
	 * {@literal false} otherwise.
	 */
	public boolean isVulnerable() {
		return vulnerabilities != null && !vulnerabilities.isEmpty();
	}

	/**
	 * Return the known vulnerabilities for a checked result.
	 *
	 * @return an empty collection when clean, or the found vulnerabilities when
	 * vulnerable. Callers must not modify the returned collection.
	 * @throws IllegalStateException if the result is absent.
	 */
	public Collection<Vulnerability> get() {
		Assert.state(vulnerabilities != null, "No vulnerabilities");
		return vulnerabilities;
	}

	/**
	 * Return the most severe {@link CvssSeverity} across the known vulnerabilities.
	 * <p>Severity ranks {@code CRITICAL > HIGH > MEDIUM > LOW > NONE > UNKNOWN}, so
	 * an unrated advisory never outranks a rated one. Surfaces use the result to
	 * choose the security-shield icon.
	 *
	 * @return the highest severity among the known vulnerabilities.
	 * @throws IllegalStateException if the result is absent or clean.
	 */
	public CvssSeverity getHighestSeverity() {
		return getTopVulnerability().getSeverity();
	}

	/**
	 * Return the most severe known vulnerability.
	 *
	 * <p>When several advisories have the same severity rank, the first one in
	 * encounter order is returned.
	 *
	 * @return the most severe vulnerability.
	 * @throws IllegalStateException if the result is absent or clean.
	 */
	public Vulnerability getTopVulnerability() {
		Assert.state(topVulnerability != null, "No vulnerabilities");
		return topVulnerability;
	}

	public int size() {
		return get().size();
	}

	@Override
	public Iterator<Vulnerability> iterator() {
		return get().iterator();
	}

	public Stream<Vulnerability> stream() {
		return get().stream();
	}

	@Override
	public String toString() {
		if (isUnknown()) {
			return "unknown";
		}
		return isClean() ? "clean" : "vulnerable (%d)".formatted(size());
	}

}
