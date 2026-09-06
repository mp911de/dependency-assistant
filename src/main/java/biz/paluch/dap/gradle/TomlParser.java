/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.gradle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GradleDependency.PropertyManagedDependency;
import biz.paluch.dap.gradle.GradleDependency.SimpleDependency;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.JBIterable;
import org.jspecify.annotations.Nullable;
import org.toml.lang.psi.TomlElement;
import org.toml.lang.psi.TomlFile;
import org.toml.lang.psi.TomlInlineTable;
import org.toml.lang.psi.TomlKey;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;
import org.toml.lang.psi.TomlTable;
import org.toml.lang.psi.TomlValue;

import org.springframework.util.Assert;

/**
 * Parser for Gradle TOML version catalogs.
 *
 * <p>Library and plugin entries become {@link ArtifactDeclaration artifact
 * declarations}. Entries in {@code [versions]} become PSI-backed properties
 * used to resolve {@code version.ref} declarations.
 *
 * @author Mark Paluch
 */
class TomlParser {

	public static final String PLUGINS = GradleUtils.PLUGINS;

	public static final String BUNDLES = "bundles";

	public static final String VERSION = GradleUtils.VERSION;

	public static final String VERSION_REF = "version.ref";

	public static final String VERSIONS = "versions";

	/**
	 * Default Gradle version catalog accessor root.
	 */
	public static final String LIBS = "libs";

	public static final String LIBRARIES = "libraries";

	public static final String MODULE = "module";

	/**
	 * Parse artifact declarations in catalog order.
	 * @return an empty list for a non-TOML file.
	 */
	public static List<ArtifactDeclaration> parseVersionCatalog(PsiFile file) {

		if (!(file instanceof TomlFile tomlFile)) {
			return List.of();
		}

		Map<String, Property> properties = parseTomlVersions(tomlFile);
		PropertyResolver propertyResolver = PropertyResolver.fromMap(properties);
		List<ArtifactDeclaration> declarations = new ArrayList<>();

		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(tomlFile, TomlTable.class)) {

			String tableName = getTomlTableName(table);
			if (LIBRARIES.equals(tableName) || PLUGINS.equals(tableName)) {
				parseEntries(table, propertyResolver, declaration -> declarations
						.add(declaration.toArtifactDeclaration(declaration.getElement(),
								declaration.getRequiredVersionLiteral())));
			}
		}

