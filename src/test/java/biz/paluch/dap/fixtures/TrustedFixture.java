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

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.project.Project;
import org.junit.function.ThrowingRunnable;

/**
 * Runs test code with a temporary project trust state.
 * <p>The previous state is restored even on failure. Thrown exceptions and
 * errors are wrapped in {@link RuntimeException}.
 *
 * @author Mark Paluch
 */
public class TrustedFixture {

	private final Project project;

	private TrustedFixture(Project project) {
		this.project = project;
	}

	public static TrustedFixture of(Project project) {
		return new TrustedFixture(project);
	}

	public void runTrusted(ThrowingRunnable runnable) {

		boolean before = TrustedProjects.isProjectTrusted(project);
		TrustedProjects.setProjectTrusted(project, true);
		try {
			runnable.run();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		} finally {
			TrustedProjects.setProjectTrusted(project, before);
		}
	}

	public void runUntrusted(ThrowingRunnable runnable) {

		boolean before = TrustedProjects.isProjectTrusted(project);
		TrustedProjects.setProjectTrusted(project, false);
		try {
			runnable.run();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		} finally {
			TrustedProjects.setProjectTrusted(project, before);
		}
	}

}
