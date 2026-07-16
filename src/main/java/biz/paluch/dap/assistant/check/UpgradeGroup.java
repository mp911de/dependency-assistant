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

package biz.paluch.dap.assistant.check;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.checker.VulnerabilityRepository;

import org.springframework.util.Assert;

/**
 * A grouped upgrade over two or more member upgrades.
 *
 * <p>The group recomputes one {@link DependencyUpgradeCandidate} from the
 * intersection of member releases, the composite vulnerability repository, the
 * oldest member version, the first member's governing rule and assistant, and
 * the merged declared versions of all members. Member order is retained for
 * update fan-out.
 *
 * @author Mark Paluch
 */
public class UpgradeGroup {

	private final List<DependencyUpgradeCandidate> members;

	private final DependencyUpgradeCandidate upgrade;

	private UpgradeGroup(List<DependencyUpgradeCandidate> members) {

		Assert.isTrue(members.size() >= 2, "Upgrade group requires at least two members");
		this.members = members;
		this.upgrade = createUpgrade(members);
	}

	/**
	 * Create a group from the given member upgrades.
	 *
	 * @param members the upgrades to group, in member order.
	 * @return a group with a complete upgrade recomputed from all members.
	 */
	public static UpgradeGroup of(List<DependencyUpgradeCandidate> members) {
		return new UpgradeGroup(members);
	}

	private static DependencyUpgradeCandidate createUpgrade(List<DependencyUpgradeCandidate> members) {

		DependencyUpgradeCandidate first = members.getFirst();
		ArtifactVersion current = first.getCurrentVersion();
		for (DependencyUpgradeCandidate member : members) {
			if (current.isNewer(member.getCurrentVersion())) {
				current = member.getCurrentVersion();
			}
		}

		Dependency dependency = new Dependency(first.getArtifactId(), current);
		for (DependencyUpgradeCandidate member : members) {
			dependency.addAllDeclarationSources(member.getDependency().getDeclarationSources());
			dependency.addAllVersionSources(member.getDependency().getVersionSources());
		}

		List<VulnerabilityRepository> repositories = members.stream()
				.map(DependencyUpgradeCandidate::getVulnerabilities)
				.toList();
		DeclaredVersions declaredVersions = DeclaredVersions
				.merge(members.stream().map(DependencyUpgradeCandidate::getDeclaredVersions).toList());
		return DependencyUpgradeCandidate.create(dependency, intersectReleases(members),
				VulnerabilityRepository.composite(repositories), first.getRule(), first.getAssistant(),
				declaredVersions);
	}

	private static Releases intersectReleases(List<DependencyUpgradeCandidate> members) {

		Releases releases = members.getFirst().getReleases();
		Set<Release> retained = new HashSet<>(releases.toList());
		for (int i = 1; i < members.size(); i++) {
			retained.retainAll(members.get(i).getReleases().toList());
		}
		return releases.filter(retained::contains);
	}

	/**
	 * Return the grouped member upgrades in their original order.
	 *
	 * @return the immutable member list.
	 */
	public List<DependencyUpgradeCandidate> getMembers() {
		return members;
	}

	/**
	 * Return the upgrade recomputed from the grouped member facts.
	 *
	 * @return the grouped upgrade.
	 */
	public DependencyUpgradeCandidate getUpgrade() {
		return upgrade;
	}

}
