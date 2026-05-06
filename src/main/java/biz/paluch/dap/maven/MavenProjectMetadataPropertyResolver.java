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

	/**
	 * Create a new {@code PropertyResolver} for {@link XmlFile}
	 * @param pom the POM file providing project coordinates.
	 */
	public MavenProjectMetadataPropertyResolver(XmlFile pom) {

		XmlTag rootTag = pom.getDocument().getRootTag();
		if (rootTag == null) {
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

		PropertyValue version = find(rootTag, "version");
		if (version != null) {
			properties.put("version", version);
			properties.put("project.version", version);
		}
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

}
