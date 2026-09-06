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
 * Vulnerability knowledge for one package version.
 * <p>Absent means no source answered. Clean means checked without advisories. A
 * vulnerable result contains at least one advisory. Accessing or traversing
 * advisories requires a non-absent result.
 *
 * @author Mark Paluch
 */
public class Vulnerabilities implements Iterable<Vulnerability> {

	private static final Vulnerabilities ABSENT = new Vulnerabilities(null);

	private static final Vulnerabilities CLEAN = new Vulnerabilities(List.of());

	private final @Nullable Collection<Vulnerability> vulnerabilities;

	private final @Nullable Vulnerability topVulnerability;

	/**
	 * Retain advisories that must not change after construction.
	 * @param vulnerabilities {@literal null} for absent, empty for clean, or known
	 * advisories.
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
	 * Return the absent result for a version no source answered.
	 */
	public static Vulnerabilities absent() {
		return ABSENT;
	}

	/**
	 * Return a checked result with no advisories.
	 */
	public static Vulnerabilities clean() {
		return CLEAN;
	}

	/**
	 * Create a checked result, clean when no advisories are supplied.
	 */
	public static Vulnerabilities of(Vulnerability... vulnerabilities) {
		return of(List.of(vulnerabilities));
	}

	/**
	 * Create a checked result, clean when no advisories are supplied.
	 * <p>The collection is retained and must not be modified.
	 */
	public static Vulnerabilities of(Collection<Vulnerability> vulnerabilities) {
		return vulnerabilities.isEmpty() ? CLEAN : new Vulnerabilities(vulnerabilities);
	}

	/**
	 * Combine advisories, removing duplicate values in encounter order.
	 * <p>Absent results contribute no advisories. Even two absent results combine
	 * to a clean result.
	 */
	@CheckReturnValue
	public Vulnerabilities addAll(Vulnerabilities v) {
		Set<Vulnerability> set = new LinkedHashSet<>(this.vulnerabilities != null ? this.vulnerabilities : Set.of());
		if (v.vulnerabilities != null) {
			set.addAll(v.vulnerabilities);
		}
		return new Vulnerabilities(set);
	}

	public boolean isUnknown() {
		return vulnerabilities == null;
	}

	public boolean isClean() {
		return vulnerabilities != null && vulnerabilities.isEmpty();
	}

	public boolean isVulnerable() {
		return vulnerabilities != null && !vulnerabilities.isEmpty();
	}

	/**
	 * Return the known advisories. Callers must not modify the collection.
	 * @throws IllegalStateException if the result is absent.
	 */
	public Collection<Vulnerability> get() {
		Assert.state(vulnerabilities != null, "No vulnerabilities");
		return vulnerabilities;
	}

	/**
	 * Return the highest known severity.
	 * @throws IllegalStateException if the result is absent or clean.
	 */
	public CvssSeverity getHighestSeverity() {
		return getTopVulnerability().getSeverity();
	}

	/**
	 * Return the most severe advisory, taking the first when severity ranks tie.
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
