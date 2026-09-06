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

package biz.paluch.dap.assertions;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.assertions.CodeInsightAssertions.CodeInsightFixtureAssert;
import biz.paluch.dap.checker.CheckRequest;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

/**
 * Shared entry point for standard AssertJ and project-specific assertions.
 *
 * @author Mark Paluch
 */
public class Assertions extends org.assertj.core.api.Assertions {

	public static ArtifactVersionAssert assertThatVersion(String version) {
		return assertThat(ArtifactVersion.of(version));
	}

	public static ArtifactVersionAssert assertThat(ArtifactVersion version) {
		return new ArtifactVersionAssert(version);
	}

	public static CodeInsightFixtureAssert assertThat(CodeInsightTestFixture fixture) {
		return new CodeInsightFixtureAssert(fixture);
	}

	public static DependencyCollectorAssert assertThat(DependencyCollector collector) {
		return new DependencyCollectorAssert(collector);
	}

	public static CheckRequestAssert assertThat(CheckRequest request) {
		return new CheckRequestAssert(request);
	}

	public static ReleasesAssert assertThat(Releases releases) {
		return new ReleasesAssert(releases);
	}

	public static ReleaseAssert assertThat(Release release) {
		return new ReleaseAssert(release);
	}

	public static PsiElementAssert assertThat(PsiElement element) {
		return new PsiElementAssert(element);
	}

	public static PsiElementAssert assertThat(PsiFile file) {
		return new PsiElementAssert(file);
	}

}
