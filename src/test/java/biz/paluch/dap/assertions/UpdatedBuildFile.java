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

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.assertions.DependencyCollectorAssert.DependencyUsageAssert;
import biz.paluch.dap.support.PropertyResolver;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AssertProvider;

/**
 * Test-oriented assertion fixture for an updated build file.
 *
 * <p>An updated file is represented by the dependency usages parsed after the
 * update and the properties resolved from the same file. This allows update
 * tests to assert semantic outcomes instead of repeating parser setup or
 * matching raw file text.
 *
 * <p>Example: <pre class="code">
 * UpdatedBuildFile.of(collector, properties, "build.gradle")
 *     .assertThat()
 *     .containsDependency("org.junit", "junit-bom", "6.0.3")
 *     .hasProperty("junit.version", "6.0.3");
 * </pre>
 *
 * @author Mark Paluch
 */
public class UpdatedBuildFile implements AssertProvider<UpdatedBuildFile.UpdatedBuildFileAssert> {

	private final String fileName;

	private final DependencyCollector collector;

	private final PropertyResolver propertyResolver;

	private UpdatedBuildFile(String fileName, DependencyCollector collector, PropertyResolver propertyResolver) {
		this.fileName = fileName;
		this.collector = collector;
		this.propertyResolver = propertyResolver;
	}

	/**
	 * Creates a new updated build-file fixture backed by the given dependency
	 * collector and property resolver.
	 * @param collector the collector containing parsed dependency usages.
	 * @param propertyResolver the resolver containing parsed properties.
	 * @param fileName the file name used in assertion failure messages.
	 * @return the created updated build-file fixture.
	 */
	public static UpdatedBuildFile of(DependencyCollector collector, PropertyResolver propertyResolver,
			String fileName) {
		return new UpdatedBuildFile(fileName, collector, propertyResolver);
	}

	/**
	 * Returns an AssertJ assertion object for this updated build-file fixture.
	 * @return the created assertion object.
	 */
	@Override
	public UpdatedBuildFileAssert assertThat() {
		return new UpdatedBuildFileAssert(this);
	}

	/**
	 * AssertJ assertions for an {@link UpdatedBuildFile}.
	 *
	 * <p>Dependency assertions delegate to {@link DependencyCollectorAssert} and
	 * therefore return {@link DependencyUsageAssert} when further assertions should
	 * apply to the matching dependency usage.
	 */
	public static class UpdatedBuildFileAssert
			extends AbstractAssert<UpdatedBuildFileAssert, UpdatedBuildFile> {

		UpdatedBuildFileAssert(UpdatedBuildFile actual) {
			super(actual, UpdatedBuildFileAssert.class);
		}

		/**
		 * Verifies that the actual updated file exposes a dependency or plugin usage
		 * for the given coordinates and returns an assertion object for that usage.
		 * @param groupId the expected group id.
		 * @param artifactId the expected artifact id.
		 * @return an assertion object for the matching dependency usage.
		 */
		public DependencyUsageAssert containsDependency(String groupId, String artifactId) {
			isNotNull();
			return biz.paluch.dap.assertions.Assertions.assertThat(this.actual.collector)
					.hasDependencyUsage(groupId, artifactId);
		}

		/**
		 * Verifies that the actual updated file exposes a dependency or plugin usage
		 * for the given coordinates with the expected version.
		 * @param groupId the expected group id.
		 * @param artifactId the expected artifact id.
		 * @param version the expected dependency version.
		 * @return this assertion object.
		 */
		public UpdatedBuildFileAssert containsDependency(String groupId, String artifactId, String version) {
			containsDependency(groupId, artifactId).hasVersion(version);
			return this;
		}

		/**
		 * Verifies that the actual updated file exposes a dependency or plugin usage
		 * for the given artifact id and returns an assertion object for that usage.
		 * @param artifactId the expected artifact id.
		 * @return an assertion object for the matching dependency usage.
		 */
		public DependencyUsageAssert hasDependency(String artifactId) {
			isNotNull();
			return biz.paluch.dap.assertions.Assertions.assertThat(this.actual.collector)
					.hasDependencyUsage(artifactId);
		}

		/**
		 * Verifies that the actual updated file exposes a dependency or plugin usage
		 * for the given artifact id with the expected version.
		 * @param artifactId the expected artifact id.
		 * @param version the expected dependency version.
		 * @return this assertion object.
		 */
		public UpdatedBuildFileAssert hasDependency(String artifactId, String version) {
			hasDependency(artifactId).hasVersion(version);
			return this;
		}

		/**
		 * Verifies that the actual updated file declares the given property with the
		 * expected value.
		 * @param propertyName the expected property name.
		 * @param expectedValue the expected property value.
		 * @return this assertion object.
		 */
		public UpdatedBuildFileAssert hasProperty(String propertyName, String expectedValue) {
			isNotNull();

			String actualValue = this.actual.propertyResolver.getProperty(propertyName);
			if (actualValue == null) {
				failWithMessage("Expected property '%s' to be declared in %s but it was not found",
						propertyName, this.actual.fileName);
			}

			if (!expectedValue.equals(actualValue)) {
				failWithMessage("Expected property '%s' in %s to have value '%s' but was '%s'",
						propertyName, this.actual.fileName, expectedValue, actualValue);
			}

			return this;
		}

		/**
		 * Verifies that the actual updated file does not declare the given property.
		 * @param propertyName the property name expected to be absent.
		 * @return this assertion object.
		 */
		public UpdatedBuildFileAssert hasNoProperty(String propertyName) {
			isNotNull();

			String value = this.actual.propertyResolver.getProperty(propertyName);
			if (value != null) {
				failWithMessage("Expected property '%s' to be absent in %s but found value '%s'",
						propertyName, this.actual.fileName, value);
			}

			return this;
		}

	}

}
