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

package biz.paluch.dap.plan;

import java.util.List;

import javax.swing.Icon;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.assistant.check.UpgradeCandidate;
import biz.paluch.dap.assistant.check.UpgradeGroup;
import biz.paluch.dap.checker.CvssSeverity;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.ticket.TicketKey;
import biz.paluch.dap.util.MessageBundle;
import org.jspecify.annotations.Nullable;

import org.springframework.util.ObjectUtils;

/**
 * One materialized Upgrade Plan item, pairing its live {@link UpgradeCandidate}
 * with member facts derived at construction and an independently replaceable
 * ticket association. Its serialized counterpart is
 * {@link UpgradePlanState.Item}.
 *
 * <p>Item identity is the {@link #getId() item id} alone: two items with the
 * same id are equal regardless of their versions or linked ticket. The linked
 * ticket is the one mutable aspect, replaced in place through
 * {@link #setTicket} by the link and unlink undo flow; every other member fact
 * is fixed at construction.
 *
 * @author Mark Paluch
 */
class UpgradePlanItem {

	private final ItemId itemId;

	private final UpgradeCandidate candidate;

	private final String displayName;

	private final AttentionLevel attentionLevel;

	private final int vulnerabilityCount;

	private final @Nullable CvssSeverity highestRatedVulnerabilitySeverity;

	private final ArtifactVersion from;

	private final ArtifactVersion to;

	private final Badge attentionBadge;

	private @Nullable UpgradeTicket ticket;

	private @Nullable Badge ticketBadge;

	/**
	 * Create a materialized item for the given candidate and pinned target version,
	 * deriving the source version as the oldest current version across the
	 * candidate's members and the attention level from the version span, or from a
	 * vulnerability that the upgrade resolves.
	 *
	 * @param itemId the stable identity of the item.
	 * @param candidate the live upgrade candidate, a single dependency or a group.
	 * @param targetVersion the pinned version the item upgrades to.
	 */
	UpgradePlanItem(ItemId itemId, UpgradeCandidate candidate, ArtifactVersion targetVersion) {

		this.itemId = itemId;
		this.candidate = candidate;
		this.displayName = candidate.getRowLabel();

		List<UpgradeCandidate> candidates = candidate instanceof UpgradeGroup group ? group.getMembers()
				: List.of(candidate);

		this.to = targetVersion;
		ArtifactVersion from = null;

		Vulnerabilities currentVulnerabilities = Vulnerabilities.clean();
		Vulnerabilities targetVulnerabilities = Vulnerabilities.clean();
		for (UpgradeCandidate member : candidates) {

			ArtifactVersion current = member.getCurrentVersion();
			currentVulnerabilities = currentVulnerabilities.addAll(member.getVulnerabilities(current));
			targetVulnerabilities = targetVulnerabilities.addAll(member.getVulnerabilities(targetVersion));

			if (from == null || from.isNewer(current)) {
				from = current;
			}
		}

		this.from = from == null ? targetVersion : from;
		this.attentionLevel = determineAttentionLevel(
				currentVulnerabilities.isVulnerable() && !targetVulnerabilities.isVulnerable());
		this.vulnerabilityCount = currentVulnerabilities.size();
		if (currentVulnerabilities.isVulnerable()) {
			this.highestRatedVulnerabilitySeverity = currentVulnerabilities.getHighestSeverity();
		} else {
			this.highestRatedVulnerabilitySeverity = CvssSeverity.NONE;
		}
		this.attentionBadge = createAttentionBadge();
	}

	/**
	 * Badge for the item's attention level.
	 */
	private Badge createAttentionBadge() {

		return switch (this.getAttentionLevel()) {
		case VULNERABILITY_FIX -> new Badge(MessageBundle.message("plan.badge.cve"), Badge.ColorType.GREEN,
				getVulnerabilityFixTooltip());
		case MAJOR -> new Badge(MessageBundle.message("upgrade-strategy.MAJOR"), Badge.ColorType.AMBER_SECONDARY,
				MessageBundle.message("plan.badge.major.tooltip"));
		case MINOR -> new Badge(MessageBundle.message("upgrade-strategy.MINOR"), Badge.ColorType.BLUE_SECONDARY,
				MessageBundle.message("plan.badge.minor.tooltip"));
		case PATCH -> new Badge(MessageBundle.message("upgrade-strategy.PATCH"), Badge.ColorType.GREEN_SECONDARY,
				MessageBundle.message("plan.badge.patch.tooltip"));
		case DOWNGRADE -> new Badge(MessageBundle.message("upgrade-strategy.DOWNGRADE"), Badge.ColorType.GRAY_SECONDARY,
				MessageBundle.message("plan.badge.downgrade.tooltip"));
		};
	}

