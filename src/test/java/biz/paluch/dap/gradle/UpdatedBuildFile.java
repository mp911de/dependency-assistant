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
package biz.paluch.dap.gradle;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.assertions.DependencyCollectorAssert.DependencyUsageAssert;
import biz.paluch.dap.support.PropertyValue;
import com.intellij.psi.PsiFile;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AssertProvider;
import org.jspecify.annotations.Nullable;

/**
 * Test-oriented view over an updated Gradle-related file.
 * <p>The view combines dependency analysis via
 * {@link GradleFixtures#analyze(PsiFile)} with file-local property resolution
 * via {@link GradlePropertyResolver#forFile(PsiFile)} so tests can assert
 * semantic update outcomes without duplicating parser setup.
 *
 * @author Mark Paluch
 */
public class UpdatedBuildFile implements AssertProvider<UpdatedBuildFile.UpdatedBuildFileAssert> {

	private final PsiFile file;

	private final DependencyCollector collector;

	private final GradlePropertyResolver propertyResolver;

	private UpdatedBuildFile(PsiFile file, DependencyCollector collector, GradlePropertyResolver propertyResolver) {
		this.file = file;
		this.collector = collector;
		this.propertyResolver = propertyResolver;
	}

	/**
	 * Create an {@link UpdatedBuildFile} backed by fresh dependency analysis and
	 * local property resolution for the given file.
	 */
	public static UpdatedBuildFile of(PsiFile file) {
		return new UpdatedBuildFile(file, GradleFixtures.analyze(file), GradlePropertyResolver.forFile(file));
	}

	@Override
	public UpdatedBuildFileAssert assertThat() {
		return new UpdatedBuildFileAssert(this);
	}

	public PsiFile getFile() {
		return file;
	}

	/**
	 * AssertJ assertions for an {@link UpdatedBuildFile}.
	 */
	public static class UpdatedBuildFileAssert
			extends AbstractAssert<UpdatedBuildFileAssert, UpdatedBuildFile> {

		UpdatedBuildFileAssert(UpdatedBuildFile actual) {
			super(actual, UpdatedBuildFileAssert.class);
		}

		/**
		 * Verify that the analyzed file exposes a dependency or plugin usage for the
		 * given coordinates and navigate to the corresponding usage assertions.
		 */
		public DependencyUsageAssert containsDependency(String groupId, String artifactId) {
			isNotNull();
			return biz.paluch.dap.assertions.Assertions.assertThat(this.actual.collector)
					.hasDependencyUsage(groupId, artifactId);
		}

		/**
		 * Verify that the analyzed file exposes a dependency or plugin usage for the
		 * given coordinates with the expected version.
		 */
		public UpdatedBuildFileAssert containsDependency(String groupId, String artifactId, String version) {
			containsDependency(groupId, artifactId).hasVersion(version);
			return this;
		}

		/**
		 * Verify that the analyzed file exposes a dependency or plugin usage for the
		 * given artifact id and navigate to the corresponding usage assertions.
		 */
		public DependencyUsageAssert hasDependency(String artifactId) {
			isNotNull();
			return biz.paluch.dap.assertions.Assertions.assertThat(this.actual.collector)
					.hasDependencyUsage(artifactId);
		}

		/**
		 * Verify that the analyzed file exposes a dependency or plugin usage for the
		 * given artifact id with the expected version.
		 */
		public UpdatedBuildFileAssert hasDependency(String artifactId, String version) {
			hasDependency(artifactId).hasVersion(version);
			return this;
		}

		/**
		 * Verify that the updated file declares the given property with the expected
		 * value.
		 */
		public UpdatedBuildFileAssert hasProperty(String propertyName, String expectedValue) {
			isNotNull();

			PropertyValue element = this.actual.propertyResolver.getElement(propertyName);
			if (element == null) {
				failWithMessage("Expected property '%s' to be declared in %s but it was not found",
						propertyName, this.actual.file.getName());
			}

			String actualValue = element.propertyValue();
			if (!expectedValue.equals(actualValue)) {
				failWithMessage("Expected property '%s' in %s to have value '%s' but was '%s'",
						propertyName, this.actual.file.getName(), expectedValue, actualValue);
			}

			return this;
		}

		/**
		 * Verify that the updated file does not declare the given property.
		 */
		public UpdatedBuildFileAssert hasNoProperty(String propertyName) {
			isNotNull();

			@Nullable
			PropertyValue element = this.actual.propertyResolver.getElement(propertyName);
			if (element != null) {
				failWithMessage("Expected property '%s' to be absent in %s but found value '%s'",
						propertyName, this.actual.file.getName(), element.propertyValue());
			}

			return this;
		}

	}

}
