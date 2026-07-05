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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.Vulnerability;

/**
 * Aggregated {@link Vulnerabilities} of a Bill of Materials declaration line
 * over the used members whose effective version is managed by the BOM.
 * <p>The aggregate holds only vulnerable members and reports each advisory
 * once, even when several members are affected by the same advisory. Instances
 * are created through a {@link Builder} that enforces both invariants.
 *
 * @author Mark Paluch
 */
public class BomAggregate extends Vulnerabilities {

	private final ArtifactId artifactId;

	private BomAggregate(ArtifactId artifactId, List<MemberAdvisories> vulnerableMembers) {
		super(union(vulnerableMembers));
		this.artifactId = artifactId;
	}

	/**
	 * Create a builder collecting member scan results for the BOM declaration line
	 * identified by {@code artifactId}.
	 */
	static Builder builder(ArtifactId artifactId) {
		return new Builder(artifactId);
	}

	private static Collection<Vulnerability> union(List<MemberAdvisories> vulnerableMembers) {

		Map<String, Vulnerability> union = new LinkedHashMap<>();
		vulnerableMembers.stream().flatMap(it -> it.getVulnerabilities().stream()).forEach(vulnerability -> {
			union.putIfAbsent(vulnerability.getIdentifier(), vulnerability);
		});

		return union.values();
	}

	/**
	 * Return the BOM artifact coordinates of the declaration line this aggregate
	 * was created for.
	 */
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	/**
	 * Builder retaining the vulnerable members of a BOM declaration line.
	 * <p>Members whose scan result is clean or unknown and repeated registrations
	 * of the same artifact are ignored.
	 */
	static class Builder {

		private final ArtifactId artifactId;

		private final Map<ArtifactId, MemberAdvisories> members = new LinkedHashMap<>();

		private Builder(ArtifactId artifactId) {
			this.artifactId = artifactId;
		}

		/**
		 * Register a member whose effective version is {@code managedVersion} along
		 * with its scan result.
		 */
		Builder member(ArtifactId artifactId, ArtifactVersion managedVersion, Vulnerabilities vulnerabilities) {

			if (vulnerabilities.isVulnerable()) {
				members.putIfAbsent(artifactId, new MemberAdvisories(artifactId, managedVersion, vulnerabilities));
			}

			return this;
		}

		/**
		 * Register a member whose effective version is {@code managedVersion} along
		 * with its scan result.
		 */
		Builder member(ArtifactId artifactId, ArtifactVersion managedVersion,
				BiFunction<ArtifactId, ArtifactVersion, Vulnerabilities> vulnerabilitiesLookup) {

			Vulnerabilities vulnerabilities = vulnerabilitiesLookup.apply(artifactId, managedVersion);

			return member(artifactId, managedVersion, vulnerabilities);
		}

		/**
		 * Return the aggregate over the vulnerable members, or {@code fallback} if no
		 * member is vulnerable.
		 */
		Vulnerabilities orElse(Vulnerabilities fallback) {
			return members.isEmpty() ? fallback : new BomAggregate(artifactId, List.copyOf(members.values()));
		}

	}

	/**
	 * One vulnerable member with its managed version and advisories.
	 */
	public static class MemberAdvisories {

		private final Vulnerabilities vulnerabilities;

		private MemberAdvisories(ArtifactId artifactId, ArtifactVersion managedVersion,
				Vulnerabilities vulnerabilities) {
			this.vulnerabilities = vulnerabilities;
		}

		Vulnerabilities getVulnerabilities() {
			return vulnerabilities;
		}

	}

}
