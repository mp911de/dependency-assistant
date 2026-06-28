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

package biz.paluch.dap.state;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.util.StringUtils;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jspecify.annotations.Nullable;

/**
 * Persistent representation of a cached artifact release.
 * <p>The serialized form stores the release version, an optional ISO-8601 date
 * string, and optional source-provided sha metadata. Conversion back to the
 * domain {@link Release} type is performed lazily and memoized for repeated
 * access within the same JVM instance.
 *
 * @author Mark Paluch
 */
@Tag("release")
public class CachedRelease {

	/**
	 * Highest {@link #lastScanned} value that is a scan-attempt counter rather than
	 * a real scan timestamp. Values {@code 0..MAX_SCAN_ATTEMPTS} encode the
	 * {@link ScanState} lifecycle; a larger value is an epoch-millisecond scan
	 * timestamp. See the {@code lastScanned} note in {@code CONTEXT.md}.
	 */
	public static final int MAX_SCAN_ATTEMPTS = 5;

	@Attribute
	private @Nullable String version;

	@Attribute
	private @Nullable String date;

	/**
	 * Opaque content hash for this version, when one is published at the source.
	 * For example, sha256 for distribution archives or commit hash for git-backed
	 * artifacts.
	 */
	@Attribute
	private @Nullable String sha;

	/**
	 * Overloaded scan field, read through {@link #scanState()} rather than by raw
	 * value. A value of {@code 0..}{@link #MAX_SCAN_ATTEMPTS} is the scan-attempt
	 * counter ({@code 0} never scanned, {@link #MAX_SCAN_ATTEMPTS} unresolvable); a
	 * larger value is the epoch-millisecond timestamp of a completed scan, from
	 * which the scan is derived.
	 */
	@Attribute
	private long lastScanned;

	private final @XCollection(propertyElementName = "vulnerabilities", elementName = "vulnerability", style = XCollection.Style.v2) List<CachedVulnerability> vulnerabilities = new ArrayList<>();

	private volatile @Nullable Release release;

	private volatile @Nullable Vulnerabilities vulnerabilitiesView;

	/**
	 * Create an empty release entry for XML deserialization.
	 */
	public CachedRelease() {
	}

	/**
	 * Create a release entry with the given serialized values.
	 *
	 * @param version the release version.
	 * @param date the optional release date in ISO-8601 local date-time form.
	 */
	public CachedRelease(String version, @Nullable String date) {
		this.version = version;
		this.date = date;
	}

	/**
	 * Create a release entry with the given serialized values including a content
	 * hash.
	 *
	 * @param version the release version.
	 * @param date the optional release date in ISO-8601 local date-time form.
	 * @param sha the opaque content hash, or {@literal null}.
	 */
	public CachedRelease(String version, @Nullable String date, @Nullable String sha) {
		this.version = version;
		this.date = date;
		this.sha = sha;
	}

	/**
	 * Create a cached representation of the given release.
	 *
	 * @param release the domain release to convert.
	 * @return the corresponding cached release representation.
	 */
	public static CachedRelease from(Release release) {
		if (release.version() instanceof GitVersion gitVersion) {
			return CachedRelease.from(gitVersion.getVersion(), release.releaseDate(), gitVersion.getSha());
		}
		return from(release.version(), release.releaseDate());
	}

	/**
	 * Create a cached representation of the given release.
	 *
	 * @param version the release version.
	 * @param releaseDate the release date, or {@literal null} if unknown.
	 * @return the corresponding cached release representation.
	 */
	public static CachedRelease from(ArtifactVersion version, @Nullable LocalDateTime releaseDate) {
		if (releaseDate != null) {
			return new CachedRelease(version.toString(), releaseDate.toString());
		}
		return new CachedRelease(version.toString(), null);
	}

	/**
	 * Create a cached release representation.
	 *
	 * @return the corresponding cached release representation.
	 */
	public static CachedRelease from(ArtifactVersion version, @Nullable LocalDateTime releaseDate,
			@Nullable String sha) {
		if (releaseDate != null) {
			return new CachedRelease(version.toString(), releaseDate.toString(), sha);
		}
		return new CachedRelease(version.toString(), null, sha);
	}

	/**
	 * Return the serialized release version.
	 *
	 * @return the release version.
	 */
	@Attribute
	public String version() {
		return version;
	}

	/**
	 * Return the serialized release date.
	 *
	 * @return the optional ISO-8601 local date-time string.
	 */
	@Attribute
	public @Nullable String date() {
		return date;
	}

	/**
	 * Return the opaque content hash for this version, or {@literal null} if not
	 * stored.
	 *
	 * @return the content hash, or {@literal null}.
	 */
	@Attribute
	public @Nullable String sha() {
		return sha;
	}

