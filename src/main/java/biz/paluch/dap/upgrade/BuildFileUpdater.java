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

package biz.paluch.dap.upgrade;

import biz.paluch.dap.support.DependencyUpdates;
import biz.paluch.dap.support.FileScope;

/**
 * Strategy interface for writing dependency updates back into build files.
 *
 * @author Mark Paluch
 */
public interface BuildFileUpdater {



	/**
	 * Apply the given updates to every build file in {@code files}.
	 *
	 * <p>The same {@code updates} are routed to each file. Updates that do not
	 * match a file are skipped by the underlying writer.
	 *
	 * @param files the build files to write. An empty collection is a no-op.
	 * @param updates the updates to apply to each file. An empty list is a no-op.
	 */
	void updateBuildFiles(FileScope files, DependencyUpdates updates);

}
