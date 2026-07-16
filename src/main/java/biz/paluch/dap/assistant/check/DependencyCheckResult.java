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
import java.util.stream.Stream;

import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.util.Sequence;

/**
 * Result of a dependency check over one or more build files.
 *
 * <p>The result transports the sorted upgrade aggregates and the build-file
 * scope to the review dialog. Each {@link DependencyUpgradeCandidate} already
 * owns its assistant and declared versions, so grouping and row assembly remain
 * review concerns without side maps.
 *
 * @author Mark Paluch
 * @param upgrades the upgrades that can be offered to the user.
 * @param scope the build-file scope included in the dependency check.
 * @param errors non-fatal release lookup errors; empty when all lookups
 * succeeded.
 */
public record DependencyCheckResult(List<DependencyUpgradeCandidate> upgrades, FileScope scope,
		List<String> errors) implements Sequence<DependencyUpgradeCandidate> {

	@Override
	public Iterator<DependencyUpgradeCandidate> iterator() {
		return upgrades.iterator();
	}

	@Override
	public Stream<DependencyUpgradeCandidate> stream() {
		return upgrades.stream();
	}

	@Override
	public boolean isEmpty() {
		return upgrades.isEmpty();
	}

}
