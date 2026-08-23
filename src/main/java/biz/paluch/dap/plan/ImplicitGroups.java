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

package biz.paluch.dap.plan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.assistant.check.VersionProperty;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.plan.UpgradePlanState.Member;
import biz.paluch.dap.state.ApplicationSettings;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Capture-time normalization of Shared Version Properties into implicit group
 * members.
 *
 * @author Mark Paluch
 */
class ImplicitGroups implements Sequence<Item> {

	private final List<Item> items;

	public ImplicitGroups(List<Item> items) {
		this.items = items;
	}

	/**
	 * Normalize the reviewed upgrades into persisted items.
	 */
	static ImplicitGroups create(Map<? extends PlannedUpgrade, ArtifactVersion> upgrades,
			ApplicationSettings settings) {

		List<ReviewedUpgrade> reviewedUpgrades = new ArrayList<>(upgrades.size());
		upgrades.forEach((capture, version) -> reviewedUpgrades.add(new ReviewedUpgrade(capture, version)));

		Map<VersionProperty, ReviewedUpgrade> owners = new LinkedHashMap<>();
		for (ReviewedUpgrade reviewedUpgrade : reviewedUpgrades) {

			if (reviewedUpgrade.isGroup()) {
				reviewedUpgrade.claimProperties(owners);
			}
		}

		List<ReviewedUpgrade> retained = new ArrayList<>(reviewedUpgrades.size());
		for (ReviewedUpgrade reviewedUpgrade : reviewedUpgrades) {

			if (reviewedUpgrade.isGroup()) {
				retained.add(reviewedUpgrade);
				continue;
			}

			OwnerMatch match = reviewedUpgrade.findOwner(owners);
			if (match == null) {
				reviewedUpgrade.claimProperties(owners);
				retained.add(reviewedUpgrade);
				continue;
			}

			match.owner().fold(reviewedUpgrade, match.property(), owners);
		}

		List<Item> items = new ArrayList<>(retained.size());
		for (ReviewedUpgrade reviewedUpgrade : retained) {
			items.add(reviewedUpgrade.toItem(owners, settings));
		}

		return new ImplicitGroups(items);
	}

	/**
	 * Version properties of the candidate in version-source order, keyed by the
	 * owning assistant.
	 */
	private static List<VersionProperty> properties(DependencyUpgradeCandidate candidate) {

		List<VersionProperty> properties = new ArrayList<>();
		for (VersionSource source : candidate.getDependency().getVersionSources()) {

			if (source instanceof VersionSource.VersionProperty property) {
				properties.add(new VersionProperty(candidate.getAssistant().getId(), property.getProperty()));
			}
		}
		return properties;
	}

	@Override
	public Iterator<Item> iterator() {
		return this.items.iterator();
	}

	@Override
	public List<Item> toList() {
		return this.items;
	}

	/**
	 * The owning capture of a shared property together with the property that
	 * matched, used to name a newly formed implicit group.
	 */
	private record OwnerMatch(ReviewedUpgrade owner, VersionProperty property) {
	}

	/**
	 * One armed capture under normalization: its display name, pinned target, and
	 * the candidates it contributes, growing as peers fold in.
	 */
	private static class ReviewedUpgrade {

		private final ArtifactVersion target;

		private final List<DependencyUpgradeCandidate> candidates;

		private final boolean group;

		private final Set<DependencyUpgradeCandidate> folded = new HashSet<>();

		private String name;

		ReviewedUpgrade(PlannedUpgrade capture, ArtifactVersion target) {
			this.name = capture.getDependencyOrProjectName();
			this.target = target;
			this.candidates = new ArrayList<>(capture.getUpgradeCandidates());
			this.group = candidates.size() > 1;
		}

		boolean isGroup() {
			return group;
		}

		void claimProperties(Map<VersionProperty, ReviewedUpgrade> owners) {

			for (DependencyUpgradeCandidate candidate : candidates) {
				for (VersionProperty property : properties(candidate)) {
					owners.putIfAbsent(property, this);
				}
			}
		}

