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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.checker.CvssSeverity;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.ticket.TicketKey;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * One materialized Upgrade Plan item over persisted member facts, a pinned
 * target version, and the interface metadata needed to render its icon.
 * Persisted plan state is resolved into this type by the plan loader.
 *
 * <p>Item identity is the {@link #getId() item id} alone: two items with the
 * same id are equal regardless of their versions or linked ticket. The linked
 * ticket is the one mutable aspect, replaced in place through
 * {@link #setTicket} by the link and unlink undo flow; every other member fact
 * is fixed at construction.
 *
 * @author Mark Paluch
 */
class UpgradePlanItem implements Sequence<ItemDependency> {

	private final ItemId itemId;

	private final List<ItemDependency> members;

	private final String displayName;

	private final AttentionLevel attentionLevel;

	private final boolean vulnerabilityFix;

	private final int vulnerabilityCount;

	private final CvssSeverity highestVulnerabilitySeverity;

	private final ArtifactVersion from;

	private final String fromVersion;

	private final ArtifactVersion to;

	private final String toVersion;

	private final Icon icon;

	private final Badge attentionBadge;

	private @Nullable UpgradeTicket ticket;

	private @Nullable Badge ticketBadge;

	/**
	 * Create a materialized item from persisted state and resolved interface
	 * metadata, deriving the source version as the oldest current version across
	 * the members and the attention level from the version span.
	 *
	 * @param itemId the stable item identity.
	 * @param displayName the captured display name, or {@literal null} to obtain it
	 * from the first assistant.
	 * @param to the pinned target version.
	 * @param vulnerabilityFix whether the target fixes current vulnerabilities.
	 * @param vulnerabilityCount the captured current vulnerability count.
	 * @param highestVulnerabilitySeverity the captured highest current severity.
	 * @param members the reconstructed member dependencies.
	 * @param assistants the interface metadata resolved for the members, in member
	 * order.
	 */
	UpgradePlanItem(ItemId itemId, @Nullable String displayName, ArtifactVersion to, boolean vulnerabilityFix,
			int vulnerabilityCount, CvssSeverity highestVulnerabilitySeverity, List<ItemDependency> members,
			List<InterfaceAssistant> assistants) {

		Assert.isTrue(!members.isEmpty(), "Upgrade Plan item requires members");
		Assert.isTrue(members.size() == assistants.size(),
				"Each Upgrade Plan member requires interface metadata");

		this.itemId = itemId;
		this.members = members;
		this.to = to;

		ArtifactVersion from = null;
		for (Dependency member : members) {
			ArtifactVersion current = member.getCurrentVersion();
			if (from == null || from.isNewer(current)) {
				from = current;
			}
		}

		this.from = from == null ? to : from;
		this.fromVersion = from == null ? "" : from.toDocumentationString();
		this.toVersion = to.toDocumentationString();
		ItemDependency dependency = members.getFirst();
		InterfaceAssistant assistant = assistants.getFirst();
		this.displayName = StringUtils.hasText(displayName) ? displayName
				: assistant.getDisplayName(dependency.getArtifactId());
		this.vulnerabilityFix = vulnerabilityFix;
		this.vulnerabilityCount = vulnerabilityCount;
		this.highestVulnerabilitySeverity = highestVulnerabilitySeverity;
		this.attentionLevel = determineAttentionLevel();
		this.attentionBadge = createAttentionBadge();
		this.icon = assistant.getTableIcon(dependency);
	}

	/**
	 * Badge for the item's attention level.
	 */
	private Badge createAttentionBadge() {

		return switch (this.getAttentionLevel()) {
		case VULNERABILITY_FIX -> new Badge(MessageBundle.message("plan.badge.cve"), Badge.ColorType.GREEN,
				MessageBundle.message("plan.badge.cve.tooltip", vulnerabilityCount,
						highestVulnerabilitySeverity.getLabel()));
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

	private AttentionLevel determineAttentionLevel() {
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

	public List<ItemDependency> getMembers() {
		return members;
	}

	public boolean isGroup() {
		return members.size() > 1;
	}

	public AttentionLevel getAttentionLevel() {
		return attentionLevel;
	}

	public Icon getIcon() {
		return icon;
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

	public String getFromVersionString() {
		return fromVersion;
	}

	public ArtifactVersion getToVersion() {
		return to;
	}

	public String getToVersionString() {
		return toVersion;
	}

	/**
	 * Return the bare property names backing the persisted member versions.
	 *
	 * @return the property names in member and source order.
	 */
	Set<String> getVersionPropertyNames() {

		Set<String> names = new LinkedHashSet<>();
		for (Dependency member : members) {
			for (VersionSource source : member.getVersionSources()) {
				if (source instanceof VersionSource.VersionProperty property) {
					names.add(property.getProperty());
				}
			}
		}
		return names;
	}

	/**
	 * Derive the Dependency Site query for all persisted members.
	 *
	 * @return the query covering every member artifact and version property.
	 */
	DependencySiteQuery toQuery() {
		return DependencySiteQuery.create(builder -> {
			for (Dependency member : members) {
				builder.artifact(member.getArtifactId());
			}
			builder.versionProperties(getVersionPropertyNames());
		});
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

		List<DependencyUpdate> updates = new ArrayList<>(members.size());
		for (ItemDependency member : members) {
			if (member.isImplicit()) {
				continue;
			}
			updates.add(DependencyUpdate.from(member, getToVersion()));
		}
		return updates;
	}

	@Override
	public Iterator<ItemDependency> iterator() {
		return members.iterator();
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

		DOWNGRADE,

		MAJOR,

		MINOR,

		PATCH,

	}

}
