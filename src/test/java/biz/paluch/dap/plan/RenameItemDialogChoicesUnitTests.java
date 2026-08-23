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

import java.util.List;

import biz.paluch.dap.extension.IdeaProjectTests;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link RenameItemDialog} choices; the dialog is built but
 * never shown.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class RenameItemDialogChoicesUnitTests {

	@Test
	void rememberNameFollowsPreselection(Project project) {

		RenameItemDialog dialog = new RenameItemDialog(project, "spring-core", List.of("spring-core"));
		try {
			dialog.setRememberName(false);
			assertThat(dialog.isRememberName()).isFalse();

			dialog.setRememberName(true);
			assertThat(dialog.isRememberName()).isTrue();
		} finally {
			Disposer.dispose(dialog.getDisposable());
		}
	}

	@Test
	void updateDependencyfileIsOffWhileUnavailable(Project project) {

		RenameItemDialog dialog = new RenameItemDialog(project, "spring-core", List.of("spring-core"));
		try {
			dialog.setUpdateDependencyfile(false, true);
			assertThat(dialog.isUpdateDependencyfile()).isFalse();

			dialog.setUpdateDependencyfile(true, true);
			assertThat(dialog.isUpdateDependencyfile()).isTrue();

			dialog.setUpdateDependencyfile(true, false);
			assertThat(dialog.isUpdateDependencyfile()).isFalse();
		} finally {
			Disposer.dispose(dialog.getDisposable());
		}
	}

}
