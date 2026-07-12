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
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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

/**
 * Parser for Maven files.
 *
 * @author Mark Paluch
 */
class MavenParser extends MavenPomSupport {

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

}
