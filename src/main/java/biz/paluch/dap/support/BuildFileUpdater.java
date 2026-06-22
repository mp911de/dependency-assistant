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

package biz.paluch.dap.support;

import java.util.Collection;
import java.util.List;

import biz.paluch.dap.artifact.DependencyUpdate;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Strategy interface for writing dependency updates back into build files.
 *
 * @author Mark Paluch
 */
public interface BuildFileUpdater {

	/**
	 * Apply the given updates to a single build file inside one undoable command.
	 *
	 * @param file the build file to write.
	 * @param updates the updates to apply; an empty list is a no-op.
	 */
	void updateBuildFile(VirtualFile file, List<DependencyUpdate> updates);

	/**
	 * Apply the given updates to every build file in {@code files} inside one
	 * undoable command.
	 *
	 * <p>The same {@code updates} are routed to each file; updates that do not
	 * match a file are skipped by the underlying writer. Use when a single chosen
	 * target fans out to several files that share one writer.
	 *
	 * @param files the build files to write; an empty collection is a no-op.
	 * @param updates the updates to apply to each file; an empty list is a no-op.
	 */
	void updateBuildFiles(Collection<VirtualFile> files, List<DependencyUpdate> updates);

}
