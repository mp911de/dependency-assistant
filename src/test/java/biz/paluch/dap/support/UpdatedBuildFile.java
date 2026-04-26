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

package biz.paluch.dap.support;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.assertions.DependencyCollectorAssert.DependencyUsageAssert;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AssertProvider;

/**
 * Test-oriented view over an updated build file.
 * <p>Combines dependency analysis via a {@link DependencyCollector} with
 * property resolution via a {@link PropertyResolver} so tests can assert
 * semantic update outcomes without duplicating parser setup.
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
	 * Create an {@link UpdatedBuildFile} backed by the given dependency collector
	 * and property resolver.
	 */
	public static UpdatedBuildFile of(DependencyCollector collector, PropertyResolver propertyResolver,
			String fileName) {
		return new UpdatedBuildFile(fileName, collector, propertyResolver);
	}

	@Override
	public UpdatedBuildFileAssert assertThat() {
		return new UpdatedBuildFileAssert(this);
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
		 * Verify that the updated file does not declare the given property.
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
