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
 * Persistent release information and vulnerability scan state.
 *
 * @author Mark Paluch
 */
@Tag("release")
public class CachedRelease {

	/**
	 * Highest {@link #lastScanned} value that is a scan-attempt counter rather than
	 * a real scan timestamp. Values {@code 0..MAX_SCAN_ATTEMPTS} encode the
	 * {@link ScanState} lifecycle. A larger value is an epoch-millisecond scan
	 * timestamp. See the {@code lastScanned} note in {@code CONTEXT.md}.
	 */
	public static final int MAX_SCAN_ATTEMPTS = 5;

	@Attribute(converter = ArtifactVersionConverter.class)
	private ArtifactVersion version;

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
	 * Persisted scan state. Use {@link #scanState()} to distinguish retry counts
	 * from successful-scan timestamps.
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
	 * Create a release entry.
	 *
	 * @param date the ISO-8601 local date-time, if known.
	 */
	public CachedRelease(String version, @Nullable String date) {
		this(ArtifactVersion.of(version), date, null);
	}

	/**
	 * Create a release entry.
	 *
	 * @param date the ISO-8601 local date-time, if known.
	 * @param sha the opaque source-provided content hash, if known.
	 */
	public CachedRelease(String version, @Nullable String date, @Nullable String sha) {
		this(ArtifactVersion.of(version), date, sha);
	}

	/**
	 * Create a release entry.
	 *
	 * @param date the ISO-8601 local date-time, if known.
	 * @param sha the opaque source-provided content hash, if known.
	 */
	public CachedRelease(@Nullable ArtifactVersion version, @Nullable String date, @Nullable String sha) {
		this.version = version;
		this.date = date;
		this.sha = sha;
	}

	public static CachedRelease from(Release release) {
		if (release.version() instanceof GitVersion gitVersion) {
			return CachedRelease.from(gitVersion.getVersion(), release.releaseDate(), gitVersion.getSha());
		}
		return from(release.version(), release.releaseDate());
	}

	public static CachedRelease from(ArtifactVersion version, @Nullable LocalDateTime releaseDate) {
		return from(version, releaseDate, null);
	}

	public static CachedRelease from(ArtifactVersion version, @Nullable LocalDateTime releaseDate,
			@Nullable String sha) {
		return new CachedRelease(version, releaseDate != null ? releaseDate.toString() : null, sha);
	}

	/**
	 * Return the persisted version, or {@code null} if it could not be parsed.
	 */
	public ArtifactVersion version() {
		return version;
	}

	/**
	 * Return the ISO-8601 local date-time, if known.
	 */
	public @Nullable String date() {
		return date;
	}

	/**
	 * Return the opaque source-provided content hash, if known.
	 */
	public @Nullable String sha() {
		return sha;
	}

	/**
	 * Return the persisted attempt counter or scan timestamp. Values above
	 * {@link #MAX_SCAN_ATTEMPTS} are epoch-millisecond timestamps.
	 *
	 * @see #scanState()
	 */
	public long getLastScanned() {
		return lastScanned;
	}

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
	 * Record a completed scan. An empty result means clean.
	 *
	 * @param scannedAt the completion time in epoch milliseconds.
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

	@Transient
	public boolean hasVulnerabilities() {
		return !vulnerabilities.isEmpty();
	}

	/**
	 * Convert to a release.
	 *
	 * @throws IllegalStateException if the persisted version could not be parsed.
	 */
	@Transient
	public Release toRelease() {

		Release cachedRelease = this.release;
		if (cachedRelease == null) {

			ArtifactVersion version = version();
			if (version == null) {
				throw new IllegalStateException("No parseable version in %s".formatted(this));
			}
			if (StringUtils.hasText(sha())) {
				version = GitVersion.of(sha(), version);
			}

			cachedRelease = Release.from(version, date());
			this.release = cachedRelease;
		}
		return cachedRelease;
	}

	/**
	 * Return the scan result. An unsuccessful scan remains absent, distinct from a
	 * completed scan with no advisories.
	 */
	@Transient
	public Vulnerabilities toVulnerabilities() {

		Vulnerabilities view = this.vulnerabilitiesView;
		synchronized (this) {

			if (view == null) {
				if (scanState() != ScanState.SCANNED) {
					view = Vulnerabilities.absent();
				} else {

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
	 * Copy for persistence without sharing the mutable advisory list.
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
