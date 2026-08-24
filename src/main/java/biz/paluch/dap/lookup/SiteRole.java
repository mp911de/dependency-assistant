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

package biz.paluch.dap.lookup;

import java.util.function.Supplier;

import biz.paluch.dap.util.MessageBundle;
import org.jetbrains.annotations.Nls;

/**
 * Role assigned to a {@link DependencySiteSearchHit Dependency Site}.
 *
 * <p>A role distinguishes the location where a version value is written from a
 * location that refers to the version indirectly. The search producer assigns
 * the role from build-tool-specific syntax.
 *
 * @author Mark Paluch
 * @see DependencySiteSearchHit
 */
public enum SiteRole {

	/**
	 * Where the version value is written: a version-property definition (a
	 * {@code [versions]} entry, a {@code gradle.properties} or {@code extra} entry,
	 * or a Maven {@code <properties>} entry) or an inline version literal.
	 */
	DECLARATION(MessageBundle.lazyMessage("dialog.findSites.role.DECLARATION")),

	/**
	 * Where the version is referenced indirectly rather than written inline: a
	 * {@code version.ref} or version-catalog accessor (for example
	 * {@code libs.spring.core}), a Maven {@code ${prop}}, or a {@code $prop}
	 * interpolation in a build script.
	 */
	VERSION_USAGE(MessageBundle.lazyMessage("dialog.findSites.role.VERSION_USAGE"));

	private final Supplier<String> message;

	SiteRole(Supplier<String> message) {
		this.message = message;
	}

	/**
	 * Return the localized display name of this role.
	 *
	 * @return the localized role name.
	 */
	public @Nls String getName() {
		return message.get();
	}

}
