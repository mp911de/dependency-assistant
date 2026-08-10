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

package biz.paluch.dap.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Tests for {@link PsiFileCache}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class PsiFileCacheTests {

	@Test
	@ProjectFile(name = "first.xml", content = "<first/>")
	@ProjectFile(name = "other.xml", content = "<other/>")
	void invalidatesOnlyWhenOwningFileChanges(Project project,
			@ProjectFile("first.xml") XmlFile first,
			@ProjectFile("other.xml") XmlFile other) {

		AtomicInteger computations = new AtomicInteger();
		Function<XmlFile, Integer> provider = it -> computations.incrementAndGet();

		assertThat(PsiFileCache.get(first, provider)).isEqualTo(1);
		assertThat(PsiFileCache.get(first, provider)).isEqualTo(1);

		WriteCommandAction.runWriteCommandAction(project,
				() -> {
					other.getRootTag().setAttribute("value", "changed");
				});

		assertThat(PsiFileCache.get(first, provider)).isEqualTo(1);

		WriteCommandAction.runWriteCommandAction(project,
				() -> {
					first.getRootTag().setAttribute("value", "changed");
				});

		assertThat(PsiFileCache.get(first, provider)).isEqualTo(2);
	}

	@Test
	@ProjectFile(name = "sample.xml", content = "<sample/>")
	void isolatesComputationsOnTheSameFile(PsiFile file) {

		Function<PsiFile, String> name = PsiFile::getName;
		Function<PsiFile, Integer> length = PsiFile::getTextLength;

		assertThat(PsiFileCache.get(file, name)).isEqualTo("sample.xml");
		assertThat(PsiFileCache.get(file, length)).isEqualTo(9);
	}

	@Test
	@ProjectFile(name = "first.xml", content = "<first/>")
	@ProjectFile(name = "other.xml", content = "<other/>")
	void invalidatesProjectCacheOnAnyPsiChange(Project project,
			@ProjectFile("first.xml") XmlFile first,
			@ProjectFile("other.xml") XmlFile other) {

		AtomicInteger computations = new AtomicInteger();
		Function<XmlFile, Integer> provider = it -> computations.incrementAndGet();

		assertThat(PsiFileCache.withProjectRoot(first, provider)).isEqualTo(1);
		assertThat(PsiFileCache.withProjectRoot(first, provider)).isEqualTo(1);

		WriteCommandAction.runWriteCommandAction(project, () -> {
			other.getRootTag().setAttribute("value", "changed");
		});

		assertThat(PsiFileCache.withProjectRoot(first, provider)).isEqualTo(2);
	}

}
