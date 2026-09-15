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
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.VersionProperty;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.plan.UpgradePlanState.Dependency;
import biz.paluch.dap.plan.UpgradePlanState.Upgrade;
import biz.paluch.dap.state.ApplicationSettings;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Normalize shared version properties into implicit plan members.
 * <p>One member owns each property write. Conflicting targets remain separate
 * items.
 *
 * @author Mark Paluch
 */
class ImplicitGroups implements Sequence<Upgrade> {

	private final List<Upgrade> upgrades;

	public ImplicitGroups(List<Upgrade> upgrades) {
		this.upgrades = upgrades;
	}

	static ImplicitGroups create(Map<? extends UpgradePlanSource, ArtifactVersion> upgrades,
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

		List<Upgrade> items = new ArrayList<>(retained.size());
		for (ReviewedUpgrade reviewedUpgrade : retained) {
			items.add(reviewedUpgrade.toItem(owners, settings));
		}

		return new ImplicitGroups(items);
	}

	private static List<VersionProperty> properties(DependencyUpgradeSource candidate) {

		List<VersionProperty> properties = new ArrayList<>();
		for (VersionSource source : candidate.getDependency().getVersionSources()) {

			if (source instanceof VersionSource.VersionProperty property) {
				properties.add(new VersionProperty(candidate.getAssistantId(), property.getProperty()));
			}
		}
		return properties;
	}

	@Override
	public Iterator<Upgrade> iterator() {
		return this.upgrades.iterator();
	}

	@Override
	public List<Upgrade> toList() {
		return this.upgrades;
	}

	private record OwnerMatch(ReviewedUpgrade owner, VersionProperty property) {
	}

	/**
	 * A reviewed upgrade that accumulates members sharing its target.
	 */
	private static class ReviewedUpgrade {

		private final ArtifactVersion target;

		private final List<DependencyUpgradeSource> candidates;

		private final boolean group;

		private final Set<DependencyUpgradeSource> folded = new HashSet<>();

		private String name;

		ReviewedUpgrade(UpgradePlanSource capture, ArtifactVersion target) {
			this.name = capture.getDisplayName();
			this.target = target;
			this.candidates = new ArrayList<>(capture.getUpgrades());
			this.group = candidates.size() > 1;
		}

		boolean isGroup() {
			return group;
		}

		void claimProperties(Map<VersionProperty, ReviewedUpgrade> owners) {

			for (DependencyUpgradeSource candidate : candidates) {
				for (VersionProperty property : properties(candidate)) {
					owners.putIfAbsent(property, this);
				}
			}
		}

		/**
		 * Find a property owner with the same target. Keep diverging targets separate
		 * so the conflict remains visible.
		 */
		@Nullable
		OwnerMatch findOwner(Map<VersionProperty, ReviewedUpgrade> owners) {

			for (DependencyUpgradeSource candidate : candidates) {
				for (VersionProperty property : properties(candidate)) {

					ReviewedUpgrade owner = owners.get(property);
					if (owner != null && owner != this && owner.target.equals(target)) {
						return new OwnerMatch(owner, property);
					}
				}
			}
			return null;
		}

		void fold(ReviewedUpgrade peer, VersionProperty property, Map<VersionProperty, ReviewedUpgrade> owners) {

			if (!group && folded.isEmpty()) {
				this.name = property.property();
			}

			for (DependencyUpgradeSource candidate : peer.candidates) {
				candidates.add(candidate);
				folded.add(candidate);
			}
			peer.candidates.forEach(candidate -> {
				for (VersionProperty peerProperty : properties(candidate)) {
					owners.putIfAbsent(peerProperty, this);
				}
			});
		}

		Upgrade toItem(Map<VersionProperty, ReviewedUpgrade> owners, ApplicationSettings settings) {

			Set<VersionProperty> claimed = new HashSet<>();
			List<Dependency> members = new ArrayList<>(candidates.size());
			List<PackageIdentity> packages = new ArrayList<>(candidates.size());
			for (DependencyUpgradeSource candidate : candidates) {
				members.add(toMember(candidate, claimed, owners));
				packages.add(candidate.getPackageIdentity());
			}

			String hint = null;
			// A rule-defined name outranks remembered user hints.
			if (!isRuleNamed()) {
				hint = settings.findNameHint(packages);
			}
			return Upgrade.from(hint != null ? hint : name, target, members, candidates);
		}

		private boolean isRuleNamed() {

			for (DependencyUpgradeSource candidate : candidates) {
				if (StringUtils.hasText(candidate.getDependencyName())) {
					return true;
				}
			}
			return false;
		}

		private Dependency toMember(DependencyUpgradeSource candidate, Set<VersionProperty> claimed,
				Map<VersionProperty, ReviewedUpgrade> owners) {

			biz.paluch.dap.artifact.Dependency dependency = candidate.getDependency();
			Set<VersionSource> sources = dependency.getVersionSources();
			List<VersionSource> retained = new ArrayList<>(sources.size());
			boolean propertyBased = false;

			for (VersionSource source : sources) {

				if (!(source instanceof VersionSource.VersionProperty property)) {
					retained.add(source);
					continue;
				}

				propertyBased = true;
				VersionProperty key = new VersionProperty(candidate.getAssistantId(), property.getProperty());

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
				return Dependency.of(candidate);
			}

			if (retained.isEmpty() && propertyBased) {
				Dependency member = Dependency.of(candidate);
				member.implicit = true;
				return member;
			}

			biz.paluch.dap.artifact.Dependency subset = new biz.paluch.dap.artifact.Dependency(
					dependency.getPackageIdentity(), dependency.getCurrentVersion());
			subset.addAllVersionSources(retained);
			subset.addAllDeclarationSources(dependency.getDeclarationSources());
			return new Dependency(subset, candidate.getAssistantId());
		}

	}

}
