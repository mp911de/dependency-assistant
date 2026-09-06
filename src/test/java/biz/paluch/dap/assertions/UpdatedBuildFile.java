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

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.assertions.DependencyCollectorAssert.DependencyUsageAssert;
import biz.paluch.dap.support.PropertyResolver;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AssertProvider;

/**
 * Assertions on dependencies and properties parsed after a build-file update.
 * <p>This lets tests check the resulting meaning without matching source
 * layout.
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
	 * Create a fixture from the parsed update result.
	 * @param fileName the name used in assertion failures.
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
	 * Assertions for updated build files. Dependency checks include plugin usages.
	 */
	public static class UpdatedBuildFileAssert
			extends AbstractAssert<UpdatedBuildFileAssert, UpdatedBuildFile> {

		UpdatedBuildFileAssert(UpdatedBuildFile actual) {
			super(actual, UpdatedBuildFileAssert.class);
		}

		public DependencyUsageAssert containsDependency(String groupId, String artifactId) {
			isNotNull();
			return biz.paluch.dap.assertions.Assertions.assertThat(this.actual.collector)
					.hasDependencyUsage(groupId, artifactId);
		}

		public UpdatedBuildFileAssert containsDependency(String groupId, String artifactId, String version) {
			containsDependency(groupId, artifactId).hasVersion(version);
			return this;
		}

		public DependencyUsageAssert hasDependency(String artifactId) {
			isNotNull();
			return biz.paluch.dap.assertions.Assertions.assertThat(this.actual.collector)
					.hasDependencyUsage(artifactId);
		}

		public DependencyCollectorAssert hasNoDependencies() {
			isNotNull();
			return biz.paluch.dap.assertions.Assertions.assertThat(this.actual.collector)
					.isEmpty();
		}

		public UpdatedBuildFileAssert hasDependency(String artifactId, String version) {
			hasDependency(artifactId).hasVersion(version);
			return this;
		}

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
