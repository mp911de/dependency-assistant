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

package biz.paluch.dap.rule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.util.StringUtils;
import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jspecify.annotations.Nullable;

/**
 * Shared name edits for dependencyfile actions and Upgrade Plan renames.
 * Callers must hold a write command. Existing scalar and array rules are left
 * unchanged.
 *
 * <p>New entries follow the descriptor's key order without reordering existing
 * entries.
 *
 * @author Mark Paluch
 */
public class DependencyfileArtifacts {

	private DependencyfileArtifacts() {
	}

	/**
	 * Name the entries covering these coordinates. Existing exact entries are
	 * renamed alongside a wildcard because they would otherwise override its name.
	 *
	 * @return the first entry's name range for caret placement, or {@code null} if
	 * the file or first entry cannot be named.
	 * @see #entries(List, String)
	 */
	public static @Nullable TextRange setName(Project project, @Nullable PsiFile psiFile,
			List<? extends ArtifactId> artifactIds, String name) {

		if (!(psiFile instanceof JsonFile jsonFile) || !(jsonFile.getTopLevelValue() instanceof JsonObject root)) {
			return null;
		}

		List<ArtifactEntry> entries = new ArrayList<>(entries(artifactIds, name));

		if (root.findProperty("artifacts") instanceof JsonProperty property
				&& property.getValue() instanceof JsonObject artifacts) {

			for (ArtifactId artifactId : artifactIds) {
				ArtifactEntry exact = new ArtifactEntry(ArtifactPattern.keyFor(artifactId), name);
				if (!entries.contains(exact) && artifacts.findProperty(exact.key()) != null) {
					entries.add(exact);
				}
			}
		}

		return setNames(project, psiFile, entries);
	}

	/**
	 * Set entry names, creating missing entries and leaving scalar or array rules
	 * unchanged. Changes reformat the file.
	 *
	 * @return the first entry's name range for caret placement, or {@code null} if
	 * there are no entries or the file or first entry cannot be named.
	 */
	public static @Nullable TextRange setNames(Project project, @Nullable PsiFile psiFile,
			Collection<ArtifactEntry> entries) {

		if (!(psiFile instanceof JsonFile jsonFile) || !(jsonFile.getTopLevelValue() instanceof JsonObject root)
				|| entries.isEmpty()) {
			return null;
		}

		JsonElementGenerator generator = new JsonElementGenerator(project);
		JsonObject artifacts = artifactsObject(root, generator);

		boolean changed = false;
		for (ArtifactEntry entry : entries) {

			JsonProperty existing = artifacts.findProperty(entry.key());
			if (existing == null) {
				insertSorted(artifacts, entry, generator);
				changed = true;
				continue;
			}

			if (!(existing.getValue() instanceof JsonObject value)) {
				continue;
			}

			JsonProperty name = value.findProperty("name");
			if (name == null) {
				insertProperty(value, generator.createProperty("name", quote(entry.name())), null, generator);
				changed = true;
				continue;
			}

			JsonValue current = name.getValue();
			if (current instanceof JsonStringLiteral literal && entry.name().equals(literal.getValue())) {
				continue;
			}

			JsonStringLiteral replacement = generator.createStringLiteral(entry.name());
			if (current != null) {
				current.replace(replacement);
			} else {
				name.add(replacement);
			}
			changed = true;
		}

		if (changed) {
			CodeStyleManager.getInstance(project).reformat(jsonFile);
			// Formatting can replace the JSON PSI nodes.
			JsonObject formattedRoot = (JsonObject) jsonFile.getTopLevelValue();
			artifacts = (JsonObject) formattedRoot.findProperty("artifacts").getValue();
		}
		return nameValueRange(artifacts, entries.iterator().next().key());
	}

	/**
	 * Use one wildcard entry for multiple coordinates sharing a group and
	 * word-boundary prefix. Otherwise, return one entry per coordinate in input
	 * order.
	 *
	 * @see #wildcardKey(List)
	 */
	public static List<ArtifactEntry> entries(List<? extends ArtifactId> artifactIds, String name) {

		if (artifactIds.size() > 1) {
			String wildcardKey = wildcardKey(artifactIds);
			if (wildcardKey != null) {
				return List.of(new ArtifactEntry(wildcardKey, name));
			}
		}

		List<ArtifactEntry> entries = new ArrayList<>(artifactIds.size());
		for (ArtifactId artifactId : artifactIds) {
			entries.add(new ArtifactEntry(ArtifactPattern.keyFor(artifactId), name));
		}
		return entries;
	}

