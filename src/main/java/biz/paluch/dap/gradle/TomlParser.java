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

package biz.paluch.dap.gradle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GradleDependency.PropertyManagedDependency;
import biz.paluch.dap.gradle.GradleDependency.SimpleDependency;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.support.VersionedDependencySite;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
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
 * Parser for TOML version catalog ({@code libs.versions.toml}) files, including
 * mapping Gradle {@code libs.…} accessor chains to catalog table names and
 * entry keys.
 *
 * @author Mark Paluch
 */
class TomlParser extends GradleParserSupport {

	/**
	 * TOML table name for plugin aliases.
	 */
	public static final String PLUGINS = GradleUtils.PLUGINS;

	/**
	 * TOML table name for bundles.
	 */
	public static final String BUNDLES = GradleUtils.BUNDLES;

	/**
	 * TOML key name for inline versions.
	 */
	public static final String VERSION = GradleUtils.VERSION;

	/**
	 * TOML key name for referenced versions.
	 */
	public static final String VERSION_REF = GradleUtils.VERSION_REF;

	/**
	 * TOML table name for version aliases.
	 */
	public static final String VERSIONS = GradleUtils.VERSIONS;

	/**
	 * Default Gradle version catalog accessor root.
	 */
	public static final String LIBS = GradleUtils.LIBS;

	/**
	 * TOML table name for library aliases.
	 */
	public static final String LIBRARIES = GradleUtils.LIBRARIES;

	private final Map<String, String> properties;

	/**
	 * Create a new {@code TomlParser}.
	 * @param collector the dependency collector to populate.
	 */
	public TomlParser(DependencyCollector collector) {
		this(collector, Map.of());
	}

	/**
	 * Create a new {@code TomlParser}.
	 * @param collector the dependency collector to populate.
	 * @param properties known version properties.
	 */
	public TomlParser(DependencyCollector collector, Map<String, String> properties) {
		super(collector);
		this.properties = properties;
	}

	/**
	 * Parses a {@code libs.versions.toml} version catalog and populates
	 * {@code collector} with all libraries that have a resolvable version.
	 */
	public void parseVersionCatalog(PsiFile file) {

		if (!(file instanceof TomlFile tomlFile)) {
			return;
		}

		Map<String, Property> properties = parseTomlVersions(tomlFile);
		properties.forEach((key, value) -> {
			this.properties.put(key, value.getValue());
		});

		parseToml(tomlFile, PropertyResolver.fromMap(properties));
	}

