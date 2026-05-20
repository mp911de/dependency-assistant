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

import biz.paluch.dap.artifact.Release;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GradleWrapperUrlRewriter}.
 *
 * @author Mark Paluch
 */
class GradleWrapperUrlRewriterUnitTests {

	@ParameterizedTest
	@CsvSource(textBlock = """
			https://alice:secret@services.gradle.org/distributions/gradle-8.14.3-bin.zip , https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			https://alice@services.gradle.org/distributions/gradle-8.14.3-bin.zip        , https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			https://alice:secret@services.gradle.org?mirror=1                            , https://services.gradle.org?mirror=1
			https://services.gradle.org/distributions/gradle-8.14.3-bin.zip              , https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			https://services.gradle.org?contact=ops@example.com                          , https://services.gradle.org?contact=ops@example.com
			""")
	void stripCredentialsRemovesUserInfoWhenPresent(String input, String expected) {
		assertThat(GradleWrapperUrlRewriter.stripCredentials(input)).isEqualTo(expected);
	}

	@Test
	void replaceVersionPreservesBinFlavor() {
		assertThat(GradleWrapperUrlRewriter.replaceVersion(
				"https://services.gradle.org/distributions/gradle-8.14.3-bin.zip", "9.5.1"))
						.isEqualTo("https://services.gradle.org/distributions/gradle-9.5.1-bin.zip");
	}

	@Test
	void replaceVersionPreservesAllFlavorAndCustomPrefix() {
		assertThat(GradleWrapperUrlRewriter.replaceVersion(
				"https://mirror.example.com/custom/gradle-8.14.3-all.zip", "9.5.1"))
						.isEqualTo("https://mirror.example.com/custom/gradle-9.5.1-all.zip");
	}

	@Test
	void replaceFileNameFallsBackToBinForMalformedFile() {
		assertThat(GradleWrapperUrlRewriter.replaceFileName(
				"https://services.gradle.org/distributions/gradle-8.14.3.zip", "8.14.3"))
						.isEqualTo("https://services.gradle.org/distributions/gradle-8.14.3-bin.zip");
	}

	@Test
	void replaceFileNamePreservesAllFlavorFromUnknownArtifact() {
		assertThat(GradleWrapperUrlRewriter.replaceFileName(
				"https://services.gradle.org/distributions/wrapper-8.14.3-all.zip", "8.14.3"))
						.isEqualTo("https://services.gradle.org/distributions/gradle-8.14.3-all.zip");
	}

	@Test
	void replaceFileNamePreservesUrlTail() {
		assertThat(GradleWrapperUrlRewriter.replaceFileName(
				"https://services.gradle.org/distributions/wrapper-8.14.3-all.zip?mirror=1#top", "8.14.3"))
						.isEqualTo("https://services.gradle.org/distributions/gradle-8.14.3-all.zip?mirror=1#top");
	}

	@Test
	void canonicalUrlUsesGradleServices() {
		assertThat(GradleWrapperUrlRewriter.canonicalUrl(Release.of("9.5.1")))
				.isEqualTo("https://services.gradle.org/distributions/gradle-9.5.1-bin.zip");
	}

}