		return declarations;
	}

	/**
	 * Find a catalog relative to the Gradle project root.
	 * <p>Absolute paths, parent segments and files outside the root are rejected.
	 * @return {@literal null} if the catalog is unavailable or outside the allowed
	 * scope.
	 */
	static @Nullable PsiFile findCatalogFile(BetterPsiManager psiManager, VirtualFile projectRoot,
			String relativePath) {

		if (!isSafeCatalogRelativePath(relativePath)) {
			return null;
		}

		VirtualFile catalogFile = projectRoot.findFileByRelativePath(relativePath);
		if (catalogFile != null && VfsUtil.isAncestor(projectRoot, catalogFile, false)) {
			return psiManager.findFile(catalogFile);
		}

		return null;
	}

	private static boolean isSafeCatalogRelativePath(String path) {

		if (path.isBlank()) {
			return false;
		}
		String normalized = path.replace('\\', '/');
		if (normalized.startsWith("/")) {
			return false;
		}
		for (String segment : normalized.split("/")) {
			if ("..".equals(segment)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Return whether the element is a literal in a version, library or plugin
	 * table.
	 */
	public static boolean isVersionElement(PsiElement element) {
		return element instanceof TomlLiteral
				&& isInsideTable(element, it -> VERSIONS.equals(it) || LIBRARIES.equals(it) || PLUGINS.equals(it));
	}

	/**
	 * Return whether an enclosing table name matches the predicate.
	 */
	public static boolean isInsideTable(PsiElement element, Predicate<String> predicate) {
		return SyntaxTraverser.revPsiTraverser().api.parents(element).filter(TomlTable.class).filter(it -> {
			String tomlTableName = getTomlTableName(it);
			return StringUtils.hasText(tomlTableName) && predicate.test(tomlTableName);
		}).first() != null;
	}

	/**
	 * Read {@code [versions]} entries as editable properties.
	 */
	public static Map<String, Property> parseTomlVersions(PsiFile tomlFile) {

		Map<String, Property> map = new LinkedHashMap<>();
		SyntaxTraverser.psiTraverser(tomlFile)
				.filter(TomlTable.class)
				.flatMap(it -> {

					String tableName = TomlParser.getTomlTableName(it);
					if (tableName.equals(VERSIONS)) {
						return SyntaxTraverser.psiTraverser(it).filter(TomlKeyValue.class);
					}
					return JBIterable.empty();
				})
				.forEach(kv -> {
					String key = TomlParser.getTomlKeyName(kv.getKey());
					PsiElement valuePsi = kv.getValue();
					if (valuePsi != null) {
						String value = TomlParser.getText(valuePsi);
						if (StringUtils.hasText(key)) {
							map.put(key, new PropertyValue(key, value, valuePsi));
						}
					}
				});

		return map;
	}

	/**
	 * Pass each complete catalog declaration to the action.
	 */
	public static void parseEntries(TomlTable table,
			PropertyResolver propertyResolver, Consumer<TomlCatalogDeclaration> action) {
		for (TomlKeyValue child : PsiTreeUtil.getChildrenOfTypeAsList(table, TomlKeyValue.class)) {
			TomlCatalogDeclaration declaration = parseTomlEntry(child, propertyResolver);
			if (declaration.isComplete()) {
				action.accept(declaration);
			}
		}
	}

	static TomlCatalogDeclaration parseTomlEntry(TomlKeyValue keyValue,
			PropertyResolver propertyResolver) {

		String key = getTomlKeyName(keyValue.getKey());
		if (keyValue.getValue() instanceof TomlInlineTable inlineTable) {
			return parseTomlEntry(key, inlineTable, propertyResolver);
		} else if (keyValue.getValue() instanceof TomlLiteral literal) {
			return parseTomlEntry(key, literal);
		}

		return new TomlCatalogDeclaration(keyValue, key, null, null, null, null,
				null);
	}

	static TomlCatalogDeclaration parseTomlEntry(String entry, TomlInlineTable inlineTable,
			PropertyResolver propertyResolver) {

		String id = null;
		String module = null;
		String group = null;
		String name = null;
		String versionRef = null;
		String version = null;
		TomlValue versionLiteral = null;

		for (TomlKeyValue inner : PsiTreeUtil.getChildrenOfTypeAsList(inlineTable, TomlKeyValue.class)) {
			String key = getTomlKeyName(inner.getKey());
			String val = getText(inner.getValue());
			switch (key) {
			case GradleUtils.ID -> id = val;
			case MODULE -> module = val;
			case GradleUtils.GROUP -> group = val;
			case GradleUtils.NAME -> name = val;
			case VERSION_REF -> versionRef = val;
			case GradleUtils.VERSION -> {
				version = val;
				versionLiteral = inner.getValue();
			}
			}
		}

		if (module == null && group != null && name != null) {
			module = group + ":" + name;
		}

		if (versionRef != null && versionLiteral == null && propertyResolver.containsProperty(versionRef)) {
			Property p = propertyResolver.getPropertyValue(versionRef);
			versionLiteral = (TomlValue) p.getValueLiteral();
		}

		return new TomlCatalogDeclaration(inlineTable, entry, id, module, versionRef,
				version, versionLiteral);
	}

	static TomlCatalogDeclaration parseTomlEntry(String entry, TomlLiteral literal) {

		String text = getText(literal);
		if (StringUtils.hasText(text)) {
			GradleDependency dependency = GradleDependency.parse(text, DeclarationSource.managed());
			// plugin
			if (dependency instanceof GradleDependency.DependencyReference reference) {
				return new TomlCatalogDeclaration(literal, entry,
						reference.id().groupId(), null, null,
						reference.id().artifactId(), literal);
			}

			if (dependency instanceof SimpleDependency simple) {
				return new TomlCatalogDeclaration(literal, entry, null,
						simple.id().toString(), null,
						simple.version(), literal);
			}
		}

		return new TomlCatalogDeclaration(literal, entry, text, text, null,
				null, literal);
	}

	public static String getTomlTableName(TomlTable table) {
		TomlKey key = table.getHeader().getKey();
		return getText(key);
	}

	public static String getTomlKeyName(TomlKeyValue keyValue) {
		return getTomlKeyName(keyValue.getKey());
	}

	public static String getTomlKeyName(TomlKey key) {
		return key.getText().trim();
	}

	/**
	 * Return element text.
	 * @throws IllegalArgumentException if the element is {@literal null}.
	 */
	static String getRequiredText(PsiElement element) {

		Assert.notNull(element, "Element must not be null");

		String text = getText(element);

		if (text == null) {
			throw new IllegalArgumentException(
					"Unexpected expression: %s (%s)".formatted(element, element.getClass()
							.getName()));
		}
		return text;
	}

	/**
	 * Return element text, removing quotes from literals.
	 * @return an empty string for {@literal null}.
	 */
	public static String getText(@Nullable PsiElement element) {

		if (element == null) {
			return "";
		}

		if (element instanceof TomlLiteral literal) {
			return StringUtils.unquote(literal.getText());
		}
		return element.getText();
	}

	static class TomlCatalogDeclaration {

		private final TomlElement element;

		private final String key;

		private final @Nullable String id;

		private final @Nullable String module;

		private final @Nullable String versionRef;

		private final @Nullable String version;

		private final @Nullable TomlValue versionLiteral;

		TomlCatalogDeclaration(TomlElement element, String key, @Nullable String id, @Nullable String module,
				@Nullable String versionRef, @Nullable String version, @Nullable TomlValue versionLiteral) {
			this.element = element;
			this.key = key;
			this.id = id;
			this.module = module;
			this.versionRef = versionRef;
			this.version = version;
			this.versionLiteral = versionLiteral;
		}

		TomlElement getElement() {
			return element;
		}

		@Nullable
		String getVersion() {
			return version;
		}

		@Nullable
		TomlValue getVersionLiteral() {
			return versionLiteral;
		}

		/**
		 * Return whether identity and editable version information are available.
		 */
		public boolean isComplete() {

			if (versionLiteral == null || (StringUtils.isEmpty(versionRef) && StringUtils.isEmpty(version))) {
				return false;
			}

			if (StringUtils.hasText(id)) {
				return true;
			}

			return GradleArtifactId.isValid(module);
		}

		/**
		 * Return the module coordinates.
		 * @throws IllegalStateException if no module is set.
		 */
		public String getRequiredModule() {
			Assert.state(StringUtils.hasText(module), "No module set");
			return module;
		}

		/**
		 * Return the editable version value.
		 * @throws IllegalStateException if no value is available.
		 */
		public TomlValue getRequiredVersionLiteral() {
			Assert.state(versionLiteral != null, "No version literal set");
			return versionLiteral;
		}

		/**
		 * Convert a complete entry to a dependency.
		 * <p>Check {@link #isComplete()} before calling.
		 */
		public GradleDependency toDependency() {

			Assert.state(StringUtils.hasText(id) || StringUtils.hasText(module), "No identifier or module set");

			Expression versionExpression = StringUtils.hasText(version) ? Expression.from(version)
					: Expression.property(versionRef);

			if (StringUtils.hasText(id)) {
				return of(GradlePluginId.of(id), versionExpression);
			}

			GradleArtifactId artifactId = GradleArtifactId.from(getRequiredModule());
			return of(artifactId, versionExpression);
		}

		/**
		 * Convert a complete entry to a declaration with the supplied PSI anchors.
		 * <p>Check {@link #isComplete()} before calling.
		 */
		public ArtifactDeclaration toArtifactDeclaration(PsiElement declaration, PsiElement version) {

			Assert.state(StringUtils.hasText(id) || StringUtils.hasText(module), "No identifier or module set");

			Expression versionExpression = StringUtils.hasText(this.version)
					? Expression.from(this.version)
					: Expression.property(versionRef);

			DeclarationSource declarationSource = StringUtils.hasText(id) ? DeclarationSource.plugin()
					: DeclarationSource.managed();
			ArtifactId artifactId = StringUtils.hasText(id) ? GradlePluginId.of(id)
					: GradleDependency.parse(module, declarationSource).getId();

			ArtifactDeclaration.Builder builder = ArtifactDeclaration.builder()
					.artifact(artifactId)
					.packageSystem(PackageSystem.MAVEN)
					.versionSource(getVersionSource(versionExpression))
					.declarationSource(declarationSource)
					.declarationElement(declaration)
					.versionLiteral(version);
			String resolvedVersion = StringUtils.hasText(this.version) ? this.version : TomlParser.getText(version);
			ArtifactVersion.from(resolvedVersion).ifPresent(builder::version);
			return builder.build();
		}

		private static VersionSource getVersionSource(Expression versionExpression) {
			return versionExpression.isProperty()
					? VersionSource.versionCatalogProperty(versionExpression.getPropertyName())
					: VersionSource.versionCatalog(versionExpression.toString());
		}

		private static GradleDependency of(ArtifactId artifactId, Expression versionExpression) {

			if (versionExpression.isProperty()) {
				return new PropertyManagedDependency(artifactId, versionExpression.getPropertyName(),
						VersionSource.versionCatalogProperty(versionExpression.getPropertyName()),
						DeclarationSource.managed());
			}
			return new SimpleDependency(artifactId, versionExpression.toString(),
					VersionSource.versionCatalog(versionExpression.toString()), DeclarationSource.managed());
		}

		public boolean hasKeyMatching(TomlReference tomlReference) {

			if (StringUtils.hasText(id)) {
				return TomlReference.of(tomlReference.getCatalogAlias(), TomlParser.PLUGINS, key)
						.equals(tomlReference);
			}

			return TomlReference.of(tomlReference.getCatalogAlias(), null, key).equals(tomlReference);
		}

	}


}