	private void parseToml(TomlFile tomlFile, PropertyResolver propertyResolver) {

		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(tomlFile, TomlTable.class)) {

			String tableName = getTomlTableName(table);
			if (LIBRARIES.equals(tableName)) {
				parseEntries(table, propertyResolver, (it) -> {
					if (it.isComplete()) {
						register(it.toDependencySite(it.element, it.getRequiredVersionLiteral()),
								DeclarationSource.managed(), propertyResolver);
					}
				});
			}

			if (PLUGINS.equals(tableName)) {
				parseEntries(table, propertyResolver, (it) -> {
					if (it.isComplete()) {
						register(it.toDependencySite(it.element, it.getRequiredVersionLiteral()),
								DeclarationSource.plugin(), propertyResolver);
					}
				});
			}
		}
	}

	/**
	 * Find the default Gradle version catalog for the given project.
	 * @param project the IntelliJ project.
	 * @param anchorFile the file used to locate the Gradle project root.
	 */
	public static @Nullable PsiFile findVersionCatalogToml(Project project, VirtualFile anchorFile) {

		VirtualFile root = GradleUtils.findProjectRoot(project, anchorFile);
		VirtualFile gradleDir = root.findChild("gradle");
		if (gradleDir == null) {
			return null;
		}
		VirtualFile toml = gradleDir.findChild(GradleUtils.LIBS_VERSIONS_TOML);
		if (toml == null) {
			return null;
		}
		return PsiManager.getInstance(project).findFile(toml);
	}

	/**
	 * Return whether the given element is inside a TOML table matching the
	 * predicate.
	 * @param element the PSI element to inspect.
	 * @param predicate table-name predicate.
	 */
	public static boolean isInsideTable(PsiElement element, Predicate<String> predicate) {
		return SyntaxTraverser.revPsiTraverser().api.parents(element).filter(TomlTable.class).filter(it -> {
			String tomlTableName = getTomlTableName(it);
			return StringUtils.hasText(tomlTableName) && predicate.test(tomlTableName);
		}).first() != null;
	}

	/**
	 * Parse the TOML {@code [versions]} table into a map of {@link PropertyValue}.
	 * We treat versions semantically as properties.
	 * @param tomlFile the file to parse.
	 * @return a map of version keys to {@link PropertyValue} descriptors.
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
						if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
							map.put(key, new PropertyValue(key, value, valuePsi));
						}
					}
				});

		return map;
	}

	/**
	 * Parse catalog entries in the given TOML table.
	 * @param table the TOML table to parse.
	 * @param propertyResolver the resolver for {@code version.ref} entries.
	 * @param action callback invoked for each complete declaration.
	 */
	public static void parseEntries(TomlTable table,
			PropertyResolver propertyResolver, Consumer<TomlDependencyDeclaration> action) {
		for (TomlKeyValue child : PsiTreeUtil.getChildrenOfTypeAsList(table, TomlKeyValue.class)) {
			TomlDependencyDeclaration declaration = parseTomlEntry(child, propertyResolver);
			if (declaration.isComplete()) {
				action.accept(declaration);
			}
		}
	}

	static TomlDependencyDeclaration parseTomlEntry(TomlKeyValue keyValue,
			PropertyResolver propertyResolver) {

		String key = getTomlKeyName(keyValue.getKey());
		if (keyValue.getValue() instanceof TomlInlineTable inlineTable) {
			return parseTomlEntry(key, inlineTable, propertyResolver);
		} else if (keyValue.getValue() instanceof TomlLiteral literal) {
			return parseTomlEntry(key, literal);
		}

		return new TomlDependencyDeclaration(keyValue, key, null, null, null, null,
				null);
	}

	static TomlDependencyDeclaration parseTomlEntry(String entry, TomlInlineTable inlineTable,
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
			case GradleUtils.MODULE -> module = val;
			case GradleUtils.GROUP -> group = val;
			case GradleUtils.NAME -> name = val;
			case GradleUtils.VERSION_REF -> versionRef = val;
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
			PropertyValue p = propertyResolver.getPropertyValue(versionRef);
			versionLiteral = (TomlValue) p.getValueLiteral();
		}

		return new TomlDependencyDeclaration(inlineTable, entry, id, module, versionRef,
				version, versionLiteral);
	}

	static TomlDependencyDeclaration parseTomlEntry(String entry, TomlLiteral literal) {

		String text = getText(literal);
		if (StringUtils.hasText(text)) {
			GradleDependency dependency = GradleDependency.parse(text);
			// plugin
			if (dependency instanceof GradleDependency.DependencyReference reference) {
				return new TomlDependencyDeclaration(literal, entry,
						reference.id().groupId(), null, null,
						reference.id().artifactId(), literal);
			}

			if (dependency instanceof SimpleDependency simple) {
				return new TomlDependencyDeclaration(literal, entry, null,
						simple.id().toString(), null,
						simple.version(), literal);
			}
		}

		return new TomlDependencyDeclaration(literal, entry, text, text, null,
				null, literal);
	}

	/**
	 * Return the name of the given TOML table.
	 */
	public static @Nullable String getTomlTableName(TomlTable table) {
		TomlKey key = table.getHeader().getKey();
		return getText(key);
	}

	/**
	 * Return the text of the given TOML key.
	 */
	public static String getTomlKeyName(TomlKey key) {
		return key.getText().trim();
	}

	/**
	 * Return the required text associated with {@code element}.
	 *
	 * @param element the PSI element to inspect.
	 * @return the required text.
	 * @throws IllegalArgumentException if the element is not supported.
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
	 * Return the string content of a TOML literal.
	 *
	 * @param element the PSI element to extract the text from.
	 * @return the string value.
	 */
	public static String getText(PsiElement element) {
		if (element instanceof TomlLiteral literal) {
			return StringUtils.unquote(literal.getText());
		}
		return element.getText();
	}

	record TomlDependencyDeclaration(TomlElement element, String key, @Nullable String id, @Nullable String module,
			@Nullable String versionRef,
			@Nullable String version, @Nullable TomlValue versionLiteral) {

		/**
		 * Check whether the declaration is complete (having id and version information
		 * or group and artifact with version information).
		 */
		public boolean isComplete() {

			if (versionLiteral == null || (StringUtils.isEmpty(versionRef) && StringUtils.isEmpty(version))) {
				return false;
			}

			if (StringUtils.hasText(id)) {
				return true;
			}

			return StringUtils.hasText(module);
		}

		/**
		 * Return the required module or throw {@link IllegalStateException} if the
		 * module is not set.
		 * @return the module value.
		 * @throws IllegalStateException if no module is set.
		 */
		public String getRequiredModule() {
			Assert.state(StringUtils.hasText(module), "No module set");
			return module;
		}

		/**
		 * Return the required version value.
		 *
		 * @return the version value.
		 * @throws IllegalStateException if no version value is available.
		 */
		public TomlValue getRequiredVersionLiteral() {
			Assert.state(versionLiteral != null, "No version literal set");
			return versionLiteral;
		}

		/**
		 * Resolve a {@link GradleDependency} from this declaration.
		 * <p>As declarations can be incomplete (e.g. missing version information), make
		 * sure to check {@link #isComplete()} before calling this method.
		 *
		 * @return the resolved dependency.
		 */
		public GradleDependency toDependency() {

			Assert.state(StringUtils.hasText(id) || StringUtils.hasText(module), "No identifier or module set");

			Expression versionExpression = StringUtils.hasText(version) ? Expression.from(version)
					: Expression.property(versionRef);

			if (StringUtils.hasText(id)) {
				return of(GradlePluginId.of(id), versionExpression);
			}

			return of(GradleDependency.parse(getRequiredModule()).getId(), versionExpression);
		}

		/**
		 * Resolve a {@link DependencySite} from this declaration.
		 * <p>As declarations can be incomplete (e.g. missing version information), make
		 * sure to check {@link #isComplete()} before calling this method.
		 *
		 * @return the resolved dependency site.
		 */
		public DependencySite toDependencySite(PsiElement declaration, PsiElement version) {

			Assert.state(StringUtils.hasText(id) || StringUtils.hasText(module), "No identifier or module set");

			Expression versionExpression = StringUtils.hasText(this.version)
					? Expression.from(this.version)
					: Expression.property(versionRef);

			ArtifactId artifactId = StringUtils.hasText(id) ? GradlePluginId.of(id)
					: GradleDependency.parse(module).getId();

			return ArtifactVersion.from(this.version)
					.map(it -> (DependencySite) VersionedDependencySite.of(artifactId, it,
							getVersionSource(versionExpression),
							declaration, version))
					.orElseGet(() -> {
						return DependencySite.of(artifactId, getVersionSource(versionExpression), declaration);
					});
		}

		private static VersionSource getVersionSource(Expression versionExpression) {
			return versionExpression.isProperty()
					? VersionSource.versionCatalogProperty(versionExpression.getPropertyName())
					: VersionSource.versionCatalog();
		}

		private static GradleDependency of(ArtifactId artifactId, Expression versionExpression) {

			if (versionExpression.isProperty()) {
				return new PropertyManagedDependency(artifactId, versionExpression.getPropertyName(),
						VersionSource.versionCatalogProperty(versionExpression.getPropertyName()));
			}
			return new SimpleDependency(artifactId, versionExpression.toString(),
					VersionSource.versionCatalog());
		}

		/**
		 * Check if the key matches the given {@link TomlReference}.
		 */
		public boolean hasKeyMatching(TomlReference tomlReference) {

			if (StringUtils.hasText(id)) {
				return TomlReference.of(tomlReference.getCatalogAlias(), TomlParser.PLUGINS, key)
						.equals(tomlReference);
			}

			return TomlReference.of(tomlReference.getCatalogAlias(), null, key).equals(tomlReference);
		}

	}


}
