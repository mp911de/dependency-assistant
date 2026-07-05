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
 * Project-specific AssertJ entry point.
 *
 * <p>This class extends AssertJ's standard
 * {@link org.assertj.core.api.Assertions} facade and adds overloads for
 * Dependency Assistant test infrastructure. Tests should statically import this
 * type when they need both standard AssertJ assertions and project-specific
 * assertions in the same file.
 *
 * <p>Example: <pre class="code">
 * import static biz.paluch.dap.assertions.Assertions.assertThat;
 *
 * assertThat(collector).hasUsageCount(2);
 * assertThat(buildFile).hasSingleGutterContaining("Patch", "6.0.3");
 * assertThat(fixture).hasNoGutterMarks();
 * </pre>
 *
 * @author Mark Paluch
 */
public class Assertions extends org.assertj.core.api.Assertions {

	/**
	 * Creates a new assertion for the parsed artifact version string.
	 * @param version the version string under test.
	 * @return the created assertion object.
	 */
	public static ArtifactVersionAssert assertThatVersion(String version) {
		return assertThat(ArtifactVersion.of(version));
	}

	/**
	 * Creates a new assertion for the given artifact version.
	 * @param version the version under test.
	 * @return the created assertion object.
	 */
	public static ArtifactVersionAssert assertThat(ArtifactVersion version) {
		return new ArtifactVersionAssert(version);
	}

	/**
	 * Creates a new assertion for the given IntelliJ fixture.
	 * @param fixture the fixture under test.
	 * @return the created assertion object.
	 */
	public static CodeInsightFixtureAssert assertThat(CodeInsightTestFixture fixture) {
		return new CodeInsightFixtureAssert(fixture);
	}

	/**
	 * Creates a new assertion for the given dependency collector.
	 * @param collector the collector under test.
	 * @return the created assertion object.
	 */
	public static DependencyCollectorAssert assertThat(DependencyCollector collector) {
		return new DependencyCollectorAssert(collector);
	}

	/**
	 * Creates a new assertion for the given vulnerability check request.
	 * @param request the request under test.
	 * @return the created assertion object.
	 */
	public static CheckRequestAssert assertThat(CheckRequest request) {
		return new CheckRequestAssert(request);
	}

	/**
	 * Creates a new assertion for the given releases.
	 * @param releases the releases under test.
	 * @return the created assertion object.
	 */
	public static ReleasesAssert assertThat(Releases releases) {
		return new ReleasesAssert(releases);
	}

	/**
	 * Creates a new assertion for the given release.
	 * @param release the release under test.
	 * @return the created assertion object.
	 */
	public static ReleaseAssert assertThat(Release release) {
		return new ReleaseAssert(release);
	}

	/**
	 * Creates a new assertion for the given PSI element.
	 * @param element the PSI element under test.
	 * @return the created assertion object.
	 */
	public static PsiElementAssert assertThat(PsiElement element) {
		return new PsiElementAssert(element);
	}

	/**
	 * Creates a new assertion for the given PSI file.
	 * @param file the PSI file under test.
	 * @return the created assertion object.
	 */
	public static PsiElementAssert assertThat(PsiFile file) {
		return new PsiElementAssert(file);
	}

}