	/**
	 * Return a {@code groupId:prefix*} key, or {@code null} if the coordinates
	 * share no group or no prefix ending at a {@code -} or {@code .} boundary.
	 *
	 * @throws java.util.NoSuchElementException if the list is empty.
	 */
	public static @Nullable String wildcardKey(List<? extends ArtifactId> artifactIds) {

		String groupId = artifactIds.getFirst().groupId();
		List<String> names = new ArrayList<>(artifactIds.size());
		for (ArtifactId artifactId : artifactIds) {
			if (!groupId.equals(artifactId.groupId())) {
				return null;
			}
			names.add(artifactId.artifactId());
		}

		String commonPrefix = StringUtils.longestCommonPrefix(names);
		int separator = Math.max(commonPrefix.lastIndexOf('-'), commonPrefix.lastIndexOf('.'));
		if (separator < 0) {
			return null;
		}

		return groupId + ":" + commonPrefix.substring(0, separator + 1) + "*";
	}

	private static JsonObject artifactsObject(JsonObject root, JsonElementGenerator generator) {

		JsonProperty artifacts = root.findProperty("artifacts");
		if (artifacts != null && artifacts.getValue() instanceof JsonObject object) {
			return object;
		}

		JsonProperty created = generator.createProperty("artifacts", "{}");
		JsonProperty inserted = (JsonProperty) insertProperty(root, created, null, generator);
		return (JsonObject) inserted.getValue();
	}

	private static void insertSorted(JsonObject artifacts, ArtifactEntry entry, JsonElementGenerator generator) {

		JsonProperty property = generator.createProperty(entry.key(), "{\"name\": " + quote(entry.name()) + "}");

		JsonProperty anchor = null;
		for (JsonProperty sibling : artifacts.getPropertyList()) {
			if (sibling.getName().compareToIgnoreCase(entry.key()) > 0) {
				anchor = sibling;
				break;
			}
		}

		insertProperty(artifacts, property, anchor, generator);
	}

	private static PsiElement insertProperty(JsonObject object, JsonProperty property, @Nullable JsonProperty anchor,
			JsonElementGenerator generator) {

		List<JsonProperty> properties = object.getPropertyList();
		if (properties.isEmpty()) {
			return object.addAfter(property, object.getFirstChild());
		}

		if (anchor == null) {
			PsiElement added = object.addAfter(property, properties.getLast());
			object.addBefore(generator.createComma(), added);
			return added;
		}

		PsiElement added = object.addBefore(property, anchor);
		object.addAfter(generator.createComma(), added);
		return added;
	}

	private static @Nullable TextRange nameValueRange(JsonObject artifacts, String key) {

		for (JsonProperty property : artifacts.getPropertyList()) {
			if (!property.getName().equals(key)) {
				continue;
			}
			if (property.getValue() instanceof JsonObject value
					&& value.findProperty("name") instanceof JsonProperty name
					&& name.getValue() instanceof JsonStringLiteral literal) {
				TextRange range = literal.getTextRange();
				return new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1);
			}
		}
		return null;
	}

	private static String quote(String value) {
		return "\"" + StringUtil.escapeStringCharacters(value) + "\"";
	}

	/**
	 * An artifact pattern key and display name for the descriptor.
	 */
	public record ArtifactEntry(String key, String name) implements Comparable<ArtifactEntry> {

		/**
		 * Create an entry using the project name, falling back to the key without a
		 * leading {@code @} when the name is absent or blank.
		 */
		public static ArtifactEntry create(ArtifactId artifactId, @Nullable String projectName) {

			String key = ArtifactPattern.keyFor(artifactId);
			String name = StringUtils.hasText(projectName) ? projectName
					: (key.startsWith("@") ? key.substring(1) : key);
			return new ArtifactEntry(key, name);
		}

		@Override
		public int compareTo(ArtifactEntry o) {
			return key.compareToIgnoreCase(o.key);
		}

	}

}
