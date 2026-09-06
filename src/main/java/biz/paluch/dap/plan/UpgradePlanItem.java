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
import com.intellij.openapi.actionSystem.DataKey;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Materialized plan item with captured member facts and a pinned target.
 * <p>Equality uses {@link ItemId}. Display name and ticket association may
 * change in place without changing identity.
 *
 * @author Mark Paluch
 */
class UpgradePlanItem implements Sequence<ItemDependency> {

	/**
	 * Rename target published only for one selected top-level row while the plan is
	 * idle.
	 */
	static final DataKey<UpgradePlanItem> RENAME_TARGET = DataKey
			.create("DependencyAssistant.UpgradePlan.RenameTarget");

	private final ItemId itemId;

	private final List<ItemDependency> members;

	private String displayName;

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
	 * Create an item from captured facts.
	 * @param displayName captured name, or blank to derive it from the first
	 * member.
	 * @param members non-empty member list, retained by this item.
	 * @param assistants one interface assistant per member, in member order.
	 * @throws IllegalArgumentException if members are empty or assistant counts
	 * differ.
	 */
	UpgradePlanItem(ItemId itemId, String displayName, ArtifactVersion to,
			boolean vulnerabilityFix, int vulnerabilityCount,
			CvssSeverity highestSeverity, List<ItemDependency> members,
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
				: dependency.getPackageSystem().getArtifactId(dependency.getArtifactId());
		this.vulnerabilityFix = vulnerabilityFix;
		this.vulnerabilityCount = vulnerabilityCount;
		this.highestVulnerabilitySeverity = highestSeverity;
		this.attentionLevel = determineAttentionLevel();
		this.attentionBadge = createAttentionBadge();
		this.icon = assistant.getTableIcon(dependency);
	}

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

	/**
	 * Return members in captured order, including implicit members. Treat the live
	 * list as read-only.
	 */
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
	 * Replace the display name with an already sanitized, non-blank name.
	 */
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	/**
	 * Return the oldest current version among members.
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
	 * Return bare property names in member and source order.
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
	 * Create a dependency-site query covering every member and version property.
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
	 * Replace the ticket link, or clear it with {@literal null}.
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
	 * Create updates for explicit members. Implicit members share another member's
	 * version-property write.
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
	 * Review attention in descending priority.
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
