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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import org.toml.lang.psi.TomlFile;
import org.toml.lang.psi.TomlInlineTable;
import org.toml.lang.psi.TomlKey;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;
import org.toml.lang.psi.TomlTable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Parser for TOML version catalog ({@code libs.versions.toml}) files, including mapping Gradle {@code libs.…} accessor
 * chains to catalog table names and entry keys. For TOML PSI navigation helpers (entry lookup, version literals), see
 * {@link GradleVersionCatalogAliasSupport}.
 *
 * @author Mark Paluch
 */
class TomlParser extends GradleParserSupport {

	/**
	 * A catalog {@code [libraries]} or {@code [plugins]} table together with the TOML entry key (kebab-case).
	 *
	 * @param tableName {@code libraries} or {@code plugins}
	 * @param entryKey kebab-case catalog entry key (e.g. {@code spring-dependency-management})
	 */
	record CatalogTableKey(String tableName, String entryKey) {
	}

	/**
	 * Maps {@code libs.plugins.a.b} to {@code plugins} / {@code a-b}; {@code libs.a.b.c} to {@code libraries} /
	 * {@code a-b-c}.
	 */
	static @Nullable CatalogTableKey catalogTableKeyFromLibsSegments(List<String> segments) {

		if (segments.size() < 2 || !"libs".equals(segments.get(0))) {
			return null;
		}
		if ("plugins".equals(segments.get(1))) {
			if (segments.size() < 3) {
				return null;
			}
			return new CatalogTableKey("plugins", String.join("-", segments.subList(2, segments.size())));
		}
		if ("versions".equals(segments.get(1)) || "bundles".equals(segments.get(1))) {
			return null;
		}
		String libKey = String.join("-", segments.subList(1, segments.size()));
		if (!StringUtils.hasText(libKey)) {
			return null;
		}
		return new CatalogTableKey("libraries", libKey);
	}

	private final Map<String, String> properties;

	public TomlParser(DependencyCollector collector) {
		this(collector, new LinkedHashMap<>());
	}

	public TomlParser(Map<String, String> properties) {
		this(new DependencyCollector(), properties);
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

	// -------------------------------------------------------------------------
	// libs.versions.toml
	// -------------------------------------------------------------------------

	/**
	 * Parses a {@code libs.versions.toml} version catalog and populates {@code collector} with all libraries that have a
	 * resolvable version.
	 */
	public void parseVersionCatalog(PsiFile file) {

		if (!(file instanceof TomlFile tomlFile)) {
			return;
		}

		Map<String, String> versions = parseTomlVersionProperties(tomlFile);
		this.properties.putAll(versions);

		parseToml(tomlFile);
	}

	private void parseToml(TomlFile tomlFile) {

		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(tomlFile, TomlTable.class)) {

			String tableName = getTomlTableName(table);
			if ("libraries".equals(tableName)) {
				parseEntries(table, TomlParser::parseArtifactId, DeclarationSource.managed());
			}

			if ("plugins".equals(tableName)) {
				parseEntries(table, module -> ArtifactId.of(module, module), DeclarationSource.managed());
			}
		}
	}

	private void parseEntries(TomlTable table, Function<String, ArtifactId> idFunction,
			DeclarationSource declarationSource) {
		for (TomlKeyValue kv : PsiTreeUtil.getChildrenOfTypeAsList(table, TomlKeyValue.class)) {
			if (kv.getValue() instanceof TomlInlineTable kvTable) {
				GradleDependency dependency = parseTomlEntry(kvTable, idFunction);
				if (dependency != null) {
					register(dependency, declarationSource);
				}
			}
		}
	}

	private static Map<String, String> parseTomlVersionProperties(TomlFile tomlFile) {

		Map<String, String> versions = new HashMap<>();

		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(tomlFile, TomlTable.class)) {
			String tableName = getTomlTableName(table);
			if (!"versions".equals(tableName)) {
				continue;
			}
			for (TomlKeyValue kv : PsiTreeUtil.getChildrenOfTypeAsList(table, TomlKeyValue.class)) {
				String key = getTomlKeyName(kv.getKey());
				String val = getText(kv.getValue());
				if (StringUtils.hasText(key) && StringUtils.hasText(val)) {
					versions.put(key, val);
				}
			}
		}

		return versions;
	}

	static @Nullable GradleDependency parseTomlEntry(TomlInlineTable inlineTable,
			Function<String, ArtifactId> artifactIdFunction) {

		String module = null;
		String versionRef = null;
		String version = null;

		for (TomlKeyValue inner : PsiTreeUtil.getChildrenOfTypeAsList(inlineTable, TomlKeyValue.class)) {
			String key = getTomlKeyName(inner.getKey());
			String val = getText(inner.getValue());
			switch (key) {
				case "id" -> module = val;
				case "module" -> module = val;
				case "version.ref" -> versionRef = val;
				case "version" -> version = val;
			}
		}

		if (!StringUtils.hasText(module)) {
			return null;
		}

		ArtifactId artifactId = artifactIdFunction.apply(module);
		if (StringUtils.hasText(versionRef)) {
			return new PropertyManagedDependency(artifactId, versionRef, VersionSource.versionCatalogProperty(versionRef));
		}

		return new SimpleDependency(artifactId, version, VersionSource.versionCatalog());
	}

	@Nullable
	String resolveValue(@Nullable String value) {
		return resolveValue(value, properties);
	}

	@Override
	@Nullable
	String getProperty(@Nullable String value) {
		return properties.get(value);
	}

	public static @Nullable String getTomlTableName(TomlTable table) {
		TomlKey key = table.getHeader().getKey();
		return key != null ? key.getText().replaceAll("\\s", "") : null;
	}

	public static String getTomlKeyName(TomlKey key) {
		return key.getText().trim();
	}

	public static @Nullable String getText(@Nullable PsiElement value) {
		if (value instanceof TomlLiteral lit) {
			String text = lit.getText();
			// Strip surrounding quotes
			if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
				return text.length() > 2 ? text.substring(1, text.length() - 1) : "";
			}
			return text;
		}
		return null;
	}

}
