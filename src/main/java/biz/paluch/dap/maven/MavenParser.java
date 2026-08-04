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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.PsiElements;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jspecify.annotations.Nullable;

/**
 * Parser for Maven files.
 *
 * @author Mark Paluch
 */
class MavenParser extends MavenPomSupport {

	private final Cache cache;

	private final MavenPomProperties propertyResolver;

	/**
	 * Create a new {@code MavenParser}.
	 * @param cache the cache used to resolve imported BOM contents.
	 */
	MavenParser(Cache cache) {
		this(cache, MavenPomProperties.empty());
	}

	/**
	 * Create a new {@code MavenParser}.
	 *
	 * @param cache the cache used to resolve imported BOM contents.
	 * @param propertyResolver Maven property resolver.
	 */
	MavenParser(Cache cache, MavenPomProperties propertyResolver) {
		this.cache = cache;
		this.propertyResolver = propertyResolver;
	}

	/**
	 * Parse dependencies, plugins, and properties from the given POM file.
	 * @param pomFile the POM file to parse.
	 */
	public List<ArtifactDeclaration> parsePomFile(XmlFile pomFile) {

		MavenPomProperties properties = MavenPomProperties.from(pomFile, this.propertyResolver);

		List<ArtifactDeclaration> declarations = new ArrayList<>();
		doWithArtifacts(properties, pomFile, declarations::add);
		return declarations;
	}

	/**
	 * Parse dependencies from the given extensions file.
	 *
	 * @param extensionsFile the extensions file to parse.
	 */
	public List<ArtifactDeclaration> parseExtensionsFile(XmlFile extensionsFile) {

		MavenPomProperties properties = MavenPomProperties.from(extensionsFile, this.propertyResolver);
		List<ArtifactDeclaration> declarations = new ArrayList<>();
		doWithRoot(extensionsFile, root -> {
			if (EXTENSIONS.equals(root.getLocalName())) {
				PomTag.of(root).subtags(EXTENSION).forEach(extension -> {
					ArtifactDeclaration declaration = parseDeclaration(properties, extension.getTag(),
							DeclarationSource.dependency());
					if (declaration != null) {
						declarations.add(declaration);
					}
				});
			}
		});
		return declarations;
	}

	protected @Nullable ArtifactDeclaration parseDeclaration(XmlTag owner) {
		return parseDeclaration(propertyResolver, owner, getDeclarationSource(owner));
	}

	private @Nullable ArtifactDeclaration parseDeclaration(MavenPomProperties properties, XmlTag owner,
			DeclarationSource declarationSource) {

		PropertyResolver resolver = properties.forDeclaration(owner);
		PomTag tag = PomTag.of(owner);
		ArtifactId artifactId = parseArtifactId(tag, resolver);

		if (artifactId == null) {
			return null;
		}

		Subtag versionTag = tag.subtag(VERSION);
		String versionText = versionTag.getText();
		Expression expression = Expression.from(versionText != null ? versionText : "");
		VersionSource versionSource = StringUtils.hasText(versionText) ? expression.asVersionSource()
				: VersionSource.none();
		if (expression.isProperty() && declarationSource instanceof DeclarationSource.Profile profile) {
			versionSource = VersionSource.profileProperty(profile.getProfileId(), expression.getPropertyName());
		}
		Property resolvedProperty = resolveProperty(expression, resolver);
		String resolvedVersion = resolvedProperty != null ? resolvedProperty.getValue() : expression.resolve(resolver);

		ArtifactDeclaration.Builder builder = ArtifactDeclaration.builder()
				.artifact(artifactId)
				.packageSystem(PackageSystem.MAVEN)
				.declarationElement(tag.getTag())
				.declarationSource(declarationSource)
				.versionSource(versionSource);

		ArtifactVersion.from(resolvedVersion).ifPresent(builder::version);
		if (resolvedProperty != null) {
			builder.versionLiteral(resolvedProperty.getValueLiteral());
		} else if (!expression.isProperty() && owner.findFirstSubTag(VERSION) != null) {
			builder.versionLiteral(owner.findFirstSubTag(VERSION));
		}
		return builder.build();
	}

