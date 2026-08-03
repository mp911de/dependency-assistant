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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * Effective Maven POM properties with child-first inheritance.
 *
 * <p>The POM itself is consulted first, followed by its direct parent and then
 * each remaining ancestor. The hierarchy walk is cycle-safe and preserves the
 * declaration PSI of the winning property.
 *
 * @author Mark Paluch
 */
class MavenPomProperties implements PropertyResolver {

	private static final MavenPomProperties EMPTY = new MavenPomProperties(PropertyResolver.empty());

	private final PropertyResolver delegate;

	private MavenPomProperties(PropertyResolver delegate) {
		this.delegate = delegate;
	}

	static MavenPomProperties forPom(XmlFile pom, PropertyResolver fallback) {
		return new MavenPomProperties(MavenProjectMetadataPropertyResolver.from(pom).withFallback(fallback));
	}

	static MavenPomProperties combined(XmlFile pom, List<XmlFile> parents) {

		List<XmlFile> pomsByPrecedence = new ArrayList<>();
		pomsByPrecedence.add(pom);
		pomsByPrecedence.addAll(parents);
		return combine(pomsByPrecedence, MavenProjectMetadataPropertyResolver::from);
	}

	static MavenPomProperties combine(List<XmlFile> pomsByPrecedence,
			Function<XmlFile, PropertyResolver> properties) {

		PropertyResolver combined = null;
		for (XmlFile pom : pomsByPrecedence) {
			PropertyResolver resolver = properties.apply(pom);
			combined = combined == null ? resolver : combined.withFallback(resolver);
		}
		return combined != null ? new MavenPomProperties(combined) : EMPTY;
	}

	static MavenPomProperties forProject(MavenProjectContext context, PsiFile pomFile) {

		if (context.isAbsent() || !(pomFile instanceof XmlFile xmlFile)) {
			return EMPTY;
		}

		List<MavenProject> projectAndParents = projectAndParents(context);
		List<XmlFile> parentPoms = new ArrayList<>();

		for (int i = 1; i < projectAndParents.size(); i++) {
			PsiFile parentPom = context.findFile(projectAndParents.get(i).getFile());
			if (parentPom instanceof XmlFile parentXml && MavenUtils.isMavenPomFile(parentXml)) {
				parentPoms.add(parentXml);
			}
		}

		MavenPomProperties projectProperties = combined(xmlFile, parentPoms);
		MavenProject mavenProject = context.getMavenProject();
		if (context.getProjectsManager().findProject(mavenProject.getFile()) == null) {
			return projectProperties;
		}
		Properties modelProperties = mavenProject.getProperties();
		return new MavenPomProperties(projectProperties.delegate.withFallback(modelProperties::getProperty));
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

	static List<Property> getDeclaredProperties(XmlFile pom) {

		List<Property> properties = new ArrayList<>();
		MavenPomSupport.doWithRoot(pom, root -> {
			MavenPomSupport.PomTag project = MavenPomSupport.PomTag.of(root);
			project.subtags(MavenPomSupport.PROPERTIES)
					.forEach(tag -> collectDeclaredProperties(tag, properties));
			MavenPomSupport.doWithProfiles(project, profile -> profile.subtags(MavenPomSupport.PROPERTIES)
					.forEach(tag -> collectDeclaredProperties(tag, properties)));
		});
		return properties;
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

	private static List<MavenProject> projectAndParents(MavenProjectContext context) {

		List<MavenProject> hierarchy = new ArrayList<>();
		Set<MavenId> visited = new HashSet<>();
		MavenProject current = context.getMavenProject();

		while (current != null && visited.add(current.getMavenId())) {
			hierarchy.add(current);
			MavenId parentId = current.getParentId();
			current = parentId != null ? context.getProjectsManager().findProject(parentId) : null;
		}
		return hierarchy;
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
