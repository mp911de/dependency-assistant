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
import java.util.List;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.plan.UpgradePlanState.DeclarationSourceState;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.plan.UpgradePlanState.Member;
import biz.paluch.dap.plan.UpgradePlanState.VersionSourceState;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Loader that validates persisted Upgrade Plan facts and resolves the interface
 * metadata needed by {@link UpgradePlanItem}.
 *
 * <p>An invalid target version, invalid member, or unavailable assistant yields
 * no materialized item. The persisted item remains in
 * {@link UpgradePlanState.Content} so a later reload can resolve it. Ticket
 * materialization is delegated to the persisted ticket value.
 *
 * @author Mark Paluch
 */
class UpgradePlanLoader {

	private final List<? extends DependencyAssistant> assistants;

	private final @Nullable TicketSystem ticketSystem;

	private final GitVersionResolver gitVersionResolver;

	UpgradePlanLoader(Project project, @Nullable TicketSystem ticketSystem) {
		this(DependencyAssistantDispatcher.findAll(project), ticketSystem, StateService.getInstance(project)
				.getCache());
	}

	UpgradePlanLoader(List<? extends DependencyAssistant> assistants,
			@Nullable TicketSystem ticketSystem, Cache cache) {
		this.assistants = assistants;
		this.ticketSystem = ticketSystem;
		this.gitVersionResolver = new GitVersionResolver(cache);
	}

	/**
	 * Materialize the given persisted item.
	 *
	 * @param item the persisted item to validate and materialize.
	 * @return the materialized item, or {@literal null} when its target version or
	 * a member is invalid, or an assistant is unavailable.
	 */
	public @Nullable UpgradePlanItem create(Item item) {

		if (ArtifactVersion.from(item.getToVersion()).isEmpty() || item.getMembers().isEmpty()) {
			return null;
		}

		List<InterfaceAssistant> resolvedAssistants = new ArrayList<>(item.getMembers().size());
		List<ItemDependency> dependencies = new ArrayList<>(item.getMembers().size());
		for (Member member : item.getMembers()) {
			if (!isValid(member)) {
				return null;
			}

			InterfaceAssistant assistant = resolveAssistant(member.assistant);
			if (assistant == null) {
				return null;
			}
			resolvedAssistants.add(assistant);
			dependencies.add(toDependency(member));
		}

		ArtifactVersion version = ArtifactVersion.of(item.getToVersion());
		for (Member member : item.getMembers()) {

			Versioned versioned = gitVersionResolver.resolveStrict(member.getPackageIdentity()
					.getArtifactId(), item.getToVersion());
			if (versioned.isVersioned()) {
				version = versioned.getVersion();
				break;
			}
		}

		UpgradePlanItem planItem = new UpgradePlanItem(item.getId(), item.getDisplayName(),
				version, item.isVulnerabilityFix(), item.getVulnerabilityCount(),
				item.getHighestVulnerabilitySeverity(), dependencies, resolvedAssistants);
		UpgradePlanState.Ticket ticket = item.getTicket();
		if (ticket != null) {
			planItem.setTicket(ticket.toUpgradeTicket(ticketSystem));
		}

		return planItem;
	}

	private static boolean isValid(Member member) {

		if (StringUtils.isEmpty(member.groupId) || StringUtils.isEmpty(member.artifactId)
				|| ArtifactVersion.from(member.fromVersion).isEmpty() || StringUtils.isEmpty(member.assistant)) {
			return false;
		}

		for (DeclarationSourceState source : member.declarationSources) {
			if (source == null || source.kind == null) {
				return false;
			}
		}
		for (VersionSourceState source : member.versionSources) {
			if (source == null || source.kind == null) {
				return false;
			}
		}

		return true;
	}

	private static ItemDependency toDependency(Member member) {

		ArtifactVersion fromVersion = ArtifactVersion.of(member.fromVersion);
		ItemDependency dependency = new ItemDependency(member.getPackageIdentity(), fromVersion, member.implicit);

		if (member.declarationSources.isEmpty()) {
			dependency.addDeclarationSource(DeclarationSource.dependency());
		} else {
			for (DeclarationSourceState source : member.declarationSources) {
				dependency.addDeclarationSource(source.toDeclarationSource());
			}
		}

		if (member.versionSources.isEmpty() && !member.implicit) {
			dependency.addVersionSource(VersionSource.declared(member.fromVersion));
		} else {
			for (VersionSourceState source : member.versionSources) {
				VersionSource versionSource = source.toVersionSource();
				if (versionSource.isDefined()) {
					dependency.addVersionSource(versionSource);
				}
			}
		}

		return dependency;
	}

	private @Nullable InterfaceAssistant resolveAssistant(String id) {

		for (DependencyAssistant assistant : assistants) {
			if (assistant.getId().equals(id)) {
				return assistant.getInterfaceAssistant();
			}
		}

		return null;
	}

}
