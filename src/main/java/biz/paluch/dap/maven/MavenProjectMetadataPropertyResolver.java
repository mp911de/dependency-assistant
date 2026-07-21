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

import java.util.HashMap;
import java.util.Map;

import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jspecify.annotations.Nullable;

/**
 * Maven property resolver over the POM's own elements.
 *
 * <p>Every value-carrying tag registers under its dot path with the
 * {@code project.} prefix and the legacy {@code pom.} alias, following the
 * Maven convention of referencing POM elements by name (e.g.
 * {@code project.scm.tag} or {@code project.parent.version}). Entries of the
 * {@code <properties>} block additionally register under their plain property
 * name.
 *
 * @author Mark Paluch
 */
class MavenProjectMetadataPropertyResolver implements PropertyResolver {

	private final Map<String, PropertyValue> properties = new HashMap<>();

	private final @Nullable PropertyValue version;

	private final @Nullable PropertyValue parentVersion;

	/**
	 * Create a new {@code MavenProjectMetadataPropertyResolver} for the given POM
	 * file.
	 * @param pom the POM file providing project coordinates.
	 */
	MavenProjectMetadataPropertyResolver(XmlFile pom) {

		XmlDocument document = pom.getDocument();
		XmlTag rootTag = document != null ? document.getRootTag() : null;
		if (rootTag != null) {
			registerProjectTags(rootTag);
		}

		this.version = properties.get("project.version");
		this.parentVersion = properties.get("project.parent.version");

		registerEffectiveCoordinate("groupId");
		registerEffectiveCoordinate("artifactId");
		registerEffectiveCoordinate("version");
	}

	public static MavenProjectMetadataPropertyResolver from(XmlFile pom) {
		return CachedValuesManager.getProjectPsiDependentCache(pom,
				MavenProjectMetadataPropertyResolver::new);
	}

	/**
	 * Register every value-carrying leaf tag under its dot path. The first
	 * occurrence of a path wins, matching document order.
	 */
	private void registerProjectTags(XmlTag rootTag) {

		for (XmlTag tag : SyntaxTraverser.psiTraverser(rootTag).filter(XmlTag.class)) {

			if (tag == rootTag || !tag.isValid() || tag.getSubTags().length > 0) {
				continue;
			}

			String text = tag.getValue().getTrimmedText();
			if (!StringUtils.hasText(text)) {
				continue;
			}

			String path = pathOf(tag, rootTag);
			if (path == null) {
				continue;
			}

			PropertyValue value = new PropertyValue(path, text, tag);
			properties.putIfAbsent("project." + path, value);
			properties.putIfAbsent("pom." + path, value);

			if (path.startsWith("properties.")) {
				properties.putIfAbsent(tag.getLocalName(), value);
			}
		}
	}

	/**
	 * Build the dot path of the tag relative to the root {@code <project>} tag, or
	 * return {@literal null} when the tag left the tree while traversing.
	 */
	private static @Nullable String pathOf(XmlTag tag, XmlTag rootTag) {

		StringBuilder path = new StringBuilder(tag.getLocalName());
		for (XmlTag parent = tag.getParentTag(); parent != rootTag; parent = parent.getParentTag()) {

			if (parent == null) {
				return null;
			}
			path.insert(0, '.').insert(0, parent.getLocalName());
		}
		return path.toString();
	}

	/**
	 * Register the plain-name placeholder for a project coordinate
	 * ({@code $}{@code {version}} style) and fall back to the {@code <parent>}
	 * declaration, so inherited coordinates resolve on POMs that do not declare
	 * them locally.
	 */
	private void registerEffectiveCoordinate(String coordinate) {

		PropertyValue effective = properties.get("project." + coordinate);
		if (effective == null) {
			effective = properties.get("project.parent." + coordinate);
		}
		if (effective == null) {
			return;
		}

		properties.putIfAbsent(coordinate, effective);
		properties.putIfAbsent("project." + coordinate, effective);
		properties.putIfAbsent("pom." + coordinate, effective);
	}

	/**
	 * Return the version declared locally in the POM, ignoring inheritance.
	 *
	 * @return the local {@code <version>}, or {@literal null} when the POM inherits
	 * its version from the parent.
	 */
	@Nullable PropertyValue getVersion() {
		return this.version;
	}

	/**
	 * Return the version declared by the {@code <parent>} element.
	 *
	 * @return the {@code <parent><version>}, or {@literal null} when the POM has no
	 * parent version.
	 */
	@Nullable PropertyValue getParentVersion() {
		return this.parentVersion;
	}

	@Override
	public @Nullable String getProperty(String key) {
		PropertyValue value = getPropertyValue(key);
		return value != null ? value.getValue() : null;
	}

	@Override
	public boolean containsProperty(String key) {
		return properties.containsKey(key);
	}

	@Override
	public @Nullable PropertyValue getPropertyValue(String key) {
		return properties.get(key);
	}

}
