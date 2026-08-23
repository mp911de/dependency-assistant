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

package biz.paluch.dap.assistant.presentation;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Display policy for a captured project name: normalizes and display-trims the
 * name once and exposes it through two acceptance tiers.
 *
 * @author Mark Paluch
 */
public class ProjectName {

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

	private static final String ROLE_WORD = "(?:bom|bill[\\s-]of[\\s-]materials|release[\\s-]train)";

	/**
	 * Trailing role word, either preceded by start, whitespace or {@code /} (not by
	 * {@code -}: hyphenated identifiers stay intact) or wrapped in parentheses or
	 * brackets.
	 */
	private static final Pattern TRAILING_ROLE_WORD = Pattern.compile(
			"(?:(?<![^\\s/])" + ROLE_WORD + "|[(\\[]\\s*" + ROLE_WORD + "\\s*[)\\]])\\s*$", Pattern.CASE_INSENSITIVE);

	private static final Pattern TRAILING_SEPARATOR_DEBRIS = Pattern.compile("[\\s:/\\-]+$");

	private static final Pattern TRAILING_VERSION = Pattern.compile("\\s\\d+(?:\\.\\d+)*$");

	private static final int MAX_LENGTH = 50;

	private final ArtifactId artifactId;

	private final @Nullable String name;

	private final @Nullable String normalized;

	private final @Nullable String displayName;

	private ProjectName(ArtifactId artifactId, @Nullable String name, @Nullable String normalized,
			@Nullable String displayName) {
		this.artifactId = artifactId;
		this.name = name;
		this.normalized = normalized;
		this.displayName = displayName;
	}

	/**
	 * Create a new {@link ProjectName} for the given coordinates and captured
	 * project name.
	 * @param artifactId the coordinates the name must add information over.
	 * @param projectName the project name; can be {@literal null}.
	 */
	public static ProjectName of(ArtifactId artifactId, @Nullable String projectName) {
		String normalized = normalize(projectName);
		String trimmed = normalized == null ? null : trimForDisplay(normalized);
		String displayName = StringUtils.isEmpty(trimmed) ? null : trimmed;
		return new ProjectName(artifactId, projectName, normalized, displayName);
	}

	/**
	 * @return the normalized, display-trimmed name, or {@literal null} when the
	 * name is absent, unresolved, over-long, or merely echoes the coordinates.
	 */
	public @Nullable String getDisplayName() {
		return this.normalized == null || isRedundant(this.normalized.toLowerCase(Locale.ROOT))
				? null
				: this.displayName;
	}

	private static @Nullable String normalize(@Nullable String projectName) {

		if (projectName == null) {
			return null;
		}

		String name = WHITESPACE.matcher(projectName.trim()).replaceAll(" ");
		if (name.endsWith(".")) {
			name = name.substring(0, name.length() - 1).trim();
		}

		if (name.isEmpty() || name.contains("${") || name.length() > MAX_LENGTH) {
			return null;
		}

		return name;
	}

	/**
	 * Runs only on normalized names; acceptance judges the raw name.
	 * <p>Module-style names containing {@code ::} are kept as written. Otherwise
	 * trailing role words ({@code BOM}, {@code Bill of Materials},
	 * {@code Release Train}) and the separator debris they leave behind are
	 * stripped until stable, then a trailing version token ({@code 3},
	 * {@code 1.82}). A name consisting only of a role word ({@code bom}) trims to
	 * empty.
	 */
	private static String trimForDisplay(String name) {

		int parenthesis = name.indexOf('(');
		if (parenthesis != -1) {
			name = name.substring(0, parenthesis);
		}

		if (!name.contains("::")) {

			String previous;
			do {
				previous = name;
				name = TRAILING_ROLE_WORD.matcher(name).replaceAll("");
				name = TRAILING_SEPARATOR_DEBRIS.matcher(name).replaceAll("");
			} while (!name.equals(previous));

			name = TRAILING_VERSION.matcher(name).replaceAll("");
		}

		return WHITESPACE.matcher(name).replaceAll(" ").trim();
	}

	private boolean isRedundant(String name) {
		String artifact = this.artifactId.artifactId().toLowerCase(Locale.ROOT);
		return name.contains(":" + artifact) || name.trim().equals(artifact);
	}

	@Override
	public boolean equals(Object o) {

		if (this == o) {
			return true;
		}
		if (!(o instanceof ProjectName that)) {
			return false;
		}
		return this.artifactId.equals(that.artifactId)
				&& Objects.equals(this.normalized, that.normalized);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.artifactId, this.normalized);
	}

	@Override
	public String toString() {
		String name = getDisplayName();
		return "%s (%s)".formatted(StringUtils.isEmpty(name) ? this.artifactId.toString() : name, this.name);
	}

}
