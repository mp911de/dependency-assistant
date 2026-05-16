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

package biz.paluch.dap.maven.wrapper;

import biz.paluch.dap.support.MessageBundle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MavenWrapperUrlFixes}.
 *
 * <p>The fix classes are {@code private static}, so the protected
 * {@code getPresentation(ActionContext, PropertyImpl)} hooks cannot be invoked
 * from a test without a real PSI fixture. These unit tests therefore verify the
 * factory contract (instances are produced for valid inputs, {@code null} for
 * an unavailable version) and lock down the localized labels via the
 * {@link MessageBundle} keys consumed by each fix. End-to-end label assertions
 * live in {@code MavenWrapperUrlInspectionTests}.
 *
 * @author Mark Paluch
 */
class MavenWrapperUrlFixesUnitTests {

	@Test
	void stripCredentialsReturnsFixInstance() {

		assertThat(MavenWrapperUrlFixes.stripCredentials()).isNotNull();
		assertThat(MessageBundle.message("inspection.maven-wrapper.credentials-in-url.fix"))
				.isEqualTo("Remove credentials");
	}

	@Test
	void replaceVersionReturnsFixInstance() {

		assertThat(MavenWrapperUrlFixes.replaceVersion("3.9.6")).isNotNull();
		assertThat(MessageBundle.message("inspection.maven-wrapper.inconsistent-version.fix", "3.9.6"))
				.isEqualTo("Use version '3.9.6'");
	}

	@Test
	void replaceArtifactReturnsFixInstance() {

		assertThat(MavenWrapperUrlFixes.replaceArtifact(WrapperProperty.DISTRIBUTION)).isNotNull();
		assertThat(MessageBundle.message("inspection.maven-wrapper.inconsistent-artifact.fix", "apache-maven"))
				.isEqualTo("Set artifact to 'apache-maven'");
	}

	@Test
	void replaceGroupPathReturnsFixInstance() {

		assertThat(MavenWrapperUrlFixes.replaceGroupPath(WrapperProperty.DISTRIBUTION)).isNotNull();
		assertThat(MessageBundle.message("inspection.maven-wrapper.improper-group-id.fix", "org/apache/maven"))
				.isEqualTo("Set group to 'org/apache/maven'");
	}

	@Test
	void replaceFileNameReturnsFixInstance() {

		assertThat(MavenWrapperUrlFixes.replaceFileName(WrapperProperty.DISTRIBUTION, "3.9.6")).isNotNull();
		assertThat(
				MessageBundle.message("inspection.maven-wrapper.malformed-file-name.fix",
						"apache-maven-3.9.6-bin.tar.gz"))
								.isEqualTo("Set file to 'apache-maven-3.9.6-bin.tar.gz'");
	}

	@Test
	void useDefaultUrlReturnsFixInstance() {

		assertThat(MavenWrapperUrlFixes.useDefaultUrl(WrapperProperty.DISTRIBUTION, "3.9.6")).isNotNull();
		assertThat(MessageBundle.message("inspection.maven-wrapper.default-url.fix")).isEqualTo("Use default URL");
	}

}
