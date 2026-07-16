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

package biz.paluch.dap.assistant.check;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.upgrade.UpgradeDecision;
import biz.paluch.dap.util.Sequence;
import com.intellij.openapi.vfs.VirtualFile;

import org.springframework.util.Assert;

/**
 * Result of a dependency check over one or more build files.
 *
 * <p>The result transports sorted domain decisions and independent presentation
 * facts to the review dialog. Grouping and row assembly remain review concerns.
 *
 * @author Mark Paluch
 * @param decisions the upgrade decisions that can be offered to the user.
 * @param assistants the assistant metadata keyed by decision identity.
 * @param declaredVersions the declared version facts keyed by decision
 * identity.
 * @param files the build files included in the dependency check.
 * @param errors non-fatal release lookup errors; empty when all lookups
 * succeeded.
 */
public record DependencyUpgradeCandidates(List<UpgradeDecision> decisions,
		Map<UpgradeDecision, InterfaceAssistant> assistants,
		Map<UpgradeDecision, DeclaredVersions> declaredVersions,
		List<VirtualFile> files,
		List<String> errors) implements Sequence<UpgradeDecision> {

	public DependencyUpgradeCandidates {
		decisions = List.copyOf(decisions);
		assistants = Map.copyOf(assistants);
		declaredVersions = Map.copyOf(declaredVersions);
		files = List.copyOf(files);
		errors = List.copyOf(errors);

		Set<UpgradeDecision> decisionSet = Set.copyOf(decisions);
		Assert.isTrue(decisionSet.size() == decisions.size(), "Upgrade decisions must be unique");
		Assert.isTrue(assistants.keySet().equals(decisionSet),
				"Each upgrade decision requires exactly one interface assistant");
		Assert.isTrue(declaredVersions.keySet().equals(decisionSet),
				"Each upgrade decision requires exactly one declared-version result");
	}

	public UpgradeDecision getFirst() {
		return decisions.getFirst();
	}

	public UpgradeDecision getLast() {
		return decisions.getLast();
	}

	@Override
	public Iterator<UpgradeDecision> iterator() {
		return decisions.iterator();
	}

	@Override
	public Stream<UpgradeDecision> stream() {
		return decisions.stream();
	}

	@Override
	public boolean isEmpty() {
		return decisions.isEmpty();
	}

}
