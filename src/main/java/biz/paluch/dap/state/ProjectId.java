/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a snapshot of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package biz.paluch.dap.state;

import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Build-tool-agnostic identity for dependency state owned by a project context.
 *
 * <p>The {@code groupId} and {@code artifactId} components are logical
 * namespace/name slots, not necessarily Maven coordinates. Maven can map them
 * directly from its model, Gradle derives them from imported project metadata,
 * NPM uses an ecosystem namespace with the {@code package.json} name, and
 * GitHub-oriented contexts use repository coordinates when available.
 *
 * <p>The optional {@code buildFile} component scopes the identity to a concrete
 * descriptor when one logical IDE project can contain multiple independently
 * tracked dependency contexts. Gradle build files, {@code package.json} files,
 * and GitHub Actions workflow files use this component to keep dependency state
 * isolated per descriptor. Build models that already provide a sufficiently
 * unique project identity may leave it {@literal null}.
 *
 * @param groupId logical namespace of the project context.
 * @param artifactId logical name of the project context.
 * @param buildFile optional descriptor path used to disambiguate project
 * contexts.
 * @author Mark Paluch
 */
public record ProjectId(@Nullable String groupId, @Nullable String artifactId, @Nullable String buildFile) {

	/**
	 * Create a project identity that is fully represented by its logical namespace
	 * and name.
	 * @param groupId logical namespace of the project context.
	 * @param artifactId logical name of the project context.
	 * @return the project identity.
	 */
	public static ProjectId of(String groupId, String artifactId) {
		return new ProjectId(groupId, artifactId, null);
	}

	/**
	 * Create a project identity scoped to a concrete build or package descriptor.
	 * @param groupId logical namespace of the project context.
	 * @param artifactId logical name of the project context.
	 * @param buildFile descriptor path used to disambiguate project contexts.
	 * @return the project identity.
	 */
	public static ProjectId of(String groupId, String artifactId, String buildFile) {
		return new ProjectId(groupId, artifactId, buildFile);
	}

	/**
	 * Create a project identity scoped to a concrete build or package descriptor.
	 * @param buildFile descriptor path used to disambiguate project contexts.
	 * @return the project identity.
	 */
	public static ProjectId of(VirtualFile buildFile) {
		return new ProjectId(null, null, buildFile.getPath());
	}

}
