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
import biz.paluch.dap.xml.PomDependency;
import biz.paluch.dap.xml.PomProfile;
import biz.paluch.dap.xml.PomProjection;
import biz.paluch.dap.xml.PomProperty;
import biz.paluch.dap.xml.XmlBeamProjectorFactory;

import java.io.IOException;
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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

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

	public void parsePomFile(PsiFile pomFile) {

		Map<String, String> properties = parseProperties(pomFile);
		this.properties.putAll(properties);
		collector.addProperties(properties.keySet());

		PomProjection projection = project(pomFile);
		doWithArtifacts(projection, (coordinate, usage) -> {

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
	}

	private void doWithDependency(PomDependency dependency, DeclarationSource declarationSource,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {

		String g = dependency.getGroupId();
		g = StringUtils.hasText(g) ? g : "org.apache.maven.plugins";
		String a = dependency.getArtifactId();

		if (a != null && a.contains("${")) {
			a = resolvePropertyValue(a);
		}

		if (g.contains("${")) {
			g = resolvePropertyValue(g);
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

	private PomProjection project(String pomContent) {
		return XmlBeamProjectorFactory.INSTANCE.projectXMLString(pomContent, PomProjection.class);
	}

	private PomProjection project(VirtualFile file) {
		try {
			return project(new String(file.contentsToByteArray(), file.getCharset()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void doWithArtifacts(PomProjection pom, BiConsumer<ArtifactId, ArtifactUsage> callback) {

		for (PomDependency dep : pom.getDependencyManagementDependencies()) {
			doWithDependency(dep, DeclarationSource.managed(), callback);
		}

		for (PomDependency dep : pom.getDependencies()) {
			doWithDependency(dep, DeclarationSource.dependency(), callback);
		}

		for (PomDependency plugin : pom.getBuildPluginManagementPlugins()) {
			doWithDependency(plugin, DeclarationSource.pluginManagement(), callback);
		}

		for (PomDependency plugin : pom.getBuildPlugins()) {
			doWithDependency(plugin, DeclarationSource.plugin(), callback);
		}

		List<PomProfile> profiles = pom.getProfiles();
		for (PomProfile profile : profiles) {

			String id = profile.getId();

			for (PomDependency dep : profile.getDependencyManagementDependencies()) {
				doWithDependency(dep, DeclarationSource.profileManaged(id), callback);
			}

			for (PomDependency dep : profile.getDependencies()) {
				doWithDependency(dep, DeclarationSource.profileDependency(id), callback);
			}

			for (PomDependency plugin : profile.getBuildPluginManagementPlugins()) {
				doWithDependency(plugin, DeclarationSource.profilePluginManagement(id), callback);
			}

			for (PomDependency plugin : profile.getBuildPlugins()) {
				doWithDependency(plugin, DeclarationSource.profilePlugin(id), callback);
			}
		}
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

	record ResolvedProperty(String name, String value, VersionSource versionSource) {
		public boolean containsProperty() {
			return value.contains("${");
		}
	}

}
