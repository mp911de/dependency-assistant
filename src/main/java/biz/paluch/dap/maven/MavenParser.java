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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactUsage;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.PsiElements;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiFile;
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

	private static final String EXTENSIONS = "extensions";

	private static final String EXTENSION = "extension";

	private static final String PLUGIN_MANAGEMENT = "pluginManagement";

	private static final String BUILD = "build";

	private static final String DEPENDENCY_MANAGEMENT = "dependencyManagement";

	private static final String REPORTING = "reporting";

	private static final String DEPENDENCIES = "dependencies";

	private static final String DEPENDENCY = "dependency";

	private static final String PLUGINS = "plugins";

	private static final String PLUGIN = "plugin";

	private static final String GROUP_ID = "groupId";

	private static final String PROFILE = "profile";

	private static final String ID = "id";

	private static final String PROPERTIES = "properties";

	private static final String ARTIFACT_ID = "artifactId";

	private static final String VERSION = "version";

	private final Cache cache;

	private final DependencyCollector collector;

	private final PropertyResolver propertyResolver;

	/**
	 * Create a new {@code MavenParser}.
	 * @param collector the dependency collector to populate.
	 */
	public MavenParser(DependencyCollector collector, Cache cache) {
		this(collector, cache, PropertyResolver.empty());
	}

	/**
	 * Create a new {@code MavenParser}.
	 *
	 * @param collector the dependency collector to populate.
	 * @param propertyResolver Maven property resolver.
	 */
	public MavenParser(DependencyCollector collector, Cache cache, PropertyResolver propertyResolver) {
		this.cache = cache;
		this.collector = collector;
		this.propertyResolver = propertyResolver;
	}

	/**
	 * Parse dependencies, plugins, and properties from the given POM file.
	 * @param pomFile the POM file to parse.
	 */
	public void parsePomFile(XmlFile pomFile) {

		Map<String, PropertyValue> properties = parseProperties(pomFile);
		collector.addProperties(properties.keySet());

		PropertyResolver resolver = MavenProjectMetadataPropertyResolver.from(pomFile)
				.withFallback(PropertyResolver.fromMap(properties))
				.withFallback(this.propertyResolver);

		doParsePomFile(pomFile, properties, resolver);
	}

	/**
	 * Parse dependencies from the given extensions file.
	 *
	 * @param extensionsFile the extensions file to parse.
	 */
	public void parseExtensionsFile(XmlFile extensionsFile) {
		doParseExtensionsFile(extensionsFile);
	}

	private void doParseExtensionsFile(XmlFile pomFile) {

		doWithRoot(pomFile, root -> {
			if (!EXTENSIONS.equals(root.getLocalName())) {
				return;
			}

			PomTag.of(root).subtags(EXTENSION).forEach(extension -> {

				doWithDependency(PropertyResolver.empty(), extension, DeclarationSource.dependency(),
						(artifactId, usage) -> {
							if (usage.version() instanceof VersionSource.DeclaredVersion declared) {
								ArtifactVersion.from(declared.getVersion())
										.ifPresent(it -> {
											collector.registerUsage(artifactId, it, usage.declaration(),
													usage.version());
										});
								collector.registerDeclaration(artifactId, usage.declaration(), usage.version());
							}
						});
			});
		});
	}

	private void doWithDependency(PropertyResolver resolver, PomTag tag, DeclarationSource declarationSource,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {

		ArtifactId artifactId = parseArtifactId(tag, resolver);

		if (artifactId == null) {
			return;
		}
		VersionSource versionSource = tag.subtag(VERSION)
				.eitherOr(version -> Expression.from(version).asVersionSource(), VersionSource::none);
		callback.accept(artifactId, new ArtifactUsage(declarationSource, versionSource));
	}

	private void doParsePomFile(XmlFile pomFile, Map<String, PropertyValue> properties,
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
				collector.registerDeclaration(coordinate, usage.declaration(), usage.version());
			} else if (!usage.version().isDefined() && !usage.declaration().isPlugin()) {
				// versionless dependencies count as use evidence for BOM members
				collector.registerDeclaration(coordinate, usage.declaration(), usage.version());
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

	private void doWithArtifacts(PropertyResolver resolver, XmlFile pomFile,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {

		doWithRoot(pomFile, root -> {

			PomTag pomTag = PomTag.of(root);
			XmlTag parent = root.findFirstSubTag("parent");
			if (isParentDependencyCandidate(root, parent)) {
				doWithDependency(resolver, PomTag.of(parent), getDeclarationSource(parent), callback);
			}

			doWithPluginsAndDependencies(resolver, callback, pomTag);
			doWithProfiles(pomTag, profile -> {

				Subtag id = profile.subtag(ID);
				if (id.isEmpty()) {
					return;
				}
				doWithPluginsAndDependencies(resolver, callback, profile);
			});
		});
	}

	private void doWithPluginsAndDependencies(PropertyResolver resolver, BiConsumer<ArtifactId, ArtifactUsage> callback,
			PomTag root) {

		root.subtags(DEPENDENCY_MANAGEMENT)
				.forEach(dependencyManagement -> doWithDependencies(dependencyManagement, resolver, callback));

		doWithDependencies(root, resolver, callback);

		root.subtags(BUILD).forEach(build -> {
			build.subtags(PLUGIN_MANAGEMENT)
					.forEach(pluginManagement -> doWithPlugins(resolver, callback, pluginManagement));
			doWithPlugins(resolver, callback, build);
			doWithExtensions(resolver, callback, build);
		});

		root.subtags(REPORTING).forEach(reporting -> doWithPlugins(resolver, callback, reporting));
	}

	private void doWithDependencies(PomTag root, PropertyResolver resolver,
			BiConsumer<ArtifactId, ArtifactUsage> callback) {
		root.subtags(DEPENDENCIES).subtags(DEPENDENCY).forEach(dependency -> {
			doWithDependency(resolver, dependency, getDeclarationSource(dependency.getTag(), resolver), callback);
		});
	}

	private void doWithPlugins(PropertyResolver resolver,
			BiConsumer<ArtifactId, ArtifactUsage> callback, PomTag build) {
		build.subtags(PLUGINS).subtags(PLUGIN).forEach(plugin -> {
			doWithDependency(resolver, plugin, getDeclarationSource(plugin.getTag()), callback);
		});
	}

	private void doWithExtensions(PropertyResolver resolver,
			BiConsumer<ArtifactId, ArtifactUsage> callback, PomTag build) {
		build.subtags(EXTENSIONS).subtags(EXTENSION).forEach(extension -> {
			if (extension.subtag(GROUP_ID).isPresent()) {
				doWithDependency(resolver, extension, getDeclarationSource(extension.getTag()), callback);
			}
		});
	}

	/**
	 * Return the {@link DeclarationSource} for the given dependency or plugin
	 * declaration tag. A dependency-management entry with {@code scope=import} and
	 * {@code type=pom} classifies as a Bill of Materials import.
	 *
	 * @param owner the dependency, plugin, or extension tag to classify.
	 * @return the declaration source describing where the artifact is declared.
	 */
	public DeclarationSource getDeclarationSource(XmlTag owner, PropertyResolver propertyResolver) {

		XmlTag profile = (XmlTag) PsiElements.findFirstParent(owner, false,
				psiElement -> psiElement instanceof XmlTag tag && PROFILE.equals(tag.getLocalName()));

		Subtag profileTag = Subtag.of(profile, ID);

		if (owner.getParentTag() instanceof XmlTag parent && parent.getParentTag() instanceof XmlTag grandParent) {

			if (DEPENDENCY_MANAGEMENT.equals(grandParent.getLocalName()) && isBomImport(owner)) {
				ArtifactId artifactId = parseArtifactId(PomTag.of(owner), propertyResolver);

				String versionText = Subtag.of(owner, VERSION).getText();
				if (artifactId != null && versionText != null) {
					String resolvedVersion = Expression.from(versionText).resolve(propertyResolver);
					if (!StringUtils.isEmpty(resolvedVersion) && !resolvedVersion.contains("${")) {

						return ArtifactVersion.from(resolvedVersion).map(bomVersion -> {

							Map<ArtifactId, ArtifactVersion> bom = BomUtil.resolveBom(cache, owner.getProject(),
									PackageIdentity.of(artifactId, PackageSystem.MAVEN), bomVersion);

							return profileTag.eitherOr(id -> DeclarationSource.profileBom(id, bom),
									() -> DeclarationSource.bom(bom));
						}).orElseGet(DeclarationSource::bom);
					}
				}
			}
		}

		return getDeclarationSource(owner);
	}

	private static boolean isBomImport(XmlTag dependency) {
		return Subtag.of(dependency, "scope").textEquals("import")
				&& Subtag.of(dependency, "type").textEquals("pom");
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

		Subtag groupId = Subtag.of(root, GROUP_ID);
		Subtag parentGroupId = Subtag.of(parent, GROUP_ID);

		// inherited groupId indicates a local multi-module project
		if (groupId.isEmpty() || groupId.textEquals(parentGroupId)) {
			return false;
		}

		Subtag relativePath = Subtag.of(parent, "relativePath");
		// skip local multi-module projects with relativePath
		return !relativePath.isPresent();
	}

	/**
	 * Parse artifact coordinates from the given XML tag.
	 * @param tag the dependency or plugin tag.
	 * @param propertyResolver resolver for Maven placeholders.
	 * @return the artifact id, or {@literal null} if no artifact id is present.
	 */
	public static @Nullable ArtifactId parseArtifactId(@Nullable XmlTag tag, PropertyResolver propertyResolver) {
		return tag != null
				? parseArtifactId(PomTag.of(tag), propertyResolver)
				: null;
	}

	private static @Nullable ArtifactId parseArtifactId(@Nullable PomTag tag, PropertyResolver propertyResolver) {
		return tag != null
				? parseArtifactId(tag.getGroupId(), tag.getArtifactId(), propertyResolver)
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

	/**
	 * Return the {@link DeclarationSource} for the given dependency or plugin
	 * declaration tag. A dependency-management entry with {@code scope=import} and
	 * {@code type=pom} classifies as a Bill of Materials import.
	 *
	 * @param owner the dependency, plugin, or extension tag to classify.
	 * @return the declaration source describing where the artifact is declared.
	 */
	public static DeclarationSource getDeclarationSource(XmlTag owner) {

		XmlTag profile = (XmlTag) PsiElements.findFirstParent(owner, false,
				psiElement -> psiElement instanceof XmlTag tag && PROFILE.equals(tag.getLocalName()));

		Subtag profileTag = Subtag.of(profile, ID);

		if (owner.getParentTag() instanceof XmlTag parent && parent.getParentTag() instanceof XmlTag grandParent) {

			if (PLUGIN_MANAGEMENT.equals(grandParent.getLocalName())) {
				return profileTag.eitherOr(DeclarationSource::profilePluginManagement,
						DeclarationSource::pluginManagement);
			}

			if (DEPENDENCY_MANAGEMENT.equals(grandParent.getLocalName())) {
				if (isBomImport(owner)) {
					return profileTag.eitherOr(DeclarationSource::profileBom, DeclarationSource::bom);
				}
				return profileTag.eitherOr(DeclarationSource::profileManaged, DeclarationSource::managed);
			}
		}

		if (PLUGIN.equals(owner.getLocalName()) || EXTENSION.equals(owner.getLocalName())) {
			return profileTag.eitherOr(DeclarationSource::profilePlugin, DeclarationSource::plugin);
		}

		return profileTag.eitherOr(DeclarationSource::profileDependency, DeclarationSource::dependency);
	}

	/**
	 * Parse Maven properties from the given {@link XmlFile}, returning each
	 * property mapped to its plain {@link String} value.
	 */
	public static Map<String, String> getProperties(XmlFile pomFile) {

		Map<String, String> result = new LinkedHashMap<>();
		parseProperties(pomFile).forEach((k, v) -> result.put(k, v.getValue()));

		return result;
	}

	/**
	 * Parse Maven properties from the given {@link XmlFile}, retaining the
	 * declaring PSI element of each property as a {@link PropertyValue}.
	 */
	public static Map<String, PropertyValue> parseProperties(XmlFile pomFile) {

		Map<String, PropertyValue> result = new LinkedHashMap<>();

		doWithRoot(pomFile, root -> {

			PomTag pomTag = PomTag.of(root);

			doWithProfiles(pomTag, profile -> {
				profile.subtags(PROPERTIES).forEach(properties -> collectProperties(properties, result));
			});
			pomTag.subtags(PROPERTIES).forEach(properties -> collectProperties(properties, result));
		});

		return result;
	}

	private static void doWithRoot(XmlFile file, Consumer<XmlTag> callback) {
		XmlTag rootTag = file.getRootTag();
		if (rootTag != null) {
			callback.accept(rootTag);
		}
	}

	private static void doWithProfiles(PomTag root, Consumer<PomTag> callback) {
		root.subtags("profiles").subtags(PROFILE).forEach(callback);
	}

	private static void collectProperties(PomTag properties, Map<String, PropertyValue> target) {

		for (XmlTag child : properties.getTag().getSubTags()) {
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

		doWithRoot(xmlFile, root -> {
			PomTag pomTag = PomTag.of(root);

			collectRepositories(pomTag, repositories);
			doWithProfiles(pomTag, profile -> collectRepositories(profile, repositories));

		});

		return repositories;
	}

	private static void collectRepositories(PomTag parent, List<MavenRemoteRepository> target) {
		collectRepositories(parent, "repositories", "repository", target);
		collectRepositories(parent, "pluginRepositories", "pluginRepository", target);
	}

	private static void collectRepositories(PomTag parent, String containerTag, String entryTag,
			List<MavenRemoteRepository> target) {
		parent.subtags(containerTag).subtags(entryTag)
				.forEach(entry -> target.add(toRemoteRepository(entry.getTag())));
	}

	private static MavenRemoteRepository toRemoteRepository(XmlTag repository) {

		String id = text(repository, ID);
		String name = text(repository, "name");
		String url = text(repository, "url");
		String layout = text(repository, "layout");

		MavenRemoteRepository.Policy releases = parsePolicy(repository.findFirstSubTag("releases"));
		MavenRemoteRepository.Policy snapshots = parsePolicy(repository.findFirstSubTag("snapshots"));

		return new MavenRemoteRepository(id, name, url, layout, releases, snapshots);
	}

	private static String text(XmlTag tag, String subTag) {
		String value = tag.getSubTagText(subTag);
		return value != null ? value.trim() : "";
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
	 * Flattened group of POM tags supporting further descent by tag name.
	 */
	private static class PomTags {

		private final XmlTag[] tags;

		private PomTags(XmlTag[] tags) {
			this.tags = tags;
		}

		public PomTags subtags(String qname) {
			return new PomTags(Arrays.stream(tags).flatMap(tag -> Arrays.stream(tag.findSubTags(qname)))
					.toArray(XmlTag[]::new));
		}

		public void forEach(Consumer<? super PomTag> action) {
			for (XmlTag tag : tags) {
				action.accept(PomTag.of(tag));
			}
		}

	}

	/**
	 * Read-only view over a POM tag with accessors for text-bearing subtags.
	 */
	private static class PomTag {

		private final XmlTag tag;

		private PomTag(XmlTag tag) {
			this.tag = tag;
		}

		public static PomTag of(XmlTag tag) {
			return new PomTag(tag);
		}

		public PomTags subtags(String qname) {
			return new PomTags(this.tag.findSubTags(qname));
		}

		public Subtag subtag(String qname) {
			return Subtag.of(this.tag, qname);
		}

		public @Nullable String getText(String qname) {
			return subtag(qname).getText();
		}

		public @Nullable String getGroupId() {
			return getText(GROUP_ID);
		}

		public @Nullable String getArtifactId() {
			return getText(ARTIFACT_ID);
		}

		public XmlTag getTag() {
			return this.tag;
		}

	}

	/**
	 * Trimmed text of a named subtag; present only when the subtag holds text.
	 */
	private static class Subtag {

		private final @Nullable String text;

		private Subtag(@Nullable XmlTag owner, String name) {
			String text = owner != null ? owner.getSubTagText(name) : null;
			this.text = StringUtils.hasText(text) ? text.trim() : null;
		}

		public static Subtag of(@Nullable XmlTag owner, String name) {
			return new Subtag(owner, name);
		}

		public @Nullable String getText() {
			return text;
		}

		public boolean isPresent() {
			return text != null;
		}

		public boolean isEmpty() {
			return text == null;
		}

		public <T> T eitherOr(Function<String, T> ifPresent, Supplier<T> otherwise) {
			return text != null ? ifPresent.apply(text) : otherwise.get();
		}

		public boolean textEquals(String expected) {
			return expected.equals(text);
		}

		public boolean textEquals(Subtag other) {
			return text != null && text.equals(other.text);
		}

	}

}
