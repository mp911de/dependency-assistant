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

package biz.paluch.dap.checker;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Three-state result of a vulnerability indicator.
 * <p>The state is one of:
 * <ul>
 * <li><em>absent</em> no vulnerability scan exists for the version. An unknown
 * result never renders as clean.</li>
 * <li><em>clean</em> no vulnerabilities were found.</li>
 * <li><em>vulnerable</em> one or more vulnerabilities were found.</li>
 * </ul>
 * Callers must distinguish absent from clean before acting; only {@link #get()}
 * on a non-absent result is contractually defined.
 *
 * @author Mark Paluch
 */
public class Vulnerabilities implements Iterable<Vulnerability> {

	private static final Vulnerabilities ABSENT = new Vulnerabilities(null);

	private static final Vulnerabilities CLEAN = new Vulnerabilities(List.of());

	private final @Nullable Collection<Vulnerability> vulnerabilities;

	private final @Nullable Vulnerability topVulnerability;

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
	 * Return the result for a version with no vulnerability scan.
	 *
	 * @return the absent result.
	 */
	public static Vulnerabilities absent() {
		return ABSENT;
	}

	/**
	 * Return the result for a scanned version with no vulnerabilities.
	 *
	 * @return the clean result.
	 */
	public static Vulnerabilities clean() {
		return CLEAN;
	}

	/**
	 * Return the result for a scanned version, deriving clean from an empty list
	 * and vulnerable otherwise.
	 *
	 * @param vulnerabilities the vulnerabilities found.
	 * @return a clean result when the list is empty, a vulnerable result otherwise.
	 */
	public static Vulnerabilities of(Vulnerability... vulnerabilities) {
		return of(List.of(vulnerabilities));
	}

	/**
	 * Return the result for a scanned version, deriving clean from an empty list
	 * and vulnerable otherwise.
	 *
	 * @param vulnerabilities the vulnerabilities found.
	 * @return a clean result when the list is empty, a vulnerable result otherwise.
	 */
	public static Vulnerabilities of(Collection<Vulnerability> vulnerabilities) {
		return vulnerabilities.isEmpty() ? CLEAN : new Vulnerabilities(List.copyOf(vulnerabilities));
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
	 * Return the known vulnerabilities.
	 *
	 * @return an empty list when clean, the found vulnerabilities when vulnerable.
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
