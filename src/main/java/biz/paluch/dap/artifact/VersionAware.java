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

package biz.paluch.dap.artifact;

/**
 * Contract for domain objects that expose an {@link ArtifactVersion}.
 *
 * <p>
 * Use this interface when version comparison or presentation code can work
 * with releases and release candidates without depending on their concrete
 * container type.
 *
 * @author Mark Paluch
 * @see ArtifactVersion
 * @see Release
 * @see ArtifactRelease
 */
public interface VersionAware {

	/**
	 * Return the artifact version exposed by this object.
	 *
	 * @return the artifact version; guaranteed to be not {@literal null}.
	 */
	ArtifactVersion getVersion();

}
