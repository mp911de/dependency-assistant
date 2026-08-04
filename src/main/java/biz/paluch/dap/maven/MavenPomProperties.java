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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import biz.paluch.dap.support.Property;
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
 * Maven POM properties with child-first inheritance.
 *
 * <p>Every value-carrying POM element is exposed under its {@code project.}
 * path and legacy {@code pom.} alias. Entries from {@code <properties>} are
 * additionally exposed under their plain name. When multiple POMs are combined,
 * the first POM takes precedence, followed by each remaining ancestor.
 *
 * @author Mark Paluch
 */
class MavenPomProperties implements PropertyResolver {

	private static final MavenPomProperties EMPTY = new MavenPomProperties(PropertyResolver.empty());

	private final PropertyResolver delegate;

	private MavenPomProperties(PropertyResolver delegate) {
		this.delegate = delegate;
	}

	static MavenPomProperties from(XmlFile pom, PropertyResolver fallback) {
		return from(pom).withFallback(fallback);
	}

	static MavenPomProperties from(XmlFile pom) {
		return CachedValuesManager.getProjectPsiDependentCache(pom,
				it -> new MavenPomProperties(readProjectProperties(it)));
	}

	static MavenPomProperties from(List<XmlFile> pomFiles) {
		return from(pomFiles, MavenPomProperties::from);
	}

	static MavenPomProperties from(List<XmlFile> pomsByPrecedence,
			Function<XmlFile, PropertyResolver> properties) {

		PropertyResolver combined = null;
		for (XmlFile pom : pomsByPrecedence) {
			PropertyResolver resolver = properties.apply(pom);
			combined = combined == null ? resolver : combined.withFallback(resolver);
		}
		return combined != null ? new MavenPomProperties(combined) : EMPTY;
	}

	static MavenPomProperties empty() {
		return EMPTY;
	}

	@Override
	public MavenPomProperties withFallback(PropertyResolver fallback) {
		return new MavenPomProperties(this.delegate.withFallback(fallback));
	}

	private static PropertyResolver readProjectProperties(XmlFile pom) {

		Map<String, PropertyValue> properties = new HashMap<>();
		XmlDocument document = pom.getDocument();
		XmlTag rootTag = document != null ? document.getRootTag() : null;
		if (rootTag != null) {
			registerProjectTags(rootTag, properties);
		}

		registerEffectiveCoordinate("groupId", properties);
		registerEffectiveCoordinate("artifactId", properties);
		registerEffectiveCoordinate("version", properties);
		return PropertyResolver.fromMap(properties);
	}

	/**
	 * Register every value-carrying leaf tag under its dot path. The first
	 * occurrence of a path wins, matching document order.
	 */
	private static void registerProjectTags(XmlTag rootTag, Map<String, PropertyValue> properties) {

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

	private static void registerEffectiveCoordinate(String coordinate,
			Map<String, PropertyValue> properties) {

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
	 * Return the property view that applies to the given pomMember. Properties of
	 * the enclosing profile take precedence over the project and inherited POM
	 * hierarchy, and properties from unrelated profiles are excluded.
	 */
	PropertyResolver forDeclaration(XmlTag pomMember) {

		XmlTag profile = findEnclosingProfile(pomMember);
		if (profile == null) {
			return this;
		}

		XmlTag propertiesTag = profile.findFirstSubTag(MavenPomSupport.PROPERTIES);
		if (propertiesTag == null) {
			return this;
		}

		Map<String, PropertyValue> properties = new LinkedHashMap<>();
		MavenPomSupport.collectProperties(MavenPomSupport.PomTag.of(propertiesTag), properties);
		return PropertyResolver.fromMap(properties).withFallback(this);
	}

	static @Nullable String profileId(Property property) {

		XmlTag declaration = property.getValueLiteral() instanceof XmlTag tag ? tag : null;
		XmlTag profile = declaration != null ? findEnclosingProfile(declaration) : null;
		String id = profile != null ? MavenPomSupport.Subtag.of(profile, MavenPomSupport.ID).getText() : null;
		return biz.paluch.dap.util.StringUtils.hasText(id) ? id : null;
	}

	private static void collectDeclaredProperties(MavenPomSupport.PomTag propertiesTag, List<Property> target) {

		Map<String, PropertyValue> properties = new LinkedHashMap<>();
		MavenPomSupport.collectProperties(propertiesTag, properties);
		target.addAll(properties.values());
	}

	private static @Nullable XmlTag findEnclosingProfile(XmlTag declaration) {

		for (XmlTag current = declaration; current != null; current = current.getParentTag()) {
			if (MavenPomSupport.PROFILE.equals(current.getLocalName())) {
				return current;
			}
		}
		return null;
	}

	@Override
	public boolean containsProperty(String key) {
		return delegate.containsProperty(key);
	}

	@Override
	public @Nullable String getProperty(String key) {
		return delegate.getProperty(key);
	}

	@Override
	public @Nullable Property getPropertyValue(String key) {
		return delegate.getPropertyValue(key);
	}

}
