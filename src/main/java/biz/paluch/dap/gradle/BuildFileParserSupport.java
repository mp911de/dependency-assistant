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

import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Support class for build file parser implementations.
 *
 * @author Mark Paluch
 */
abstract class BuildFileParserSupport {

	private final DependencyCollector collector;

	public BuildFileParserSupport(DependencyCollector collector) {
		this.collector = collector;
	}

	public DependencyCollector getCollector() {
		return collector;
	}

	@Nullable
	abstract String resolveValue(@Nullable String value);

	@Nullable
	abstract String getProperty(@Nullable String value);

	static @Nullable String resolveValue(@Nullable String value, Map<String, String> properties) {
		if (value == null) {
			return null;
		}
		if (value.startsWith("${") && value.endsWith("}")) {
			return properties.getOrDefault(value.substring(2, value.length() - 1), value);
		}
		return value;
	}

}
