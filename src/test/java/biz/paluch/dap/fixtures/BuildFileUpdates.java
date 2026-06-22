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

package biz.paluch.dap.fixtures;

import java.util.List;
import java.util.function.BiConsumer;

import biz.paluch.dap.assistant.BuildActionDelegate;
import biz.paluch.dap.support.DependencyUpdate;
import com.intellij.psi.PsiFile;

/**
 * Test support for driving a single {@link DependencyUpdate} through
 * {@link BuildActionDelegate} against a build file across the format-specific
 * update tests (Maven extensions, NPM, GitHub workflows, Antora playbooks).
 *
 * @author Mark Paluch
 */
public class BuildFileUpdates {

	/**
	 * Apply the given update to the file by wiring the format-specific update step
	 * into a {@link BuildActionDelegate} and running it over the file.
	 */
	public static void applyUpdate(PsiFile file, DependencyUpdate update,
			BiConsumer<PsiFile, List<DependencyUpdate>> updateStep) {

		new BuildActionDelegate(file.getProject(), updateStep)
				.updateBuildFile(file.getVirtualFile(), List.of(update));
	}

}
