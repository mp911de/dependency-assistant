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

import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GradleDependency.SimpleDependency;
import biz.paluch.dap.gradle.TomlParser.TomlCatalogDeclaration;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.toml.lang.psi.TomlFile;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;
import org.toml.lang.psi.TomlPsiFactory;
import org.toml.lang.psi.TomlTable;

/**
 * Applies selected dependency version updates to Gradle build/property/TOML
 * files.
 *
 * @author Mark Paluch
 */
class UpdateGradleFile {

	private final Project project;

	/**
	 * Create an updater for Gradle-related files in the given project.
	 */
	public UpdateGradleFile(Project project) {
		this.project = project;
	}

	/**
	 * Applies the selected version updates to the Gradle build file.
	 *
	 * @param buildFile the Gradle file to update.
	 * @param updates dependency updates to apply.
	 */
	public void applyUpdates(PsiFile buildFile, List<DependencyUpdate> updates) {

		GradlePropertyResolver propertyResolver = GradlePropertyResolver.create(buildFile);

		for (DependencyUpdate update : updates) {
			applyUpdate(buildFile, propertyResolver, update);
		}
	}

	private void applyUpdate(PsiFile buildFile, GradlePropertyResolver propertyResolver, DependencyUpdate update) {

		String newVersion = update.version().toString();

		for (VersionSource source : update.versionSources()) {

			if (source instanceof VersionSource.VersionProperty vps) {
				updateProperty(buildFile, vps.getProperty(), newVersion);
				continue;
			}

			if (!(source instanceof VersionSource.DeclaredVersion)) {
				continue;
			}

			updateDeclaration(buildFile, propertyResolver, update.artifactId(), newVersion);
		}
	}

	/**
	 * Apply a single update at the given Gradle version literal. The literal must
	 * be one of the supported PSI shapes (Properties value, TOML literal, Groovy
	 * literal, or Kotlin string template).
	 * @param literal the version PSI element to rewrite; must not be
	 * {@literal null}.
	 * @param update the update to apply.
	 */
	public void applyUpdate(PsiElement literal, DependencyUpdate update) {

		String newVersion = update.version().toString();

		switch (literal) {
		case PropertyValueImpl propertyValue -> {
			if (propertyValue.getParent() instanceof IProperty property) {
				property.setValue(newVersion);
			}
		}
		case TomlLiteral tomlLiteral -> {
			updateTomlLiteral(tomlLiteral, newVersion);
		}
		case GrLiteral grLiteral -> {
			if (update.hasVersionSource(VersionSource::isProperty)) {
				UpdateGroovyDsl.updateExtProperty(grLiteral, newVersion);
			} else {
				UpdateGroovyDsl.updateVersion(grLiteral, newVersion);
			}
		}
		case KtStringTemplateExpression ktExpression -> {
			UpdateKotlinDsl.updateVersion(ktExpression, newVersion);
		}
		default -> {
		}
		}
	}

	/**
	 * Updates a property value in a properties file or Groovy/Kotlin ext/extra
	 * block.
	 */
	public void updateProperty(PsiFile file, String propertyKey, String newVersion) {

		// gradle.properties
		if (file instanceof PropertiesFile propsFile) {
			IProperty prop = propsFile.findPropertyByKey(propertyKey);
			if (prop != null) {
				prop.setValue(newVersion);
			}
		}

		if (file instanceof TomlFile tf) {
			updateProperty(tf, propertyKey, newVersion);
		}

		// Groovy DSL: ext { key = 'value' } / ext.key = 'value' / ext { set('key',
		// 'value') }
		if (GradleUtils.isGroovyDsl(file.getVirtualFile())) {
			new UpdateGroovyDsl(PropertyResolver.empty()).updateExtProperty(file, propertyKey, newVersion);
		}

		// Kotlin DSL: extra["key"] = "value" or val key = "value"
		if (GradleUtils.isKotlinDsl(file.getVirtualFile()) && GradleUtils.KOTLIN_AVAILABLE) {
			if (!UpdateKotlinDsl.updateExtraProperty(file, propertyKey, newVersion)) {
				UpdateKotlinDsl.updateValProperty(file, propertyKey, newVersion);
			}
		}
	}