		/**
		 * Find the first capture, in version-source order, owning one of this capture's
		 * properties at the same pinned target. Diverging targets never match, keeping
		 * the conflict visible as separate items.
		 */
		@Nullable
		OwnerMatch findOwner(Map<VersionProperty, ReviewedUpgrade> owners) {

			for (DependencyUpgradeCandidate candidate : candidates) {
				for (VersionProperty property : properties(candidate)) {

					ReviewedUpgrade owner = owners.get(property);
					if (owner != null && owner != this && owner.target.equals(target)) {
						return new OwnerMatch(owner, property);
					}
				}
			}
			return null;
		}

		/**
		 * Absorb the peer's candidates as members of this capture. A top-level capture
		 * forming a group through its first fold is renamed to the bare property name;
		 * the peer's remaining properties are claimed so later peers join the same
		 * item.
		 */
		void fold(ReviewedUpgrade peer, VersionProperty property, Map<VersionProperty, ReviewedUpgrade> owners) {

			if (!group && folded.isEmpty()) {
				this.name = property.property();
			}

			for (DependencyUpgradeCandidate candidate : peer.candidates) {
				candidates.add(candidate);
				folded.add(candidate);
			}
			peer.candidates.forEach(candidate -> {
				for (VersionProperty peerProperty : properties(candidate)) {
					owners.putIfAbsent(peerProperty, this);
				}
			});
		}

		Item toItem(Map<VersionProperty, ReviewedUpgrade> owners, ApplicationSettings settings) {

			Set<VersionProperty> claimed = new HashSet<>();
			List<Member> members = new ArrayList<>(candidates.size());
			List<PackageIdentity> packages = new ArrayList<>(candidates.size());
			for (DependencyUpgradeCandidate candidate : candidates) {
				members.add(toMember(candidate, claimed, owners));
				packages.add(candidate.getDependency().getPackageIdentity());
			}

			String hint = null;
			if (!isRuleNamed()) {
				hint = settings.findNameHint(packages);
			}
			return Item.from(hint != null ? hint : name, target, members, candidates);
		}

		/**
		 * A capture governed by a rule carrying a dependency name keeps that name; the
		 * rule outranks any remembered hint.
		 */
		private boolean isRuleNamed() {

			for (DependencyUpgradeCandidate candidate : candidates) {
				if (StringUtils.hasText(candidate.getRule().getDependencyName())) {
					return true;
				}
			}
			return false;
		}

		private Member toMember(DependencyUpgradeCandidate candidate, Set<VersionProperty> claimed,
				Map<VersionProperty, ReviewedUpgrade> owners) {

			Dependency dependency = candidate.getDependency();
			Set<VersionSource> sources = dependency.getVersionSources();
			List<VersionSource> retained = new ArrayList<>(sources.size());
			boolean propertyBased = false;

			for (VersionSource source : sources) {

				if (!(source instanceof VersionSource.VersionProperty property)) {
					retained.add(source);
					continue;
				}

				propertyBased = true;
				VersionProperty key = new VersionProperty(candidate.getAssistant().getId(), property.getProperty());

				if (folded.contains(candidate)) {
					ReviewedUpgrade owner = owners.get(key);
					if (owner != null && owner != this && owner.target.equals(target)) {
						continue;
					}
				}
				if (claimed.add(key)) {
					retained.add(source);
				}
			}

			if (retained.size() == sources.size()) {
				return Member.of(candidate);
			}

			if (retained.isEmpty() && propertyBased) {
				Member member = Member.of(candidate);
				member.implicit = true;
				return member;
			}

			Dependency subset = new Dependency(dependency.getPackageIdentity(), dependency.getCurrentVersion());
			subset.addAllVersionSources(retained);
			subset.addAllDeclarationSources(dependency.getDeclarationSources());
			return new Member(subset, candidate.getAssistant());
		}

	}

}
