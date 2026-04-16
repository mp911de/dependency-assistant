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

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GradleDependency.SimpleDependency;
import biz.paluch.dap.gradle.TomlParser.TomlDependencyDeclaration;
import biz.paluch.dap.support.PsiPropertyValueElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
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

	private static final Logger LOG = Logger.getInstance(UpdateGradleFile.class);

	private final Project project;

	public UpdateGradleFile(Project project) {
		this.project = project;
	}

	/**
	 * Applies the selected version updates to the Gradle build file.
	 *
	 * @param buildFile the build file currently open in the editor
	 * @param updates dependency updates to apply.
	 */
	public void applyUpdates(VirtualFile buildFile, List<DependencyUpdate> updates) {

		if (updates.isEmpty()) {
			return;
		}

		Runnable applyAll = () -> {
			Document document = FileDocumentManager.getInstance().getDocument(buildFile);
			if (document != null) {
				PsiDocumentManager.getInstance(project).commitDocument(document);
			}

			PsiFile psiFile = PsiManager.getInstance(project).findFile(buildFile);
			if (psiFile == null) {
				LOG.warn("Cannot update Gradle file: PSI not found for " + buildFile.getPath());
				return;
			}

			GradlePropertyResolver propertyResolver = GradlePropertyResolver.create(psiFile);

			for (DependencyUpdate update : updates) {
				applyUpdate(psiFile, propertyResolver, update);
			}

			Document after = PsiDocumentManager.getInstance(project).getDocument(psiFile);
			if (after != null) {
				PsiDocumentManager.getInstance(project).commitDocument(after);
			}
		};

		ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(project,
				applyAll, MessageBundle.message("command.update.title"), null));
	}

	private void applyUpdate(PsiFile buildFile, GradlePropertyResolver propertyResolver, DependencyUpdate update) {

		String newVersion = update.version().toString();

		for (VersionSource source : update.versionSources()) {

			if (source instanceof VersionSource.VersionProperty vps) {
				String propertyKey = vps.getProperty();
				updateProperty(buildFile, propertyKey, newVersion);
			}

			if (source instanceof VersionSource.DeclaredVersion
					|| source instanceof VersionSource.VersionCatalogProperty) {
				for (DeclarationSource declSrc : update.declarationSources()) {
					if (declSrc instanceof DeclarationSource.Plugin) {
						updatePlugin(buildFile, propertyResolver, update.coordinate(), newVersion);
					} else {
						updateDeclaration(buildFile, propertyResolver, update.coordinate(), newVersion);
					}
				}
			}
		}
	}

	/**
	 * Updates a version entry inside the {@code [versions]} table of a
	 * {@code libs.versions.toml} catalog.
	 *
	 * @return {@code true} if the key was found and updated
	 */
	public void updateProperty(TomlFile file, String propertyKey, String newVersion) {

		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(file, TomlTable.class)) {
			String tableName = table.getHeader().getKey() != null ? table.getHeader().getKey().getText().trim() : null;

			if (!"versions".equals(tableName)) {
				continue;
			}

			for (TomlKeyValue kv : PsiTreeUtil.getChildrenOfTypeAsList(table, TomlKeyValue.class)) {
				if (!propertyKey.equals(kv.getKey().getText().trim())) {
					continue;
				}
				if (!(kv.getValue() instanceof TomlLiteral literal)) {
					continue;
				}
				TomlLiteral newLiteral = new TomlPsiFactory(project, false)
						.createLiteral("\"%s\"".formatted(newVersion));
				literal.replace(newLiteral);
			}
		}
	}

	/**
	 * Updates a property value in a properties file or Groovy/Kotlin ext/extra
	 * block.
	 *
	 * @return {@code true} if the property was found and updated
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
			new UpdateGroovyDsl(property -> null).updateExtProperty(file, propertyKey, newVersion);
		}

		// Kotlin DSL: extra["key"] = "value"
		if (GradleUtils.isKotlinDsl(file.getVirtualFile()) && GradleUtils.KOTLIN_AVAILABLE) {
			new UpdateKotlinDsl(property -> null).updateExtraProperty(file, propertyKey, newVersion);
		}
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
			new UpdateKotlinDsl(propertyResolver).updateDeclaration(file, artifactId, newVersion,
					DeclarationSource.dependency());
		}
	}

	/**
	 * Updates the plugin versions.
	 */
	private void updatePlugin(PsiFile file, GradlePropertyResolver propertyResolver, ArtifactId id, String newVersion) {

		// TOML
		VirtualFile virtualFile = file.getVirtualFile();

		if (GradleUtils.isVersionCatalog(virtualFile) && file instanceof TomlFile tomlFile) {
			updateDeclaration(tomlFile, id, newVersion);
		}

		// Groovy DSL
		if (GradleUtils.isGroovyDsl(virtualFile)) {
			new UpdateGroovyDsl(propertyResolver).updateDeclaration(file, id, newVersion);
		}

		// Kotlin DSL
		if (GradleUtils.isKotlinDsl(virtualFile) && GradleUtils.KOTLIN_AVAILABLE) {
			new UpdateKotlinDsl(propertyResolver).updateDeclaration(file, id, newVersion,
					DeclarationSource.plugin());
		}
	}

	/**
	 * Updates {@code version = "…"} inside a {@code [libraries]} or
	 * {@code [plugins]} inline table when the entry matches {@code artifactId} and
	 * uses a literal version (not {@code version.ref}).
	 */
	private void updateDeclaration(TomlFile file, ArtifactId artifactId, String newVersion) {

		Map<String, PsiPropertyValueElement> properties = TomlParser.parseTomlVersions(file);
		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(file, TomlTable.class)) {

			String tableName = TomlParser.getTomlTableName(table);
			boolean isPlugin = TomlParser.PLUGINS.equals(tableName);
			boolean isDependency = TomlParser.LIBRARIES.equals(tableName);
			if ((!isDependency && !isPlugin)) {
				continue;
			}

			for (TomlKeyValue kv : PsiTreeUtil.getChildrenOfTypeAsList(table, TomlKeyValue.class)) {

				TomlDependencyDeclaration entry = TomlParser.parseTomlEntry(kv, properties);
				if (!entry.isComplete()) {
					continue;
				}

				GradleDependency dep = entry.toDependency();

				if (!(dep instanceof SimpleDependency sd) || !sd.id().equals(artifactId)
						|| entry.versionLiteral() == null) {
					continue;
				}

				String replacementText = newVersion;
				if (kv.getValue() instanceof TomlLiteral) {
					String text = TomlParser.getRequiredText(kv.getValue());
					replacementText = text.replace(entry.version(), newVersion);
				}

				TomlLiteral newLiteral = new TomlPsiFactory(project, false)
						.createLiteral("\"%s\"".formatted(replacementText));
				entry.versionLiteral().replace(newLiteral);
			}
		}
	}

}
