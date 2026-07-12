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

package biz.paluch.dap.maven;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;

/**
 * Base class for {@link MavenParser} and {@link MavenBomParser}, providing
 * shared POM tag traversal, property extraction, and artifact-coordinate
 * parsing.
 *
 * @author Mark Paluch
 */
class MavenPomSupport {

	static final String EXTENSIONS = "extensions";

	static final String EXTENSION = "extension";

	static final String PLUGIN_MANAGEMENT = "pluginManagement";

	static final String BUILD = "build";

	static final String DEPENDENCY_MANAGEMENT = "dependencyManagement";

	static final String REPORTING = "reporting";

	static final String DEPENDENCIES = "dependencies";

	static final String DEPENDENCY = "dependency";

	static final String PLUGINS = "plugins";

	static final String PLUGIN = "plugin";

	static final String GROUP_ID = "groupId";

	static final String PROFILE = "profile";

	static final String ID = "id";

	static final String PROPERTIES = "properties";

	static final String ARTIFACT_ID = "artifactId";

	static final String VERSION = "version";

	/**
	 * Return whether the given {@code parent} tag is a candidate for being a parent
	 * dependency declaration. A parent tag is a candidate if it declares a groupId
	 * that is not inherited from the current project and does not specify a
	 * relativePath, which would indicate a local multi-module project.
	 * @param root project root tag.
	 * @param parent parent tag.
	 * @return {@literal true} if the given {@code parent} tag is a supported
	 * dependency candidate.
	 */
	@Contract("_, null -> false; null, _ -> false")
	public static boolean isParentDependencyCandidate(@Nullable XmlTag root, @Nullable XmlTag parent) {

		if (root == null || parent == null) {
			return false;
		}

		Subtag groupId = Subtag.of(root, GROUP_ID);
		Subtag parentGroupId = Subtag.of(parent, GROUP_ID);

		// inherited groupId indicates a local multi-module project
		if (groupId.isEmpty() || groupId.textEquals(parentGroupId)) {
			return false;
		}

		Subtag relativePath = Subtag.of(parent, "relativePath");
		// skip local multi-module projects with relativePath
		return !relativePath.isPresent();
	}

	/**
	 * Parse artifact coordinates from the given XML tag.
	 * @param tag the dependency or plugin tag.
	 * @param propertyResolver resolver for Maven placeholders.
	 * @return the artifact id, or {@literal null} if no artifact id is present.
	 */
	public static @Nullable ArtifactId parseArtifactId(@Nullable XmlTag tag, PropertyResolver propertyResolver) {
		return tag != null
				? parseArtifactId(PomTag.of(tag), propertyResolver)
				: null;
	}

	static @Nullable ArtifactId parseArtifactId(@Nullable PomTag tag, PropertyResolver propertyResolver) {
		return tag != null
				? parseArtifactId(tag.getGroupId(), tag.getArtifactId(), propertyResolver)
				: null;
	}

	public static @Nullable ArtifactId parseArtifactId(@Nullable String groupId, @Nullable String artifactId,
			PropertyResolver propertyResolver) {

		if (StringUtils.isEmpty(artifactId)) {
			return null;
		}

		groupId = StringUtils.hasText(groupId) ? groupId : "org.apache.maven.plugins";

		if (artifactId.contains("${") || artifactId.contains("}")) {
			artifactId = propertyResolver.resolvePlaceholders(artifactId);
		}

		if (groupId.contains("${") || groupId.contains("}")) {
			groupId = propertyResolver.resolvePlaceholders(groupId);
		}

		return ArtifactId.of(groupId, artifactId);
	}


	/**
	 * Parse Maven properties from the given {@link XmlFile}, returning each
	 * property mapped to its plain {@link String} value.
	 */
	public static Map<String, String> getProperties(XmlFile pomFile) {

		Map<String, String> result = new LinkedHashMap<>();
		parseProperties(pomFile).forEach((k, v) -> result.put(k, v.getValue()));

		return result;
	}

