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

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.assistant.check.DeclaredVersions;
import biz.paluch.dap.assistant.check.DependencyUpdateCandidate;
import biz.paluch.dap.assistant.check.UpgradeCandidate;
import biz.paluch.dap.assistant.check.UpgradeGroup;
import biz.paluch.dap.assistant.check.UpgradeGroups;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.plan.UpgradePlanState.Member;
import biz.paluch.dap.plan.UpgradePlanState.VersionSourceState;
import biz.paluch.dap.rule.BranchSource;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleService;
import biz.paluch.dap.rule.ResolutionContext;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Loader for {@link UpgradePlanItem} from a serialized {@link Item}.
 *
 * @author Mark Paluch
 */
class UpgradePlanLoader {

	private final List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll();

	private final StateService stateService;

	private final @Nullable TicketSystem ticketSystem;

	private final Cache cache;

	private final DependencyRuleService ruleService;

	private final BranchSource branchSource;

	UpgradePlanLoader(Project project, FileScope files, @Nullable TicketSystem ticketSystem) {

		this.stateService = StateService.getInstance(project);
		this.ticketSystem = ticketSystem;
		this.cache = stateService.getCache();
		this.ruleService = DependencyRuleService.getInstance(project);
		this.branchSource = BranchSource.of(files.stream().findFirst().orElse(null));
	}

	public @Nullable UpgradePlanItem create(Item item) {

		if (StringUtils.isEmpty(item.getToVersion())) {
			return null;
		}

		List<UpgradeCandidate> members = new ArrayList<>();
		for (Member member : item.getMembers()) {
			UpgradeCandidate candidate = loadCandidate(member);
			if (candidate == null) {
				return null;
			}
			members.add(candidate);
		}

		if (members.isEmpty()) {
			return null;
		}

		UpgradeCandidate candidate;
		if (members.size() == 1) {
			candidate = members.getFirst();
		} else {
			// group through the dialog's own logic so the label and member label match;
			// force a group when the members no longer satisfy the grouping criteria,
			// restoring the captured derived name for inferred groups
			List<UpgradeCandidate> grouped = UpgradeGroups.of(members).toList();
			if (grouped.size() == 1) {
				candidate = grouped.getFirst();
			} else {
				candidate = item.getDisplayName() != null ? UpgradeGroup.inferred(members, item.getDisplayName())
						: UpgradeGroup.of(members);
			}
		}

		UpgradePlanItem view = new UpgradePlanItem(item.getId(), candidate, ArtifactVersion.of(item.getToVersion()));

		UpgradePlanState.Ticket ticket = item.getTicket();
		if (ticket != null) {
			view.setTicket(ticket.toUpgradeTicket(ticketSystem));
		}

		return view;
	}

	private @Nullable UpgradeCandidate loadCandidate(Member member) {

		if (StringUtils.isEmpty(member.groupId) || StringUtils.isEmpty(member.artifactId)
				|| StringUtils.isEmpty(member.fromVersion)) {
			return null;
		}

		InterfaceAssistant assistant = resolveAssistant(member.assistant);
		if (assistant == null) {
			return null;
		}

		ArtifactId artifactId = ArtifactId.of(member.groupId, member.artifactId);
		ArtifactVersion fromVersion = ArtifactVersion.of(member.fromVersion);

		Dependency dependency = new Dependency(artifactId, fromVersion);
		if (member.declarationSources.isEmpty()) {
			dependency.addDeclarationSource(DeclarationSource.dependency());
		} else {
			member.declarationSources
					.forEach(source -> dependency.addDeclarationSource(source.toDeclarationSource()));
		}
		if (member.versionSources.isEmpty()) {
			dependency.addVersionSource(VersionSource.declared(member.fromVersion));
		} else {
			for (VersionSourceState source : member.versionSources) {
				VersionSource versionSource = source.toVersionSource();
				if (versionSource.isDefined()) {
					dependency.addVersionSource(versionSource);
				}
			}
		}

		Releases releases = cache.getReleases(artifactId);
		VulnerabilityRepository vulnerabilities = version -> stateService.getVulnerabilities(artifactId, version);
		DependencyRule rule = ruleService
				.resolve(ResolutionContext.forAggregate(dependency, branchSource, Versioned.unversioned()));

		DependencyUpdateCandidate candidate = new DependencyUpdateCandidate(dependency, releases, vulnerabilities,
				rule);
		return new UpgradeCandidate(candidate, assistant, DeclaredVersions.of(fromVersion));
	}

	private @Nullable InterfaceAssistant resolveAssistant(@Nullable String assistantClassName) {

		if (assistantClassName == null) {
			return null;
		}

		for (DependencyAssistant assistant : assistants) {
			if (assistant.getInterfaceAssistant().getClass().getName()
					.equals(assistantClassName)) {
				return assistant.getInterfaceAssistant();
			}
		}

		return null;
	}

}
