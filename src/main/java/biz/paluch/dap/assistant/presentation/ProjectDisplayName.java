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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import org.jspecify.annotations.Nullable;

/**
 * Display policy for a captured project name.
 *
 * @author Mark Paluch
 */
public class ProjectDisplayName {

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

	private static final Pattern BOM_WORD = Pattern.compile("\\s(?:BOM|(?i:bill.of.materials))\\b");

	private static final Pattern TRAILING_SEPARATOR_DEBRIS = Pattern.compile("[\\s:\\-]+$");

	private static final Pattern DECLARED_BOUNDARY = Pattern.compile("\\s*::\\s*");

	private static final int MAX_LENGTH = 50;

	private ProjectDisplayName() {
	}

	/**
	 * Run the captured project name through the strict tier for surfaces that
	 * already show the coordinates: the name must add information over them.
	 * @param artifactId the coordinates the name must add information over.
	 * @param projectName the captured project name; can be {@literal null}.
	 * @return the normalized, display-trimmed name, or {@literal null} when the
	 * name is absent, unresolved, over-long, or merely echoes the coordinates.
	 */
	public static @Nullable String getAcceptedProjectName(ArtifactId artifactId, @Nullable String projectName) {

		String name = normalize(projectName);
		if (name == null || isRedundant(name.toLowerCase(Locale.ROOT), artifactId)) {
			return null;
		}

		String display = trimForDisplay(name);
		return display.isEmpty() ? null : display;
	}

	/**
	 * Run the captured project name through the grouping tier for cross-member
	 * name-shape analysis: a name whose tokens all appear among the coordinate
	 * tokens counts as absent, and {@code ::} module separators are collapsed into
	 * single spaces so the result carries plain word boundaries.
	 * <p>Unlike the strict tier, a boundary-shifted echo such as
	 * {@code AspectJ Weaver} for {@code aspectjweaver} is kept: the shifted word
	 * boundary is exactly the information the shape analysis consumes.
	 * @param artifactId the coordinates the name must add information over.
	 * @param projectName the captured project name; can be {@literal null}.
	 * @return the grouping name as written, or {@literal null} when the name is
	 * absent, unresolved, over-long, or a subset echo of the coordinates.
	 */
	public static @Nullable String getGroupingName(ArtifactId artifactId, @Nullable String projectName) {

		String name = normalize(projectName);
		if (name == null || isCoordinateSubset(name, artifactId)) {
			return null;
		}

		String grouping = DECLARED_BOUNDARY.matcher(trimForDisplay(name)).replaceAll(" ").trim();
		return grouping.isEmpty() ? null : grouping;
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
	 * A single-token name restating the last token of a multi-token artifact id
	 * ("bom" for {@code mongodb-driver-bom}) is a module qualifier, not a name.
	 */
	private static boolean isModuleQualifier(String display, ArtifactId artifactId) {

		List<String> nameTokens = tokenize(display);
		if (nameTokens.size() != 1) {
			return false;
		}

		List<String> artifactTokens = tokenize(artifactId.artifactId());
		return artifactTokens.size() > 1 && nameTokens.get(0).equals(artifactTokens.get(artifactTokens.size() - 1));
	}

	/**
	 * Runs only on accepted names; acceptance judges the raw name.
	 */
	private static String trimForDisplay(String name) {

		int parenthesis = name.indexOf('(');
		if (parenthesis != -1) {
			name = name.substring(0, parenthesis);
		}

		name = BOM_WORD.matcher(name).replaceAll("");
		name = TRAILING_SEPARATOR_DEBRIS.matcher(name).replaceAll("");

		return WHITESPACE.matcher(name).replaceAll(" ").trim();
	}

	private static boolean isRedundant(String name, ArtifactId artifactId) {
		String artifact = artifactId.artifactId().toLowerCase(Locale.ROOT);
		return name.contains(":" + artifact) || name.trim().equals(artifact);
	}

	/**
	 * Every name token appears among the coordinate tokens: the name adds no
	 * information over shown coordinates.
	 */
	private static boolean isCoordinateSubset(String name, ArtifactId artifactId) {

		Set<String> coordinateTokens = new HashSet<>(tokenize(artifactId.groupId()));
		coordinateTokens.addAll(tokenize(artifactId.artifactId()));
		return coordinateTokens.containsAll(tokenize(name));
	}

	private static List<String> tokenize(String value) {

		List<String> tokens = new ArrayList<>();
		for (String token : NON_ALPHANUMERIC.split(value.toLowerCase(Locale.ROOT))) {

			if (!token.isEmpty()) {
				tokens.add(token);
			}
		}

		return tokens;
	}

}
