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

package biz.paluch.dap.plan;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import biz.paluch.dap.extension.IdeaProjectTests;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradePlanPanel}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpgradePlanPanelUnitTests {

	@Test
	void retainsFilesBelowAnyProjectRoot(@TempDir Path temp) throws IOException {

		WriteAction.runAndWait(() -> {

			VirtualFile root = LocalFileSystem.getInstance()
					.refreshAndFindFileByNioFile(temp);

			VirtualFile firstRoot = root.createChildDirectory(this, "first");
			VirtualFile secondRoot = root.createChildDirectory(this, "second");
			VirtualFile outsideRoot = root.createChildDirectory(this, "outside");

			VirtualFile first = firstRoot.createChildData(this, "pom.xml");
			VirtualFile second = secondRoot.createChildData(this, "build.gradle");
			VirtualFile outside = outsideRoot.createChildData(this, "package.json");

			UpgradePlanState.Content content = new UpgradePlanState.Content();
			content.setAffectedFiles(first.getPath(), outside.getPath(), second.getPath(),
					firstRoot.getPath() + "/missing.xml", "pom.xml");

			List<String> affectedFiles = UpgradePlanPanel.filterAffectedFiles(content,
					new EmptyProgressIndicator(ModalityState.NON_MODAL), List.of(firstRoot, secondRoot));

			assertThat(affectedFiles).containsExactly(first.getPath(), second.getPath());
		});
	}

}
