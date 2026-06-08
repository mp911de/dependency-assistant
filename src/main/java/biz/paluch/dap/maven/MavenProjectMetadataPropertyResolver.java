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
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import org.jspecify.annotations.Nullable;

/**
 * Maven property resolver using Maven POM project metadata like {@code groupId}
 * and {@code artifactId}.
 *
 * @author Mark Paluch
 */
class MavenProjectMetadataPropertyResolver implements PropertyResolver {

	private final Map<String, PropertyValue> properties = new HashMap<>();

	private final @Nullable PropertyValue version;

	private final @Nullable PropertyValue parentVersion;

	/**
	 * Create a new {@code PropertyResolver} for {@link XmlFile}
	 * @param pom the POM file providing project coordinates.
	 */
	public MavenProjectMetadataPropertyResolver(XmlFile pom) {

		XmlTag rootTag = pom.getDocument().getRootTag();
		if (rootTag == null) {
			this.version = null;
			this.parentVersion = null;
			return;
		}

		PropertyValue artifactId = find(rootTag, "artifactId");
		if (artifactId != null) {
			properties.put("artifactId", artifactId);
			properties.put("project.artifactId", artifactId);
		}

		PropertyValue groupId = find(rootTag, "groupId");
		if (groupId != null) {
			properties.put("groupId", groupId);
			properties.put("project.groupId", groupId);
		}

		this.version = findDirect(rootTag, "version");
		this.parentVersion = findParent(rootTag, "version");

		PropertyValue effectiveVersion = this.version != null ? this.version : this.parentVersion;
		if (effectiveVersion != null) {
			properties.put("version", effectiveVersion);
			properties.put("project.version", effectiveVersion);
		}
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

	private @Nullable PropertyValue find(XmlTag tag, String tagName) {

		PropertyValue direct = findDirect(tag, tagName);
		if (direct != null) {
			return direct;
		}
		return findParent(tag, tagName);
	}

	private @Nullable PropertyValue findParent(XmlTag tag, String tagName) {

		for (XmlTag parent : tag.findSubTags("parent")) {
			if (!parent.isValid()) {
				continue;
			}
			PropertyValue property = find(parent, tagName);
			if (property != null) {
				return property;
			}
		}
		return null;
	}

	private static @Nullable PropertyValue findDirect(XmlTag tag, String tagName) {

		for (XmlTag subTag : tag.findSubTags(tagName)) {
			if (!subTag.isValid()) {
				continue;
			}
			XmlTagValue value = subTag.getValue();
			String text = value.getTrimmedText();
			if (StringUtils.hasText(text)) {
				return new PropertyValue(tagName, text, subTag);
			}
		}
		return null;
	}

}