	private static @Nullable Property resolveProperty(Expression expression, PropertyResolver resolver) {

		Set<String> visited = new HashSet<>();
		Property property = null;
		while (expression.isProperty() && visited.add(expression.getPropertyName())) {
			property = resolver.getPropertyValue(expression.getPropertyName());
			if (property == null) {
				return null;
			}
			expression = Expression.from(property.getValue());
		}
		return expression.isProperty() ? null : property;
	}

	private void doWithArtifacts(MavenPomProperties properties, XmlFile pomFile,
			Consumer<ArtifactDeclaration> callback) {

		doWithRoot(pomFile, root -> {

			PomTag pomTag = PomTag.of(root);
			XmlTag parent = root.findFirstSubTag("parent");
			if (isParentDependencyCandidate(root, parent)) {
				doWithDeclaration(properties, PomTag.of(parent), getDeclarationSource(parent), callback);
			}

			doWithPluginsAndDependencies(properties, callback, pomTag);
			doWithProfiles(pomTag, profile -> {

				Subtag id = profile.subtag(ID);
				if (id.isEmpty()) {
					return;
				}
				doWithPluginsAndDependencies(properties, callback, profile);
			});
		});
	}

	private void doWithPluginsAndDependencies(MavenPomProperties properties, Consumer<ArtifactDeclaration> callback,
			PomTag root) {

		root.subtags(DEPENDENCY_MANAGEMENT)
				.forEach(dependencyManagement -> doWithDependencies(dependencyManagement, properties, callback));

		doWithDependencies(root, properties, callback);

		root.subtags(BUILD).forEach(build -> {
			build.subtags(PLUGIN_MANAGEMENT)
					.forEach(pluginManagement -> doWithPlugins(properties, callback, pluginManagement));
			doWithPlugins(properties, callback, build);
			doWithExtensions(properties, callback, build);
		});

		root.subtags(REPORTING).forEach(reporting -> doWithPlugins(properties, callback, reporting));
	}

	private void doWithDependencies(PomTag root, MavenPomProperties properties,
			Consumer<ArtifactDeclaration> callback) {
		root.subtags(DEPENDENCIES).subtags(DEPENDENCY).forEach(dependency -> {
			doWithDeclaration(properties, dependency, getDeclarationSource(dependency.getTag(), properties), callback);
		});
	}

	private void doWithPlugins(MavenPomProperties properties,
			Consumer<ArtifactDeclaration> callback, PomTag build) {
		build.subtags(PLUGINS).subtags(PLUGIN).forEach(plugin -> {
			doWithDeclaration(properties, plugin, getDeclarationSource(plugin.getTag()), callback);
		});
	}

	private void doWithExtensions(MavenPomProperties properties,
			Consumer<ArtifactDeclaration> callback, PomTag build) {
		build.subtags(EXTENSIONS).subtags(EXTENSION).forEach(extension -> {
			if (extension.subtag(GROUP_ID).isPresent()) {
				doWithDeclaration(properties, extension, getDeclarationSource(extension.getTag()), callback);
			}
		});
	}

	private void doWithDeclaration(MavenPomProperties properties, PomTag tag, DeclarationSource declarationSource,
			Consumer<ArtifactDeclaration> callback) {

		ArtifactDeclaration declaration = parseDeclaration(properties, tag.getTag(), declarationSource);
		if (declaration != null) {
			callback.accept(declaration);
		}
	}

	/**
	 * Return the {@link DeclarationSource} for the given dependency or plugin
	 * declaration tag. A dependency-management entry with {@code scope=import} and
	 * {@code type=pom} classifies as a Bill of Materials import.
	 *
	 * @param owner the dependency, plugin, or extension tag to classify.
	 * @return the declaration source describing where the artifact is declared.
	 */
	private DeclarationSource getDeclarationSource(XmlTag owner, MavenPomProperties properties) {

		PropertyResolver propertyResolver = properties.forDeclaration(owner);

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

}
