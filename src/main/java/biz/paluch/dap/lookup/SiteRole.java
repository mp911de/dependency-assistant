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

package biz.paluch.dap.lookup;

/**
 * {@link DependencySiteSearchHit Search hit} role.
 *
 * <p>The role classifies a located PSI element by what it contributes to a
 * dependency's version: the place the version value is written, or a place the
 * version is referenced indirectly. The owning build ecosystem assigns the role
 * because only it knows, for example, that a version-catalog {@code [versions]}
 * entry is a definition and a {@code version.ref} is a usage.
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
	DECLARATION,

	/**
	 * Where the version is referenced indirectly rather than written inline: a
	 * {@code version.ref} or version-catalog accessor (for example
	 * {@code libs.spring.core}), a Maven {@code ${prop}}, or a {@code $prop}
	 * interpolation in a build script.
	 */
	VERSION_USAGE

}
