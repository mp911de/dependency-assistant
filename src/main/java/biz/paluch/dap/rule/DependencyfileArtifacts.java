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
 * PSI edits on the {@code artifacts} section of a {@code dependencyfile.json}
 * descriptor: setting entry names and inserting entries that do not exist yet.
 * Existing object-valued entries have their {@code name} replaced so that "Add
 * to dependencyfile.json" and an Upgrade Plan rename behave alike. Existing
 * scalar and array rules are left unchanged. Callers run these operations
 * inside a write command on a JSON {@link PsiFile}; an {@code artifacts} object
 * is created when no object-valued section exists.
 *
 * <p>Entry keys are the narrowest {@link ArtifactPattern#keyFor(ArtifactId)
 * pattern key}, or a {@code groupId:prefix*} wildcard for members sharing a
 * groupId and a word-boundary prefix (see {@link #wildcardKey}). A new key is
 * inserted before the first existing key that sorts after it
 * (case-insensitive), so the descriptor stays loosely ordered without a full
 * rewrite.
 *
 * @author Mark Paluch
 */
public class DependencyfileArtifacts {

	private DependencyfileArtifacts() {
	}

	/**
	 * Set {@code name} on the entries covering the coordinates (see
	 * {@link #entries(List, String)}). When that is a wildcard entry, existing
	 * exact entries of the coordinates are renamed as well: an exact key outranks
	 * the wildcard and would otherwise keep the old name in effect. Only
	 * object-valued exact entries can be renamed.
	 *
	 * @param project the project owning the file.
	 * @param psiFile the descriptor file; can be {@literal null}.
	 * @param artifactIds the coordinates to name, possibly empty.
	 * @param name the name to write.
	 * @return the {@code name}-value range of the first entry for the caret, or
	 * {@literal null} when the file is not a JSON object, no coordinates are given,
	 * or the first entry remains non-object-valued.
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
	 * Set each entry's {@code name} in the descriptor's {@code artifacts} object:
	 * an existing entry gets its {@code name} value replaced (or added), a missing
	 * entry is inserted sorted. Entries whose value is not an object are left
	 * alone. The file is reformatted when anything changed.
	 *
	 * @param project the project owning the file.
	 * @param psiFile the descriptor file; can be {@literal null}.
	 * @param entries the entries whose names to set.
	 * @return the {@code name}-value range of the first entry for the caret, or
	 * {@literal null} when the file is not a JSON object, no entry was given, or
	 * the first entry has no string-valued {@code name} after editing.
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
		}
		return nameValueRange(artifacts, entries.iterator().next().key());
	}

	/**
	 * Compute the entries naming the given coordinates {@code name}: one wildcard
	 * entry when the coordinates share a groupId and word-boundary prefix,
	 * otherwise one entry per coordinate.
	 *
	 * @param artifactIds the coordinates to name.
	 * @param name the name to write.
	 * @return the entries, in coordinate order.
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
	 * Return the {@code groupId:prefix*} wildcard key for the coordinates, or
	 * {@literal null} when they do not share a groupId or their artifactIds have no
	 * common prefix ending on a {@code -} or {@code .} word boundary.
	 *
	 * @param artifactIds the member coordinates. The list must not be empty.
	 * @return the wildcard key, or {@literal null}.
	 * @throws java.util.NoSuchElementException if {@code artifactIds} is empty.
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

	/**
	 * Return the descriptor object's {@code artifacts} value, creating an empty
	 * {@code artifacts} object when no object-valued property exists.
	 */
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

	/**
	 * Insert {@code property} into {@code object}: before {@code anchor} when
	 * given, otherwise appended after the last property; an empty object receives
	 * it directly after the opening brace. The required comma is added on the side
	 * that borders an existing property.
	 */
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
	 * One {@code artifacts} entry: its key and display name.
	 *
	 * @param key the artifact pattern key.
	 * @param name the display name.
	 */
	public record ArtifactEntry(String key, String name) implements Comparable<ArtifactEntry> {

		/**
		 * Create an entry for the coordinate, named after {@code projectName} when
		 * present, else after the key without a leading {@code @}.
		 *
		 * @param artifactId the artifact coordinates.
		 * @param projectName the project name, or {@literal null} or blank when
		 * unavailable.
		 * @return the descriptor entry.
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
