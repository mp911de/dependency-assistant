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

/**
 * Forward-only plan revision carrying the logical plan state represented by the
 * revision. Undo and redo restore a prior logical state under a fresh revision,
 * so optimistic-concurrency tokens are never reused.
 *
 * @author Mark Paluch
 */
class PlanGeneration {

	final long revision;

	final long state;

	PlanGeneration(long revision, long state) {
		this.revision = revision;
		this.state = state;
	}

	boolean hasState(PlanGeneration generation) {
		return state == generation.state;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof PlanGeneration that)) {
			return false;
		}
		return revision == that.revision;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(revision);
	}

	@Override
	public String toString() {
		return revision + "[" + state + "]";
	}

}
