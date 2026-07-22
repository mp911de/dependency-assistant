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

package biz.paluch.dap.fixtures;

import java.util.Collection;
import java.util.List;

import biz.paluch.dap.assistant.check.DeclarationSite;
import biz.paluch.dap.assistant.check.DeclaredVersions;

/**
 * Test factory for {@link DeclaredVersions} without git-ref resolution and
 * without a project for location rendering.
 *
 * @author Mark Paluch
 */
public class TestDeclaredVersions {

	private TestDeclaredVersions() {
	}

	/**
	 * Create {@link DeclaredVersions} from the given sites, resolving no git refs
	 * and rendering absolute file locations.
	 */
	public static DeclaredVersions from(DeclarationSite... sites) {
		return from(List.of(sites));
	}

	/**
	 * Create {@link DeclaredVersions} from the given sites, resolving no git refs
	 * and rendering absolute file locations.
	 */
	public static DeclaredVersions from(Collection<DeclarationSite> sites) {
		return DeclaredVersions.from(sites, ref -> null, null);
	}

}
