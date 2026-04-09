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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.ProjectProperty;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactReference;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import org.toml.lang.psi.TomlFile;
import org.toml.lang.psi.TomlInlineTable;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;
import org.toml.lang.psi.TomlTable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Resolves {@link ArtifactReference} from version-catalog TOML and from {@code libs.…} accessors.
 *
 * @author Mark Paluch
 */
final class GradleVersionCatalogArtifactReferenceResolver {

	private final Project project;

	private final PsiFile file;

	private final GradleProjectContext buildContext;

	private final @Nullable ProjectState projectState;

	private final GradlePropertyDeclarationPsi propertyPsi;

	GradleVersionCatalogArtifactReferenceResolver(Project project, PsiFile file, GradleProjectContext buildContext,
			@Nullable ProjectState projectState, GradlePropertyDeclarationPsi propertyPsi) {
		this.project = project;
		this.file = file;
		this.buildContext = buildContext;
		this.projectState = projectState;
		this.propertyPsi = propertyPsi;
	}

	ArtifactReference resolveTomlLiteral(TomlLiteral literal) {

		TomlKeyValue kv = findParentTomlKeyValue(literal);
		if (kv == null) {
			return ArtifactReference.unresolved();
		}

		if (isInsideTable(literal, "versions"::equals)) {

			String versionKey = TomlParser.getTomlKeyName(kv.getKey());
			String version = TomlParser.getText(literal);
			if (version == null || projectState == null) {
				return ArtifactReference.unresolved();
			}

			ProjectProperty projectProperty = projectState.findProjectProperty(versionKey);
			if (projectProperty == null || projectProperty.property().artifacts().isEmpty()) {
				return ArtifactReference.unresolved();
			}

			CachedArtifact first = projectProperty.property().artifacts().iterator().next();
			return ArtifactReference.from(it -> {
				it.artifact(first.toArtifactId()).declarationElement(kv).versionSource(VersionSource.property(versionKey));
				ArtifactVersion.from(version).ifPresent(it::version);
				it.versionLiteral(literal);
			});
		}

		if (isInsideTable(literal, it -> it.equals(TomlParser.LIBRARIES) || it.equals(TomlParser.PLUGINS))
				&& kv.getParent() instanceof TomlInlineTable inlineTable) {

			PsiElement keyPsi = literal.getParent() != null ? literal.getParent().getFirstChild() : null;
			if (keyPsi == null || !"version".equals(keyPsi.getText().trim())) {
				return ArtifactReference.unresolved();
			}

			GradleParser.GradleDependency dependency = TomlParser.parseTomlEntry(inlineTable, GradleParser::parseArtifactId);
			TomlKeyValue libraryOrPluginKv = PsiTreeUtil.getParentOfType(inlineTable, TomlKeyValue.class);
			if (libraryOrPluginKv == null) {
				return ArtifactReference.unresolved();
			}

			if (dependency instanceof GradleParser.PropertyManagedDependency managed) {
				String resolved = buildContext.getPropertyValue(managed.property());
				TomlLiteral versionsLiteral = null;
				if (file instanceof TomlFile openCatalog) {
					versionsLiteral = GradleVersionCatalogAliasSupport.findVersionsTableLiteral(openCatalog, managed.property());
				}
				PsiElement versionPsi = versionsLiteral != null ? versionsLiteral
						: propertyPsi.findPropertyValuePsi(managed.property());
				return fromPropertyManaged(managed, libraryOrPluginKv, resolved, versionPsi);
			}

			if (dependency instanceof GradleParser.SimpleDependency simple) {
				return fromSimple(simple, libraryOrPluginKv, literal);
			}
		}

		return ArtifactReference.unresolved();
	}

	/**
	 * Resolves a {@code libs.…} catalog accessor through {@code gradle/libs.versions.toml}.
	 */
	ArtifactReference resolveFromLibsSegments(List<String> segments, PsiElement declarationElement) {

		TomlParser.CatalogTableKey key = TomlParser.catalogTableKeyFromLibsSegments(segments);
		if (key == null) {
			return ArtifactReference.unresolved();
		}
		VirtualFile vf = file.getVirtualFile();
		if (vf == null) {
			return ArtifactReference.unresolved();
		}
		PsiFile catalogPsi = TomlParser.findVersionCatalogToml(project, vf);
		if (!(catalogPsi instanceof TomlFile tomlFile)) {
			return ArtifactReference.unresolved();
		}
		TomlKeyValue entryKv = GradleVersionCatalogAliasSupport.findCatalogEntryKeyValue(tomlFile, key.tableName(),
				key.entryKey());
		if (entryKv == null || !(entryKv.getValue() instanceof TomlInlineTable inline)) {
			return ArtifactReference.unresolved();
		}
		GradleParser.GradleDependency dependency = TomlParser.parseTomlEntry(inline,
				GradleVersionCatalogAliasSupport.idFunctionForTable(key.tableName()));
		if (dependency == null) {
			return ArtifactReference.unresolved();
		}

		if (dependency instanceof GradleParser.PropertyManagedDependency managed) {

			String resolved = null;
			if (buildContext.isAvailable()) {
				resolved = buildContext.getPropertyValue(managed.property());
			}

			TomlLiteral versionsFromCatalog = GradleVersionCatalogAliasSupport.findVersionsTableLiteral(tomlFile,
					managed.property());
			if (!StringUtils.hasText(resolved) && versionsFromCatalog != null) {
				resolved = TomlParser.getText(versionsFromCatalog);
			}

			PsiElement versionPsi = versionsFromCatalog != null ? versionsFromCatalog
					: propertyPsi.findPropertyValuePsi(managed.property());
			return fromPropertyManaged(managed, declarationElement, resolved, versionPsi);
		}

		if (dependency instanceof GradleParser.SimpleDependency simple) {
			TomlLiteral inlineVersion = GradleVersionCatalogAliasSupport.findInlineVersionLiteral(inline);
			return fromSimple(simple, declarationElement, inlineVersion);
		}
		return ArtifactReference.unresolved();
	}

	private static ArtifactReference fromPropertyManaged(GradleParser.PropertyManagedDependency managed,
			PsiElement declarationElement, @Nullable String resolvedVersion, @Nullable PsiElement versionPsi) {

		return ArtifactReference.from(it -> {
			it.artifact(managed.getId()).declarationElement(declarationElement).versionSource(managed.getVersionSource());
			if (StringUtils.hasText(resolvedVersion)) {
				ArtifactVersion.from(resolvedVersion).ifPresent(it::version);
			}
			if (versionPsi != null) {
				it.versionLiteral(versionPsi);
			}
		});
	}

	private static ArtifactReference fromSimple(GradleParser.SimpleDependency simple, PsiElement declarationElement,
			@Nullable PsiElement versionPsi) {

		return ArtifactReference.from(it -> {
			it.artifact(simple.getId()).declarationElement(declarationElement).versionSource(simple.getVersionSource());
			ArtifactVersion.from(simple.version()).ifPresent(it::version);
			if (versionPsi != null) {
				it.versionLiteral(versionPsi);
			}
		});
	}

	private static @Nullable TomlKeyValue findParentTomlKeyValue(PsiElement element) {
		return PsiTreeUtil.getParentOfType(element, TomlKeyValue.class);
	}

	private static boolean isInsideTable(PsiElement element, Predicate<String> predicate) {

		TomlTable table = PsiTreeUtil.getParentOfType(element, TomlTable.class);
		if (table == null) {
			return false;
		}
		String name = TomlParser.getTomlTableName(table);
		return name != null && predicate.test(name);
	}

}
