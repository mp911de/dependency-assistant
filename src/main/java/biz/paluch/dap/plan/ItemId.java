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

import java.util.Set;

import biz.paluch.dap.artifact.PackageSystem;

import org.springframework.util.ObjectUtils;

/**
 * Semantic identity of an Upgrade Plan item, derived from the unordered set of
 * its member coordinates, package systems, current versions, and owning
 * assistants.
 *
 * <p>The display name, target version, ticket, declaration structure, and
 * implicit-member flag do not participate in identity.
 *
 * @author Mark Paluch
 */
class ItemId {

	private final Set<MemberKey> members;

	ItemId(Set<MemberKey> members) {
		this.members = members;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ItemId itemId)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(members, itemId.members);
	}

	@Override
	public int hashCode() {
		return members.hashCode();
	}

	@Override
	public String toString() {
		return members.toString();
	}

	record MemberKey(String groupId, String artifactId, PackageSystem packageSystem, String version, String assistant) {
	}

}
