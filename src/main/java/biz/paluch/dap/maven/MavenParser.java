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
import java.util.Map;
import java.util.function.BiConsumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactUsage;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jspecify.annotations.Nullable;

/**
 * Parser for Maven files.
 *
 * @author Mark Paluch
 */
class MavenParser {

	private final DependencyCollector collector;

	private final Map<String, String> properties;

	/**
	 * Create a new {@code MavenParser}.
	 * @param collector the dependency collector to populate.
	 * @param properties known Maven properties.
	 */
	public MavenParser(DependencyCollector collector, Map<String, String> properties) {
		this.collector = collector;
		this.properties = new HashMap<>(properties);
	}

	/**
	 * Parse Maven properties from the given {@link PsiFile}.
	 */
	public static Map<String, PropertyValue> parseProperties(XmlFile pomFile) {

		Map<String, PropertyValue> result = new LinkedHashMap<>();
		XmlTag root = pomFile.getDocument().getRootTag();
		if (root == null) {
			return result;
		}

		for (XmlTag profiles : root.findSubTags("profiles")) {
			for (XmlTag profile : profiles.findSubTags("profile")) {
				for (XmlTag properties : profile.findSubTags("properties")) {
					collectProperties(properties, result);
				}
			}
		}

		for (XmlTag properties : root.findSubTags("properties")) {
			collectProperties(properties, result);
		}

		return result;
	}

	/**
	 * Parse Maven properties from the given {@link PsiFile}.
	 */
	public static Map<String, String> getProperties(XmlFile pomFile) {

		Map<String, String> result = new LinkedHashMap<>();
		parseProperties(pomFile).forEach((k, v) -> result.put(k, v.getValue()));

		return result;
	}

	private static void collectProperties(XmlTag properties, Map<String, PropertyValue> target) {

		for (XmlTag child : properties.getSubTags()) {
			String name = child.getLocalName();
			if (StringUtils.isEmpty(name)) {
				continue;
			}
			target.put(name.trim(), new PropertyValue(name, child.getValue().getTrimmedText().trim(), child));
		}
	}

	/**
	 * Parse dependencies, plugins, and properties from the given POM file.
	 * @param cache the project cache used for property-to-artifact associations.
	 * @param pomFile the POM file to parse.
	 */
	public void parsePomFile(Cache cache, XmlFile pomFile) {

		Map<String, PropertyValue> properties = parseProperties(pomFile);
		collector.addProperties(properties.keySet());

		PropertyResolver resolver = new MavenProjectMetadataPropertyResolver(pomFile)
				.withFallback(PropertyResolver.fromMap(properties))
				.withFallback(this.properties::get);

		doParsePomFile(cache, pomFile, properties, resolver);
	}

	/**
	 * Parse dependencies, plugins, and properties from the given POM file.
	 * @param cache the project cache used for property-to-artifact associations.
	 * @param pomFile the POM file to parse.
	 */
	public void parsePomFile(Cache cache, XmlFile pomFile, PropertyResolver propertyResolver) {

		Map<String, PropertyValue> properties = parseProperties(pomFile);
		collector.addProperties(properties.keySet());

		doParsePomFile(cache, pomFile, properties, propertyResolver);
	}

	/**
	 * Parse dependencies, plugins, and properties from the given POM file.
	 * @param cache the project cache used for property-to-artifact associations.
	 * @param pomFile the POM file to parse.
	 * @param properties declared within the POM file.
	 * @param propertyResolver the property resolver to use.
	 */
	public void doParsePomFile(Cache cache, XmlFile pomFile, Map<String, PropertyValue> properties,
			PropertyResolver propertyResolver) {

		doWithArtifacts(propertyResolver, pomFile, (coordinate, usage) -> {

			if (usage.version() instanceof VersionSource.VersionProperty versionProperty) {

				String value = propertyResolver.getProperty(versionProperty.getProperty());
				if (StringUtils.hasText(value)) {
					ArtifactVersion.from(value).ifPresent(it -> {
						collector.registerUsage(coordinate, it, usage.declaration(), usage.version());
					});
				}
				collector.registerDeclaration(coordinate, usage.declaration(), usage.version());
			} else if (usage.version() instanceof VersionSource.DeclaredVersion declared) {
				ArtifactVersion.from(declared.getVersion()).ifPresent(it -> {
					collector.registerUsage(coordinate, it, usage.declaration(), usage.version());
				});
				collector.registerDeclaration(coordinate, usage.declaration(), declared);
			}
		});

		cache.doWithProperties(property -> {
			if (property.hasArtifacts() && properties.containsKey(property.name())) {

				String value = propertyResolver.getProperty(property.name());

				if (StringUtils.isEmpty(value)) {
					return;
				}

				ArtifactVersion.from(value).ifPresent(version -> {
					for (CachedArtifact artifact : property.artifacts()) {
						collector.registerUsage(artifact.toArtifactId(), version, DeclarationSource.managed(),
								VersionSource.property(property.name()));
					}
				});
			}
		});
	}

