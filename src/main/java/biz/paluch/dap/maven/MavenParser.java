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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactUsage;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.support.PropertyExpression;
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

	private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]*)\\}");

	private final DependencyCollector collector;

	private final Map<String, String> properties;

	public MavenParser(DependencyCollector collector, Map<String, String> properties) {
		this.collector = collector;
		this.properties = new HashMap<>(properties);
	}

	/**
	 * Parse Maven properties from the given {@link PsiFile}.
	 */
	public static Map<String, MavenProperty> parseProperties(XmlFile pomFile) {

		Map<String, MavenProperty> result = new LinkedHashMap<>();
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
		parseProperties(pomFile).forEach((k, v) -> result.put(k, v.value()));

		return result;
	}

	private static void collectProperties(XmlTag properties, Map<String, MavenProperty> target) {

		for (XmlTag child : properties.getSubTags()) {
			String name = child.getLocalName();
			if (StringUtils.isEmpty(name)) {
				continue;
			}
			target.put(name.trim(), new MavenProperty(name, child.getValue().getTrimmedText().trim(), child));
		}
	}

	public void parsePomFile(Cache cache, XmlFile pomFile) {

		Map<String, String> properties = getProperties(pomFile);
		this.properties.putAll(properties);
		collector.addProperties(properties.keySet());

		PropertyResolver resolver = new PropertyResolver(pomFile, this.properties);

		doWithArtifacts(resolver, pomFile, (coordinate, usage) -> {

			if (usage.version() instanceof VersionSource.VersionProperty versionProperty) {

				if (this.properties.containsKey(versionProperty.getProperty())) {

					String version = this.properties.get(versionProperty.getProperty());
					ArtifactVersion.from(version).ifPresent(it -> {
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

				String value = this.properties.get(property.name());

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

	public static @Nullable ArtifactId parseArtifactId(XmlTag tag, Function<String, String> propertyResolver) {
		return parseArtifactId(tag.getSubTagText("groupId"), tag.getSubTagText("artifactId"), propertyResolver);
	}

	public static @Nullable ArtifactId parseArtifactId(@Nullable String groupId, @Nullable String artifactId,
			Function<String, String> propertyResolver) {

		if (StringUtils.isEmpty(artifactId)) {
			return null;
		}

		groupId = StringUtils.hasText(groupId) ? groupId : "org.apache.maven.plugins";

		if (artifactId.contains("${") || artifactId.contains("}")) {
			artifactId = propertyResolver.apply(artifactId);
		}

		if (groupId.contains("${") || groupId.contains("}")) {
			groupId = propertyResolver.apply(groupId);
		}

		return ArtifactId.of(groupId, artifactId);
	}

	private void doWithDependency(PropertyResolver resolver, XmlTag tag, DeclarationSource declarationSource,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {

		ArtifactId artifactId = parseArtifactId(tag, resolver::resolvePropertyValue);

		if (artifactId == null) {
			return;
		}

		VersionSource versionSource = getVersionSource(tag.getSubTagText("version"));
		callback.accept(artifactId, new ArtifactUsage(declarationSource, versionSource));
	}

	private VersionSource getVersionSource(@Nullable String version) {

		if (StringUtils.hasText(version)) {
			PropertyExpression expression = PropertyExpression.from(version);
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

	record ResolvedProperty(String name, String value, VersionSource versionSource) {

		public boolean containsProperty() {
			return value.contains("${");
		}

	}

	/**
	 * Property resolver within the scope of a single pom file.
	 */
	static class PropertyResolver {

		private final Function<String, @Nullable String> propertySource;

		private final @Nullable String artifactId;

		private final @Nullable String groupId;

		private final @Nullable String version;

		public PropertyResolver(XmlFile pom, Map<String, String> properties) {
			this(pom, properties::get);
		}

		public PropertyResolver(XmlFile pom, Function<String, @Nullable String> propertySource) {

			this.propertySource = propertySource;

			XmlTag rootTag = pom.getDocument().getRootTag();
			if (rootTag == null) {
				this.artifactId = null;
				this.groupId = null;
				this.version = null;
				return;
			}

			String artifactId = rootTag.getSubTagText("artifactId");
			String groupId = rootTag.getSubTagText("groupId");
			String version = rootTag.getSubTagText("version");

			XmlTag parent = rootTag.findFirstSubTag("parent");
			if (StringUtils.isEmpty(groupId) && parent != null) {
				groupId = parent.getSubTagText("groupId");
			}

			if (StringUtils.isEmpty(version) && parent != null) {
				version = parent.getSubTagText("version");
			}

			this.artifactId = artifactId;
			this.groupId = groupId;
			this.version = version;
		}

		public String resolvePropertyValue(String property) {

			Matcher matcher = PROPERTY_PATTERN.matcher(property);
			String result = property;
			while (matcher.find()) {

				String name = matcher.group(1);
				ResolvedProperty pomProperty = resolveProperty(name, new LinkedHashSet<>());
				if (pomProperty != null) {
					result = matcher.replaceFirst(Matcher.quoteReplacement(pomProperty.value()));
					matcher = PROPERTY_PATTERN.matcher(result);
				}
			}

			return result;
		}

		private MavenParser.@Nullable ResolvedProperty resolveProperty(String property, Set<String> cycleGuard) {

			if (property.startsWith("${") && property.endsWith("}")) {
				property = property.substring(2, property.length() - 1);
			}

			if (!cycleGuard.add(property)) {
				return null;
			}

			String value = resolveKnownProperty(property);

			if (value == null) {
				value = propertySource.apply(property);
			}

			if (StringUtils.isEmpty(value)) {
				return null;
			}

			ResolvedProperty resolvedProperty = new ResolvedProperty(property, value, VersionSource.property(property));

			if (resolvedProperty.containsProperty()) {
				return resolveProperty(resolvedProperty.value().trim(), cycleGuard);
			}

			return new ResolvedProperty(property, resolvedProperty.value().trim(), VersionSource.property(property));
		}

		private @Nullable String resolveKnownProperty(String property) {
			return switch (property) {
			case "artifactId", "project.artifactId" -> artifactId;
			case "groupId", "project.groupId" -> groupId;
			case "version", "project.version" -> version;
			default -> null;
			};
		}

	}

	record MavenProperty(String key, String value, XmlTag valueElement) {

	}

}
