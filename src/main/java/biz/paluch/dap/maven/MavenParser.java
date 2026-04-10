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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactUsage;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.xml.PomDependency;
import biz.paluch.dap.xml.PomProfile;
import biz.paluch.dap.xml.PomProjection;
import biz.paluch.dap.xml.PomProperty;
import biz.paluch.dap.xml.XmlBeamProjectorFactory;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

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
	public static Map<String, String> parseProperties(PsiFile pomFile) {

		PomProjection pom = project(pomFile);
		Map<String, String> result = new HashMap<>();

		for (PomProfile profile : pom.getProfiles()) {
			for (PomProperty property : profile.getProperties()) {
				result.put(property.getName().trim(), property.getValue().trim());
			}
		}

		for (PomProperty property : pom.getProperties()) {
			result.put(property.getName().trim(), property.getValue().trim());
		}

		return result;
	}

	public void parsePomFile(Cache cache, XmlFile pomFile) {

		Map<String, String> properties = parseProperties(pomFile);
		this.properties.putAll(properties);
		collector.addProperties(properties.keySet());

		PropertyResolver resolver = new PropertyResolver(pomFile, this.properties);

		PomProjection projection = project(pomFile);
		doWithArtifacts(resolver, projection, (coordinate, usage) -> {

			if (usage.version() instanceof VersionSource.VersionPropertySource vps) {

				if (properties.containsKey(vps.getProperty())) {

					String version = properties.get(vps.getProperty());
					ArtifactVersion.from(version).ifPresent(it -> {
						collector.registerUpdateCandidate(coordinate, it, usage.declaration(), vps);
					});
				}
			} else if (usage.version() instanceof VersionSource.DeclaredVersion declared) {
				ArtifactVersion.from(declared.getVersion()).ifPresent(it -> {
					collector.registerUpdateCandidate(coordinate, it, usage.declaration(), usage.version());
				});
			}

			collector.add(coordinate, usage);
		});

		cache.doWithProperties(property -> {
			if (property.hasArtifacts() && properties.containsKey(property.name())) {

				String value = this.properties.get(property.name());

				if (!StringUtils.hasText(value)) {
					return;
				}

				ArtifactVersion.from(value).ifPresent(version -> {
					for (CachedArtifact artifact : property.artifacts()) {
						collector.registerUpdateCandidate(artifact.toArtifactId(), version, DeclarationSource.managed(),
								VersionSource.property(property.name()));
					}
				});
			}
		});
	}

	private void doWithDependency(PropertyResolver resolver, PomDependency dependency,
			DeclarationSource declarationSource,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {

		String g = dependency.getGroupId();
		g = StringUtils.hasText(g) ? g : "org.apache.maven.plugins";
		String a = dependency.getArtifactId();

		if (a != null && a.contains("${")) {
			a = resolver.resolvePropertyValue(a);
		}

		if (g.contains("${")) {
			g = resolver.resolvePropertyValue(g);
		}

		VersionSource versionSource = getVersionSource(dependency.getVersion());
		if (a != null) {
			callback.accept(ArtifactId.of(g, a), new ArtifactUsage(declarationSource, versionSource));
		}
	}

	private VersionSource getVersionSource(@Nullable String version) {

		if (StringUtils.hasText(version)) {
			if (version.startsWith("${") && version.endsWith("}")) {
				version = version.substring(2, version.length() - 1);
				return VersionSource.property(version);
			}
			return VersionSource.declared(version);
		}

		return VersionSource.none();
	}

	private static PomProjection project(PsiFile pomFile) {
		return XmlBeamProjectorFactory.INSTANCE.projectXMLString(pomFile.getText(), PomProjection.class);
	}

	private void doWithArtifacts(PropertyResolver resolver, PomProjection pom,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {

		for (PomDependency dep : pom.getDependencyManagementDependencies()) {
			doWithDependency(resolver, dep, DeclarationSource.managed(), callback);
		}

		for (PomDependency dep : pom.getDependencies()) {
			doWithDependency(resolver, dep, DeclarationSource.dependency(), callback);
		}

		for (PomDependency plugin : pom.getBuildPluginManagementPlugins()) {
			doWithDependency(resolver, plugin, DeclarationSource.pluginManagement(), callback);
		}

		for (PomDependency plugin : pom.getBuildPlugins()) {
			doWithDependency(resolver, plugin, DeclarationSource.plugin(), callback);
		}

		List<PomProfile> profiles = pom.getProfiles();
		for (PomProfile profile : profiles) {

			String id = profile.getId();

			for (PomDependency dep : profile.getDependencyManagementDependencies()) {
				doWithDependency(resolver, dep, DeclarationSource.profileManaged(id), callback);
			}

			for (PomDependency dep : profile.getDependencies()) {
				doWithDependency(resolver, dep, DeclarationSource.profileDependency(id), callback);
			}

			for (PomDependency plugin : profile.getBuildPluginManagementPlugins()) {
				doWithDependency(resolver, plugin, DeclarationSource.profilePluginManagement(id), callback);
			}

			for (PomDependency plugin : profile.getBuildPlugins()) {
				doWithDependency(resolver, plugin, DeclarationSource.profilePlugin(id), callback);
			}
		}
	}

	record ResolvedProperty(String name, String value, VersionSource versionSource) {
		public boolean containsProperty() {
			return value.contains("${");
		}
	}

	static class PropertyResolver {

		private final Map<String, String> properties;

		private final @Nullable String artifactId;
		private final @Nullable String groupId;
		private final @Nullable String version;

		public PropertyResolver(XmlFile pom, Map<String, String> properties) {

			this.properties = properties;

			XmlTag rootTag = pom.getDocument().getRootTag();

			String artifactId = rootTag.getSubTagText("artifactId");
			String groupId = rootTag.getSubTagText("groupId");
			String version = rootTag.getSubTagText("version");

			XmlTag parent = rootTag.findFirstSubTag("parent");
			if (!StringUtils.hasText(groupId) && parent != null) {
				groupId = parent.getSubTagText("groupId");
			}

			if (!StringUtils.hasText(version) && parent != null) {
				version = parent.getSubTagText("version");
			}

			this.artifactId = artifactId;
			this.groupId = groupId;
			this.version = version;
		}

		private MavenParser.@Nullable ResolvedProperty resolveProperty(String property) {
			return resolveProperty(property, new LinkedHashSet<>());
		}

		private MavenParser.@Nullable ResolvedProperty resolveProperty(String property, Set<String> visited) {

			if (property.startsWith("${") && property.endsWith("}")) {
				property = property.substring(2, property.length() - 1);
			}

			if (!visited.add(property)) {
				return null;
			}

			String s = properties.get(property);

			if (s == null) {
				s = resolveKnownProperty(property);
			}

			ResolvedProperty value = s != null ? new ResolvedProperty(property, s, VersionSource.property(property)) : null;

			if (value != null) {

				if (value.containsProperty()) {
					return resolveProperty(value.value().trim(), visited);
				}

				return new ResolvedProperty(property, value.value().trim(), VersionSource.property(property));
			}

			return null;
		}

		private @Nullable String resolvePropertyValue(String property) {

			Matcher matcher = PROPERTY_PATTERN.matcher(property);
			String result = property;
			while (matcher.find()) {

				String name = matcher.group(1);
				ResolvedProperty pomProperty = resolveProperty(name);
				if (pomProperty != null) {
					result = matcher.replaceFirst(pomProperty.value());
					matcher = PROPERTY_PATTERN.matcher(result);
				}
			}

			return result;
		}

		private @Nullable String resolveKnownProperty(String property) {

			if (property.equals("artifactId") || property.equals("project.artifactId")) {
				return artifactId;
			}

			if (property.equals("groupId") || property.equals("project.groupId")) {
				return groupId;
			}

			if (property.equals("version") || property.equals("project.version")) {
				return version;
			}

			return null;
		}

	}

}