	/**
	 * Parse artifact coordinates from the given XML tag.
	 * @param tag the dependency or plugin tag.
	 * @param propertyResolver resolver for Maven placeholders.
	 * @return the artifact id, or {@code null} if no artifact id is present.
	 */
	public static @Nullable ArtifactId parseArtifactId(XmlTag tag, PropertyResolver propertyResolver) {
		return parseArtifactId(tag.getSubTagText("groupId"), tag.getSubTagText("artifactId"), propertyResolver);
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

	private void doWithDependency(PropertyResolver resolver, XmlTag tag, DeclarationSource declarationSource,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {

		ArtifactId artifactId = parseArtifactId(tag, resolver);

		if (artifactId == null) {
			return;
		}

		VersionSource versionSource = getVersionSource(tag.getSubTagText("version"));
		callback.accept(artifactId, new ArtifactUsage(declarationSource, versionSource));
	}

	private VersionSource getVersionSource(@Nullable String version) {

		if (StringUtils.hasText(version)) {
			Expression expression = Expression.from(version);
			if (expression.isProperty()) {
				return VersionSource.property(expression.getPropertyName());
			}
			return VersionSource.declared(version);
		}

		return VersionSource.none();
	}

	private void doWithArtifacts(PropertyResolver resolver, XmlFile pomFile,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {

		XmlTag root = pomFile.getDocument().getRootTag();
		if (root == null) {
			return;
		}

		for (XmlTag dependencyManagement : root.findSubTags("dependencyManagement")) {
			doWithDependencies(dependencyManagement, resolver, DeclarationSource.managed(), callback);
		}

		doWithDependencies(root, resolver, DeclarationSource.dependency(), callback);

		for (XmlTag build : root.findSubTags("build")) {
			for (XmlTag pluginManagement : build.findSubTags("pluginManagement")) {
				doWithPlugins(resolver, DeclarationSource.pluginManagement(), callback, pluginManagement);
			}
			doWithPlugins(resolver, DeclarationSource.plugin(), callback, build);
		}

		for (XmlTag profiles : root.findSubTags("profiles")) {
			for (XmlTag profile : profiles.findSubTags("profiles")) {

				String id = profile.getSubTagText("id");
				if (StringUtils.isEmpty(id)) {
					continue;
				}

				for (XmlTag dependencyManagement : root.findSubTags("dependencyManagement")) {
					doWithDependencies(dependencyManagement, resolver, DeclarationSource.profileManaged(id), callback);
				}
				doWithDependencies(root, resolver, DeclarationSource.profileDependency(id), callback);

				for (XmlTag build : root.findSubTags("build")) {
					for (XmlTag pluginManagement : build.findSubTags("pluginManagement")) {
						doWithPlugins(resolver, DeclarationSource.profilePluginManagement(id), callback,
								pluginManagement);
					}
					doWithPlugins(resolver, DeclarationSource.profilePlugin(id), callback, build);
				}
			}
		}
	}

	private void doWithDependencies(XmlTag root, PropertyResolver resolver, DeclarationSource declarationSource,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {
		for (XmlTag dependencies : root.findSubTags("dependencies")) {
			for (XmlTag dependency : dependencies.findSubTags("dependency")) {
				doWithDependency(resolver, dependency, declarationSource, callback);
			}
		}
	}

	private void doWithPlugins(PropertyResolver resolver, DeclarationSource declarationSource,
			BiConsumer<ArtifactId, ArtifactUsage> callback, XmlTag build) {
		for (XmlTag plugins : build.findSubTags("plugins")) {
			for (XmlTag plugin : plugins.findSubTags("plugin")) {
				doWithDependency(resolver, plugin, declarationSource, callback);
			}
		}
	}


}
