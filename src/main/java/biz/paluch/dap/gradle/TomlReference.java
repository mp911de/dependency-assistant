/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package biz.paluch.dap.gradle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 *
 * Value class representing a TOML entry reference within a Gradle build file.
 *
 * <p>Gradle treats '-', '_' and '.' as identifier separators when generating
 * type-safe accessors. For example:
 *
 * <pre>
 * "groovy-core"         -> ["groovy", "core"]
 * "androidx.awesome.lib" -> ["androidx", "awesome", "lib"]
 * "my_lib"              -> ["my", "lib"]
 * </pre>
 * @author Mark Paluch
 */
class TomlReference {

	private static final Pattern IDENTIFIER_SEPARATOR = Pattern.compile("[-_.]+");

	private final String key;

	private final @Nullable String section;

	private final List<String> segments;

	private TomlReference(String key, @Nullable String section, List<String> segments) {
		this.key = key;
		this.section = section;
		this.segments = segments;
	}

	/**
	 * Create a reference to a library.
	 */
	public static TomlReference libs(String library) {
		return new TomlReference(TomlParser.LIBS, null, split(library));
	}

	/**
	 * Create a reference to a plugin.
	 */
	public static TomlReference plugin(String plugin) {
		return new TomlReference(TomlParser.LIBS, TomlParser.PLUGINS, split(plugin));
	}

	/**
	 * Parse a TOML identifier alias.
	 */
	public static @Nullable TomlReference from(String identifier) {
		return from(split(identifier));
	}


	/**
	 * Create a reference from a list of segments.
	 */
	public static @Nullable TomlReference from(List<String> segments) {

		if (segments.size() < 2 || !TomlParser.LIBS.equals(segments.get(0))) {
			return null;
		}
		if (TomlParser.PLUGINS.equals(segments.get(1))) {
			if (segments.size() < 3) {
				return null;
			}

			return new TomlReference(segments.getFirst(), TomlParser.PLUGINS,
					List.copyOf(segments.subList(2, segments.size())));
		}
		if (TomlParser.VERSIONS.equals(segments.get(1)) || TomlParser.BUNDLES.equals(segments.get(1))) {
			return null;
		}

		return new TomlReference(segments.getFirst(), null, List.copyOf(segments.subList(1, segments.size())));
	}

	/**
	 * @return the TOML table name such as {@code libraries} or {@code plugins}.
	 */
	public String getTableName() {
		if (section == null && key.equals(TomlParser.LIBS)) {
			return TomlParser.LIBRARIES;
		}
		return section == null ? key : section;
	}

	private boolean isPlugin() {
		return TomlParser.PLUGINS.equals(section);
	}

	private static List<String> split(String identifier) {

		Assert.notNull(identifier, "TOML identifier alias must not be null");
		String normalized = identifier.trim();
		if (normalized.isEmpty()) {
			return List.of();
		}

		String[] rawParts = IDENTIFIER_SEPARATOR.split(normalized, -1);
		List<String> parts = new ArrayList<>(rawParts.length);

		for (String rawPart : rawParts) {
			if (!rawPart.isEmpty()) {
				parts.add(rawPart);
			}
		}

		return parts;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass())
			return false;
		TomlReference that = (TomlReference) o;
		return Objects.equals(key, that.key) && Objects.equals(section, that.section)
				&& Objects.equals(segments, that.segments);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, section, segments);
	}

	@Override
	public String toString() {

		List<String> parts = new ArrayList<>();
		parts.add(key);
		if (section != null) {
			parts.add(section);
		}
		parts.addAll(segments);
		return StringUtils.collectionToDelimitedString(parts, ".");
	}

}