	/**
	 * Return the timestamp at which this version was last scanned for
	 * vulnerabilities.
	 */
	public long getLastScanned() {
		return lastScanned;
	}

	/**
	 * Return the {@link ScanState} of this release, derived from
	 * {@link #lastScanned} by magnitude.
	 *
	 * @return the scan state; never {@literal null}.
	 */
	@Transient
	public ScanState scanState() {

		if (lastScanned == 0) {
			return ScanState.NEVER_SCANNED;
		}
		if (lastScanned < MAX_SCAN_ATTEMPTS) {
			return ScanState.ATTEMPTED;
		}
		if (lastScanned == MAX_SCAN_ATTEMPTS) {
			return ScanState.UNRESOLVABLE;
		}
		return ScanState.SCANNED;
	}

	/**
	 * Store the vulnerabilities found by a completed scan, stamping the scan time.
	 *
	 * @param scannedAt the time the scan completed.
	 * @param vulnerabilities the vulnerabilities found, possibly empty for a clean
	 * scan.
	 */
	public void setVulnerabilities(long scannedAt, Iterable<Vulnerability> vulnerabilities) {

		this.lastScanned = scannedAt;
		synchronized (this) {
			this.vulnerabilities.clear();
			for (Vulnerability vulnerability : vulnerabilities) {
				this.vulnerabilities.add(CachedVulnerability.from(vulnerability));
			}
			this.vulnerabilitiesView = null;
		}
	}

	/**
	 * Record one completed scan attempt that returned no data for this release,
	 * advancing the scan-attempt counter toward {@link ScanState#UNRESOLVABLE}. A
	 * no-op once the budget is spent or once a real scan has been recorded.
	 */
	public void recordAttempt() {

		if (lastScanned < MAX_SCAN_ATTEMPTS) {
			this.lastScanned++;
			this.vulnerabilitiesView = null;
		}
	}

	/**
	 * Return whether there are known vulnerabilities for this version.
	 *
	 * @return {@literal true} if there are known vulnerabilities; {@literal false}
	 * otherwise.
	 */
	@Transient
	public boolean hasVulnerabilities() {
		return !vulnerabilities.isEmpty();
	}

	/**
	 * Return this entry as a domain {@link Release}.
	 * <p>The returned instance is memoized after the first conversion.
	 *
	 * @return the corresponding release.
	 */
	@Transient
	public Release toRelease() {

		Release cachedRelease = this.release;
		if (cachedRelease == null) {

			ArtifactVersion version = ArtifactVersion.of(version());
			if (StringUtils.hasText(sha())) {
				version = GitVersion.of(sha(), version);
			}

			cachedRelease = Release.from(version, date());
			this.release = cachedRelease;
		}
		return cachedRelease;
	}

	/**
	 * Return the {@link Vulnerabilities} derived from the stored scan timestamp and
	 * vulnerability list.
	 * <p>A release that was never scanned is absent; a scanned release with no
	 * vulnerabilities is clean; a scanned release with vulnerabilities is
	 * vulnerable. The returned instance is memoized after the first derivation.
	 *
	 * @return the vulnerabilities object.
	 */
	@Transient
	public Vulnerabilities toVulnerabilities() {

		Vulnerabilities view = this.vulnerabilitiesView;
		if (view == null) {

			if (scanState() != ScanState.SCANNED) {
				view = Vulnerabilities.absent();
			} else {
				synchronized (this) {
					List<Vulnerability> result = new ArrayList<>(vulnerabilities.size());
					for (CachedVulnerability vulnerability : vulnerabilities) {
						result.add(vulnerability.toVulnerability());
					}
					view = Vulnerabilities.of(result);
				}
			}
			this.vulnerabilitiesView = view;
		}
		return view;
	}

	/**
	 * Return an isolated copy safe to serialize while the original may still be
	 * mutated by a concurrent vulnerability scan.
	 *
	 * @return an independent copy of this release; never {@literal null}.
	 */
	CachedRelease snapshot() {

		synchronized (this) {
			CachedRelease copy = new CachedRelease(version, date, sha);
			copy.lastScanned = lastScanned;
			copy.vulnerabilities.addAll(vulnerabilities);
			return copy;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		CachedRelease that = (CachedRelease) obj;
		return Objects.equals(this.version, that.version) && Objects.equals(this.date, that.date)
				&& Objects.equals(this.sha, that.sha);
	}

	@Override
	public int hashCode() {
		return Objects.hash(version, date, sha);
	}

	@Override
	public String toString() {
		return "Release[" + "version=" + version + ", " + "date=" + date + ", sha=" + sha + ']';
	}

}
