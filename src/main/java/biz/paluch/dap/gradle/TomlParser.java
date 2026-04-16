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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GradleDependency.PropertyManagedDependency;
import biz.paluch.dap.gradle.GradleDependency.SimpleDependency;
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PsiPropertyValueElement;
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

	public static final String PLUGINS = "plugins";

	public static final String BUNDLES = "bundles";

	public static final String VERSION = "version";

	public static final String VERSIONS = "versions";

	public static final String LIBS = "libs";

	public static final String LIBRARIES = "libraries";

	private final Map<String, String> properties;

	public TomlParser(DependencyCollector collector) {
		this(collector, new LinkedHashMap<>());
	}

	public TomlParser(DependencyCollector collector, Map<String, String> properties) {
		super(collector);
		this.properties = properties;
	}

	public static @Nullable PsiFile findVersionCatalogToml(Project project, VirtualFile anchorFile) {

		VirtualFile root = GradleUtils.findProjectRoot(anchorFile);
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

	@Override
	protected Map<String, String> getPropertyMap() {
		return properties;
	}

	/**
	 * Parses a {@code libs.versions.toml} version catalog and populates
	 * {@code collector} with all libraries that have a resolvable version.
	 */
	public void parseVersionCatalog(PsiFile file) {

		if (!(file instanceof TomlFile tomlFile)) {
			return;
		}

		Map<String, PsiPropertyValueElement> properties = parseTomlVersions(tomlFile);
		properties.forEach((key, value) -> {
			this.properties.put(key, value.propertyValue());
		});

		parseToml(tomlFile, properties);
	}

	private void parseToml(TomlFile tomlFile, Map<String, PsiPropertyValueElement> properties) {

		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(tomlFile, TomlTable.class)) {

			String tableName = getTomlTableName(table);
			if (LIBRARIES.equals(tableName)) {
				parseEntries(table, properties, (it) -> {
					if (it.isComplete()) {
						register(it.toDependency(), DeclarationSource.managed());
					}
				});
			}

			if (PLUGINS.equals(tableName)) {
				parseEntries(table, properties, (it) -> {
					if (it.isComplete()) {
						register(it.toDependency(), DeclarationSource.plugin());
					}
				});
			}
		}
	}

	/**
	 * Parse TOML {@code [versions]} table into a map of
	 * {@link PsiPropertyValueElement}. We treat versions semantically as
	 * properties.
	 *
	 * @param tomlFile the file to parse.
	 * @return map of {@link PsiPropertyValueElement} mapped to its version key.
	 */
	public static Map<String, PsiPropertyValueElement> parseTomlVersions(PsiFile tomlFile) {

		Map<String, PsiPropertyValueElement> map = new LinkedHashMap<>();
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
					String value = TomlParser.getText(valuePsi);
					if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
						map.put(key, new PsiPropertyValueElement(valuePsi, key, value));
					}
				});

		return map;
	}


	public static void parseEntries(TomlTable table,
			Map<String, PsiPropertyValueElement> properties, Consumer<TomlDependencyDeclaration> action) {
		for (TomlKeyValue child : PsiTreeUtil.findChildrenOfType(table, TomlKeyValue.class)) {
			TomlDependencyDeclaration declaration = parseTomlEntry(child, properties);
			if (declaration.isComplete()) {
				action.accept(declaration);
			}
		}
	}

	static TomlDependencyDeclaration parseTomlEntry(TomlKeyValue keyValue,
			Map<String, PsiPropertyValueElement> properties) {

		String key = getTomlKeyName(keyValue.getKey());
		if (keyValue.getValue() instanceof TomlInlineTable inlineTable) {
			return parseTomlEntry(key, inlineTable, properties);
		} else if (keyValue.getValue() instanceof TomlLiteral literal) {
			return parseTomlEntry(key, literal);
		}

		return new TomlDependencyDeclaration((TomlFile) keyValue.getContainingFile(), key, null, null, null, null,
				null);
	}

	static TomlDependencyDeclaration parseTomlEntry(String entry, TomlInlineTable inlineTable,
			Map<String, PsiPropertyValueElement> properties) {

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
			case "id" -> id = val;
			case "module" -> module = val;
			case "group" -> group = val;
			case "name" -> name = val;
			case "version.ref" -> versionRef = val;
			case "version" -> {
				version = val;
				versionLiteral = inner.getValue();
			}
			}
		}

		if (module == null && group != null && name != null) {
			module = group + ":" + name;
		}

		if (versionRef != null && versionLiteral == null && properties.containsKey(versionRef)) {
			PsiPropertyValueElement p = properties.get(versionRef);
			versionLiteral = (TomlValue) p.element();
		}

		return new TomlDependencyDeclaration((TomlFile) inlineTable.getContainingFile(), entry, id, module, versionRef,
				version, versionLiteral);
	}

	static TomlDependencyDeclaration parseTomlEntry(String entry, TomlLiteral literal) {

		String text = getText(literal);
		if (StringUtils.hasText(text)) {
			GradleDependency dependency = GradleDependency.parse(text);
			// plugin
			if (dependency instanceof GradleDependency.DependencyReference reference) {
				return new TomlDependencyDeclaration((TomlFile) literal.getContainingFile(), entry,
						reference.id().groupId(), null, null,
						reference.id().artifactId(), literal);
			}

			if (dependency instanceof SimpleDependency simple) {
				return new TomlDependencyDeclaration((TomlFile) literal.getContainingFile(), entry, null,
						simple.id().toString(), null,
						simple.version(), literal);
			}
		}

		return new TomlDependencyDeclaration((TomlFile) literal.getContainingFile(), entry, text, text, null,
				null, literal);
	}

	public static @Nullable String getTomlTableName(TomlTable table) {
		TomlKey key = table.getHeader().getKey();
		return getText(key);
	}

	public static String getTomlKeyName(TomlKey key) {
		return key.getText().trim();
	}

	/**
	 * Returns the required text associated with {@code expression} or throw
	 * {@link IllegalArgumentException} if the text could not be obtained.
	 * @return the required text.
	 * @throws IllegalArgumentException if the expression is not supported.
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
	 * Returns the string content of a TOML literal.
	 * @param element the PSI element to extract the text from.
	 * @return the string value.
	 */
	public static @Nullable String getText(@Nullable PsiElement element) {

		if (element instanceof TomlKey key) {
			return key.getText();
		}

		if (element instanceof TomlLiteral lit) {
			String text = lit.getText();
			// Strip surrounding quotes
			if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
				return text.length() > 2 ? text.substring(1, text.length() - 1) : "";
			}
			return text;
		}
		return null;
	}

	record TomlDependencyDeclaration(TomlFile file, String key, @Nullable String id, @Nullable String module,
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
		 * Resolve a {@link GradleDependency} from this declaration.
		 * {@link PropertyResolver}.
		 * <p>As declarations can be incomplete (e.g. missing version information), make
		 * sure to check {@link #isComplete()} before calling this method.
		 * @return the resolved dependency.
		 */
		public GradleDependency toDependency() {

			Assert.state(StringUtils.hasText(id) || StringUtils.hasText(module), "No identifier or module set");

			PropertyExpression versionExpression = StringUtils.hasText(version) ? PropertyExpression.from(version)
					: PropertyExpression.property(versionRef);

			if (StringUtils.hasText(id)) {
				return of(GradlePlugin.of(id), versionExpression);
			}

			return of(GradleDependency.parse(module).getId(), versionExpression);
		}

		public static GradleDependency of(ArtifactId artifactId, PropertyExpression versionExpression) {

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