	/**
	 * Update a version entry inside the {@code [versions]} table of a
	 * {@code libs.versions.toml} catalog.
	 */
	public void updateProperty(TomlFile file, String propertyKey, String newVersion) {

		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(file, TomlTable.class)) {
			String tableName = table.getHeader().getKey() != null ? table.getHeader().getKey().getText().trim() : null;

			if (!GradleUtils.VERSIONS.equals(tableName)) {
				continue;
			}

			for (TomlKeyValue kv : PsiTreeUtil.getChildrenOfTypeAsList(table, TomlKeyValue.class)) {
				if (!propertyKey.equals(kv.getKey().getText().trim())) {
					continue;
				}
				if (!(kv.getValue() instanceof TomlLiteral literal)) {
					continue;
				}
				updateTomlLiteral(literal, newVersion);
			}
		}
	}

	private void updateTomlLiteral(TomlLiteral literal, String newVersion) {
		TomlLiteral newLiteral = new TomlPsiFactory(project, false)
				.createLiteral("\"%s\"".formatted(newVersion));
		literal.replace(newLiteral);
	}

	/**
	 * Updates the version in a dependency GAV string literal or map-notation
	 * {@code version:} argument.
	 */
	private void updateDeclaration(PsiFile file, GradlePropertyResolver propertyResolver, ArtifactId artifactId,
			String newVersion) {

		// TOML
		VirtualFile virtualFile = file.getVirtualFile();

		if (GradleUtils.isVersionCatalog(virtualFile) && file instanceof TomlFile tomlFile) {
			updateDeclaration(tomlFile, artifactId, newVersion);
			return;
		}

		// Groovy DSL
		if (GradleUtils.isGroovyDsl(virtualFile)) {
			new UpdateGroovyDsl(propertyResolver).updateDeclaration(file, artifactId, newVersion);
			return;
		}

		// Kotlin DSL
		if (GradleUtils.isKotlinDsl(virtualFile) && GradleUtils.KOTLIN_AVAILABLE) {
			new UpdateKotlinDsl(propertyResolver).updateDeclaration(file, artifactId, newVersion);
		}
	}

	/**
	 * Updates {@code version = "…"} inside a {@code [libraries]} or
	 * {@code [plugins]} inline table when the entry matches {@code artifactId} and
	 * uses a literal version (not {@code version.ref}).
	 */
	private void updateDeclaration(TomlFile file, ArtifactId artifactId, String newVersion) {

		Map<String, Property> properties = TomlParser.parseTomlVersions(file);
		PropertyResolver propertyResolver = PropertyResolver.fromMap(properties);
		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(file, TomlTable.class)) {

			String tableName = TomlParser.getTomlTableName(table);
			boolean isPlugin = TomlParser.PLUGINS.equals(tableName);
			boolean isDependency = TomlParser.LIBRARIES.equals(tableName);
			if ((!isDependency && !isPlugin)) {
				continue;
			}

			for (TomlKeyValue kv : PsiTreeUtil.getChildrenOfTypeAsList(table, TomlKeyValue.class)) {

				TomlCatalogDeclaration entry = TomlParser.parseTomlEntry(kv, propertyResolver);
				if (!entry.isComplete()) {
					continue;
				}

				GradleDependency dependency = entry.toDependency();

				if (!(dependency instanceof SimpleDependency sd) || !sd.id().equals(artifactId)
						|| entry.getVersionLiteral() == null) {
					continue;
				}

				String replacementText = newVersion;
				if (kv.getValue() instanceof TomlLiteral) {
					String text = TomlParser.getRequiredText(kv.getValue());
					replacementText = text.replace(entry.getVersion(), newVersion);
				}

				TomlLiteral newLiteral = new TomlPsiFactory(project, false)
						.createLiteral("\"%s\"".formatted(replacementText));
				entry.getVersionLiteral().replace(newLiteral);
			}
		}
	}


}
