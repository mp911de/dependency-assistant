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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;

/**
 * Parser for Maven files.
 *
 * @author Mark Paluch
 */
class MavenParser {

	private final DependencyCollector collector;

	private final PropertyResolver propertyResolver;

	/**
	 * Create a new {@code MavenParser}.
	 * @param collector the dependency collector to populate.
	 */
	public MavenParser(DependencyCollector collector) {
		this(collector, PropertyResolver.empty());
	}

	/**
	 * Create a new {@code MavenParser}.
	 * @param collector the dependency collector to populate.
	 * @param propertyResolver Maven property resolver.
	 */
	public MavenParser(DependencyCollector collector, PropertyResolver propertyResolver) {
		this.collector = collector;
		this.propertyResolver = propertyResolver;
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

		doWithProfiles(root, profile -> {
			for (XmlTag properties : profile.findSubTags("properties")) {
				collectProperties(properties, result);
			}
		});

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
	 * Parse Maven repositories from the given {@link PsiFile}.
	 * @param pomFile the Maven POM file.
	 * @return list of repositories.
	 */
	public static List<MavenRemoteRepository> parseRepositories(PsiFile pomFile) {

		List<MavenRemoteRepository> repositories = new ArrayList<>();

		if (!(pomFile instanceof XmlFile xmlFile)) {
			return repositories;
		}

		XmlTag root = xmlFile.getDocument() != null ? xmlFile.getDocument().getRootTag() : null;
		if (root == null) {
			return repositories;
		}

		collectRepositories(root, repositories);
		doWithProfiles(root, profile -> collectRepositories(profile, repositories));

		return repositories;
	}

	private static void collectRepositories(XmlTag parent, List<MavenRemoteRepository> target) {
		collectRepositories(parent, "repositories", "repository", target);
		collectRepositories(parent, "pluginRepositories", "pluginRepository", target);
	}

	private static void collectRepositories(XmlTag parent, String containerTag, String entryTag,
			List<MavenRemoteRepository> target) {

		for (XmlTag container : parent.findSubTags(containerTag)) {
			for (XmlTag entry : container.findSubTags(entryTag)) {
				target.add(toRemoteRepository(entry));
			}
		}
	}

	private static MavenRemoteRepository toRemoteRepository(XmlTag repository) {

		String id = text(repository, "id");
		String name = text(repository, "name");
		String url = text(repository, "url");
		String layout = text(repository, "layout");

		MavenRemoteRepository.Policy releases = parsePolicy(repository.findFirstSubTag("releases"));
		MavenRemoteRepository.Policy snapshots = parsePolicy(repository.findFirstSubTag("snapshots"));

		return new MavenRemoteRepository(id, name, url, layout, releases, snapshots);
	}

	private static MavenRemoteRepository.Policy parsePolicy(@Nullable XmlTag policy) {

		if (policy == null) {
			return new MavenRemoteRepository.Policy(true, "daily", "warn");
		}

		String enabled = text(policy, "enabled");
		String updatePolicy = text(policy, "updatePolicy");
		String checksumPolicy = text(policy, "checksumPolicy");

		return new MavenRemoteRepository.Policy(!"false".equals(enabled),
				updatePolicy.isEmpty() ? "daily" : updatePolicy,
				checksumPolicy.isEmpty() ? "warn" : checksumPolicy);
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
				.withFallback(this.propertyResolver);

		doParsePomFile(cache, pomFile, properties, resolver);
	}

	/**
	 * Parse dependencies from the given extensions file.
	 *
	 * @param extensionsFile the extensions file to parse.
	 */
	public void parseExtensionsFile(XmlFile extensionsFile) {
		doParseExtensionsFile(extensionsFile);
	}

	private void doParsePomFile(Cache cache, XmlFile pomFile, Map<String, PropertyValue> properties,
			PropertyResolver propertyResolver) {

		doWithArtifacts(propertyResolver, pomFile, (coordinate, usage) -> {

			if (usage.version() instanceof VersionSource.VersionProperty versionProperty) {

				String value = propertyResolver.getProperty(versionProperty.getProperty());
				if (StringUtils.hasText(value) && properties.containsKey(versionProperty.getProperty())) {
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

	private void doParseExtensionsFile(XmlFile pomFile) {

		XmlTag root = pomFile.getDocument().getRootTag();
		if (root == null || !"extensions".equals(root.getLocalName())) {
			return;
		}

		XmlTag[] extensions = root.findSubTags("extension");

		for (XmlTag extension : extensions) {
			doWithDependency(PropertyResolver.empty(), extension, DeclarationSource.dependency(),
					(artifactId, usage) -> {
						if (usage.version() instanceof VersionSource.DeclaredVersion declared) {
							ArtifactVersion.from(declared.getVersion()).ifPresent(it -> {
								collector.registerUsage(artifactId, it, usage.declaration(), usage.version());
							});
							collector.registerDeclaration(artifactId, usage.declaration(), declared);
						}
					});
		}
	}

	/**
	 * Parse artifact coordinates from the given XML tag.
	 * @param tag the dependency or plugin tag.
	 * @param propertyResolver resolver for Maven placeholders.
	 * @return the artifact id, or {@literal null} if no artifact id is present.
	 */
	public static @Nullable ArtifactId parseArtifactId(@Nullable XmlTag tag, PropertyResolver propertyResolver) {
		return tag != null
				? parseArtifactId(text(tag, "groupId"), text(tag, "artifactId"), propertyResolver)
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
		}

		return VersionSource.from(version);
	}

	private void doWithArtifacts(PropertyResolver resolver, XmlFile pomFile,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {

		XmlTag root = pomFile.getDocument().getRootTag();
		if (root == null) {
			return;
		}

		XmlTag parent = root.findFirstSubTag("parent");
		if (isParentDependencyCandidate(root, parent)) {
			doWithDependency(resolver, parent, getDeclarationSource(parent), callback);
		}

		doWithPluginsAndDependencies(resolver, callback, root);
		doWithProfiles(root, profile -> {
			String id = profile.getSubTagText("id");
			if (StringUtils.isEmpty(id)) {
				return;
			}
			doWithPluginsAndDependencies(resolver, callback, profile);
		});
	}

	private void doWithPluginsAndDependencies(PropertyResolver resolver, BiConsumer<ArtifactId, ArtifactUsage> callback,
			XmlTag root) {

		for (XmlTag dependencyManagement : root.findSubTags("dependencyManagement")) {
			doWithDependencies(dependencyManagement, resolver, callback);
		}

		doWithDependencies(root, resolver, callback);

		for (XmlTag build : root.findSubTags("build")) {
			for (XmlTag pluginManagement : build.findSubTags("pluginManagement")) {
				doWithPlugins(resolver, callback, pluginManagement);
			}
			doWithPlugins(resolver, callback, build);
			doWithExtensions(resolver, callback, build);
		}

		for (XmlTag reporting : root.findSubTags("reporting")) {
			doWithPlugins(resolver, callback, reporting);
		}
	}

	/**
	 * Return the {@link DeclarationSource} for the given dependency or plugin
	 * declaration tag.
	 * 
	 * @param owner the dependency, plugin, or extension tag to classify.
	 * @return the declaration source describing where the artifact is declared.
	 */
	public static DeclarationSource getDeclarationSource(XmlTag owner) {

		XmlTag profile = (XmlTag) PsiTreeUtil.findFirstParent(owner,
				psiElement -> psiElement instanceof XmlTag tag && "profile".equals(tag.getLocalName()));
		String profileId = profile != null ? text(profile, "id") : null;

		if (owner.getParentTag() instanceof XmlTag parent && parent.getParentTag() instanceof XmlTag grandParent) {

			if ("pluginManagement".equals(grandParent.getLocalName())) {
				return StringUtils.hasText(profileId) ? DeclarationSource.profilePluginManagement(profileId)
						: DeclarationSource.pluginManagement();
			}

			if ("dependencyManagement".equals(grandParent.getLocalName())) {
				return StringUtils.hasText(profileId) ? DeclarationSource.profileManaged(profileId)
						: DeclarationSource.managed();
			}
		}

		if ("plugin".equals(owner.getLocalName()) || "extension".equals(owner.getLocalName())) {
			return StringUtils.hasText(profileId) ? DeclarationSource.profilePlugin(profileId)
					: DeclarationSource.plugin();
		}

		return StringUtils.hasText(profileId) ? DeclarationSource.profileDependency(profileId)
				: DeclarationSource.dependency();
	}


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

		String groupId = text(root, "groupId");
		String parentGroupId = text(parent, "groupId");

		// inherited groupId indicates a local multi-module project
		if (StringUtils.isEmpty(groupId) || groupId.equals(parentGroupId)) {
			return false;
		}

		// skip local multi-module projects with relativePath
		if (StringUtils.hasText(text(parent, "relativePath"))) {
			return false;
		}

		return true;
	}

	private static void doWithProfiles(XmlTag root, Consumer<XmlTag> callback) {
		for (XmlTag profiles : root.findSubTags("profiles")) {
			for (XmlTag profile : profiles.findSubTags("profile")) {
				callback.accept(profile);
			}
		}
	}

	private void doWithDependencies(XmlTag root, PropertyResolver resolver,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {
		for (XmlTag dependencies : root.findSubTags("dependencies")) {
			for (XmlTag dependency : dependencies.findSubTags("dependency")) {
				doWithDependency(resolver, dependency, getDeclarationSource(dependency), callback);
			}
		}
	}

	private void doWithPlugins(PropertyResolver resolver,
			BiConsumer<ArtifactId, ArtifactUsage> callback, XmlTag build) {
		for (XmlTag plugins : build.findSubTags("plugins")) {
			for (XmlTag plugin : plugins.findSubTags("plugin")) {
				doWithDependency(resolver, plugin, getDeclarationSource(plugin), callback);
			}
		}
	}

	private void doWithExtensions(PropertyResolver resolver,
			BiConsumer<ArtifactId, ArtifactUsage> callback, XmlTag build) {
		for (XmlTag extensions : build.findSubTags("extensions")) {
			for (XmlTag extension : extensions.findSubTags("extension")) {
				if (StringUtils.isEmpty(extension.getSubTagText("groupId"))) {
					continue;
				}
				doWithDependency(resolver, extension, getDeclarationSource(extension), callback);
			}
		}
	}

	private static String text(XmlTag tag, String subTag) {
		String value = tag.getSubTagText(subTag);
		return value != null ? value.trim() : "";
	}

}