	private String getVulnerabilityFixTooltip() {
		if (highestRatedVulnerabilitySeverity == null) {
			return MessageBundle.message("plan.badge.cve.tooltip.unrated", vulnerabilityCount);
		}
		return MessageBundle.message("plan.badge.cve.tooltip", vulnerabilityCount,
				highestRatedVulnerabilitySeverity.getLabel());
	}

	private AttentionLevel determineAttentionLevel(boolean vulnerabilityFix) {

		if (vulnerabilityFix) {
			return AttentionLevel.VULNERABILITY_FIX;
		}

		return switch (VersionAge.between(getFromVersion(), getToVersion())) {
		case NEWER_MAJOR -> AttentionLevel.MAJOR;
		case NEWER_MINOR -> AttentionLevel.MINOR;
		case OLDER -> AttentionLevel.DOWNGRADE;
		default -> AttentionLevel.PATCH;
		};
	}

	public ItemId getId() {
		return itemId;
	}

	public UpgradeCandidate getUpgradeCandidate() {
		return candidate;
	}

	public AttentionLevel getAttentionLevel() {
		return attentionLevel;
	}

	public Icon getIcon() {
		return candidate.getTableIcon();
	}

	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Return the version the item upgrades from: the oldest current version across
	 * the item's members, or the target version when no member declares one.
	 */
	public ArtifactVersion getFromVersion() {
		return from;
	}

	public ArtifactVersion getToVersion() {
		return to;
	}

	public boolean isGroup() {
		return candidate instanceof UpgradeGroup;
	}

	/**
	 * Return the member candidates of a group item, or an empty list for a single
	 * dependency item.
	 */
	public List<UpgradeCandidate> getMembers() {
		return candidate instanceof UpgradeGroup group ? group.getMembers() : List.of();
	}

	public Badge getAttentionBadge() {
		return attentionBadge;
	}

	public boolean hasTicket() {
		return ticket != null;
	}

	public @Nullable TicketKey getTicketKey() {
		return ticket != null ? TicketKey.of(ticket.getKey()) : null;
	}

	public @Nullable UpgradeTicket getTicket() {
		return ticket;
	}

	/**
	 * Replace the linked ticket association in place, refreshing the derived ticket
	 * badge. Driven by the link and unlink undo flow; passing {@literal null}
	 * clears both the association and the badge.
	 *
	 * @param ticket the linked ticket, or {@literal null} to clear the association.
	 */
	public void setTicket(@Nullable UpgradeTicket ticket) {
		this.ticket = ticket;
		if (ticket != null) {
			this.ticketBadge = new Badge(ticket.getDisplayReference(), Badge.ColorType.BLUE_SECONDARY,
					MessageBundle.message("plan.badge.ticket.tooltip", ticket.getDisplayReference()));
		} else {
			this.ticketBadge = null;
		}
	}

	public @Nullable Badge getTicketBadge() {
		return ticketBadge;
	}

	/**
	 * Return the build-file updates that move this item's members to the pinned
	 * target, fanned out to every member for a group item.
	 */
	public List<DependencyUpdate> createUpdates() {
		return candidate.createUpdates(getToVersion());
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof UpgradePlanItem planItem)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(itemId, planItem.itemId);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHashCode(itemId);
	}

	@Override
	public String toString() {
		return getDisplayName() + " " + getFromVersion() + " -> " + getToVersion();
	}

	/**
	 * Review attention assigned to an Upgrade Plan item, declared from highest to
	 * lowest attention.
	 *
	 * @author Mark Paluch
	 */
	enum AttentionLevel {

		VULNERABILITY_FIX,

		MAJOR,

		MINOR,

		PATCH,

		DOWNGRADE

	}

}