	/**
	 * Parse Maven properties from the given {@link XmlFile}, retaining the
	 * declaring PSI element of each property as a {@link PropertyValue}.
	 */
	public static Map<String, PropertyValue> parseProperties(XmlFile pomFile) {

		Map<String, PropertyValue> result = new LinkedHashMap<>();

		doWithRoot(pomFile, root -> {

			PomTag pomTag = PomTag.of(root);

			doWithProfiles(pomTag, profile -> {
				profile.subtags(PROPERTIES).forEach(properties -> collectProperties(properties, result));
			});
			pomTag.subtags(PROPERTIES).forEach(properties -> collectProperties(properties, result));
		});

		return result;
	}

	static void doWithRoot(XmlFile file, Consumer<XmlTag> callback) {
		XmlTag rootTag = file.getRootTag();
		if (rootTag != null) {
			callback.accept(rootTag);
		}
	}

	static void doWithProfiles(PomTag root, Consumer<PomTag> callback) {
		root.subtags("profiles").subtags(PROFILE).forEach(callback);
	}

	static void collectProperties(PomTag properties, Map<String, PropertyValue> target) {

		for (XmlTag child : properties.getTag().getSubTags()) {
			String name = child.getLocalName();
			if (StringUtils.isEmpty(name)) {
				continue;
			}
			target.put(name.trim(), new PropertyValue(name, child.getValue().getTrimmedText().trim(), child));
		}
	}

	static String text(XmlTag tag, String subTag) {
		String value = tag.getSubTagText(subTag);
		return value != null ? value.trim() : "";
	}

	/**
	 * Flattened group of POM tags supporting further descent by tag name.
	 */
	static class PomTags {

		private final XmlTag[] tags;

		private PomTags(XmlTag[] tags) {
			this.tags = tags;
		}

		public PomTags subtags(String qname) {
			return new PomTags(Arrays.stream(tags).flatMap(tag -> Arrays.stream(tag.findSubTags(qname)))
					.toArray(XmlTag[]::new));
		}

		public void forEach(Consumer<? super PomTag> action) {
			for (XmlTag tag : tags) {
				action.accept(PomTag.of(tag));
			}
		}

	}

	/**
	 * Read-only view over a POM tag with accessors for text-bearing subtags.
	 */
	static class PomTag {

		private final XmlTag tag;

		private PomTag(XmlTag tag) {
			this.tag = tag;
		}

		public static PomTag of(XmlTag tag) {
			return new PomTag(tag);
		}

		public PomTags subtags(String qname) {
			return new PomTags(this.tag.findSubTags(qname));
		}

		public Subtag subtag(String qname) {
			return Subtag.of(this.tag, qname);
		}

		public @Nullable String getText(String qname) {
			return subtag(qname).getText();
		}

		public @Nullable String getGroupId() {
			return getText(GROUP_ID);
		}

		public @Nullable String getArtifactId() {
			return getText(ARTIFACT_ID);
		}

		public XmlTag getTag() {
			return this.tag;
		}

	}

	/**
	 * Trimmed text of a named subtag; present only when the subtag holds text.
	 */
	static class Subtag {

		private final @Nullable String text;

		private Subtag(@Nullable XmlTag owner, String name) {
			String text = owner != null ? owner.getSubTagText(name) : null;
			this.text = StringUtils.hasText(text) ? text.trim() : null;
		}

		public static Subtag of(@Nullable XmlTag owner, String name) {
			return new Subtag(owner, name);
		}

		public @Nullable String getText() {
			return text;
		}

		public boolean isPresent() {
			return text != null;
		}

		public boolean isEmpty() {
			return text == null;
		}

		public <T> T eitherOr(Function<String, T> ifPresent, Supplier<T> otherwise) {
			return text != null ? ifPresent.apply(text) : otherwise.get();
		}

		public boolean textEquals(String expected) {
			return expected.equals(text);
		}

		public boolean textEquals(Subtag other) {
			return text != null && text.equals(other.text);
		}

	}

}
