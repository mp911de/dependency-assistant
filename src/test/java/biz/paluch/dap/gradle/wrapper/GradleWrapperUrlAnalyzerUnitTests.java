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

package biz.paluch.dap.gradle.wrapper;

import biz.paluch.dap.extension.StringTest;
import biz.paluch.dap.gradle.wrapper.GradleWrapperUrlProblem.CredentialsInUrl;
import biz.paluch.dap.gradle.wrapper.GradleWrapperUrlProblem.InvalidUrl;
import biz.paluch.dap.gradle.wrapper.GradleWrapperUrlProblem.MalformedFileName;
import biz.paluch.dap.gradle.wrapper.GradleWrapperUrlProblem.UnknownArtifact;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GradleWrapperUrlAnalyzer}.
 *
 * @author Mark Paluch
 */
class GradleWrapperUrlAnalyzerUnitTests {

	@StringTest("https://services.gradle.org/distributions/gradle-8.14.3-bin.zip")
	void canonicalBinUrlProducesNoProblems(String url) {
		assertThat(analyze(url)).isEmpty();
	}

	@StringTest("https://services.gradle.org/distributions/gradle-8.14.3-all.zip")
	void canonicalAllUrlProducesNoProblems(String url) {
		assertThat(analyze(url)).isEmpty();
	}

	@StringTest("https://alice:secret@services.gradle.org/distributions/gradle-8.14.3-bin.zip")
	void emitsCredentialsInUrl(String url) {
		assertThat(analyze(url)).containsExactly(new CredentialsInUrl());
	}

	@StringTest("not a url at all")
	void emitsInvalidUrl(String url) {
		assertThat(analyze(url)).containsExactly(new InvalidUrl());
	}

	@StringTest("https://services.gradle.org/distributions/wrapper-8.14.3-bin.zip")
	void emitsUnknownArtifact(String url) {
		assertThat(analyze(url)).containsExactly(new UnknownArtifact("wrapper"));
	}

	@StringTest("https://services.gradle.org/distributions/gradle-8.14.3.zip")
	void emitsMalformedFileName(String url) {
		assertThat(analyze(url)).containsExactly(new MalformedFileName("gradle-8.14.3.zip"));
	}

	@StringTest("https://mirror.example.com/custom/gradle-8.14.3-bin.zip")
	void customHostWithCanonicalFileNameProducesNoProblems(String url) {
		assertThat(analyze(url)).isEmpty();
	}

	@StringTest("https://services.gradle.org/distributions/gradle-8.___IntellijIdeaRulezzz___-bin.zip")
	void skipsCompletionPlaceholder(String url) {
		assertThat(analyze(url)).isEmpty();
	}

	private static java.util.List<GradleWrapperUrlProblem> analyze(String url) {
		return GradleWrapperUrlAnalyzer.analyze(url, url);
	}

}
