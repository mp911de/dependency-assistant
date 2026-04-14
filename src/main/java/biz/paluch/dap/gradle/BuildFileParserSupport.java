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

import java.util.regex.Pattern;

import biz.paluch.dap.artifact.DependencyCollector;
import org.jspecify.annotations.Nullable;

/**
 * Support class for build file parser implementations.
 *
 * @author Mark Paluch
 */
abstract class BuildFileParserSupport {


	private static final Pattern PLUGIN_ID_PATTERN = Pattern.compile("[a-zA-Z0-9._-]+");

	private final DependencyCollector collector;

	public BuildFileParserSupport(DependencyCollector collector) {
		this.collector = collector;
	}

	public DependencyCollector getCollector() {
		return collector;
	}

	/**
	 * Returns {@literal true} if {@code id} is a safe, well-formed Gradle plugin
	 * ID.
	 */
	static boolean isValidPluginId(@Nullable String id) {
		return id != null && !id.isEmpty() && PLUGIN_ID_PATTERN.matcher(id).matches();
	}

}

