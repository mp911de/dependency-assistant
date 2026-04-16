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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GradleDependency.PropertyManagedDependency;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.ProjectProperty;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.PsiPropertyValueElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;
import org.toml.lang.psi.TomlFile;
import org.toml.lang.psi.TomlKeyValue;
import org.toml.lang.psi.TomlLiteral;
import org.toml.lang.psi.TomlTable;

import static biz.paluch.dap.gradle.TomlParser.*;

/**
 * Resolves {@link ArtifactReference} from version-catalog TOML and from
 * {@code libs.…} accessors.
 *
 * @author Mark Paluch
 */
class TomlArtifactResolver {

	private final Project project;

	private final PsiFile file;

	private final @Nullable ProjectState projectState;

	private final @Nullable VersionCatalogRegistry registry;

	TomlArtifactResolver(Project project, PsiFile file,
			@Nullable ProjectState projectState) {
		this(project, file, projectState, null);
	}

	TomlArtifactResolver(Project project, PsiFile file,
			@Nullable ProjectState projectState,
			@Nullable VersionCatalogRegistry registry) {

		this.project = project;
		this.file = file;
		this.projectState = projectState;
		this.registry = registry;
	}

	/**
	 * Resolves the {@link ArtifactReference} from a {@link TomlLiteral literal}.
	 * @param literal the TOML literal.
	 * @return the resolved artifact reference result.
	 */
	public ArtifactReference resolveTomlLiteral(TomlLiteral literal) {

		TomlKeyValue kv = PsiTreeUtil.getParentOfType(literal, TomlKeyValue.class);
		if (kv == null) {
			return ArtifactReference.unresolved();
		}

		if (isInsideTable(literal, VERSIONS::equals)) {
			return resolveByProperty(kv, literal);
		}

		if (isInsideTable(literal, it -> it.equals(TomlParser.LIBRARIES) || it.equals(TomlParser.PLUGINS))) {
			return resolve(kv, literal);
		}

		return ArtifactReference.unresolved();
	}

	private ArtifactReference resolveByProperty(TomlKeyValue kv, TomlLiteral literal) {

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
			it.artifact(first.toArtifactId()).declarationElement(kv)
					.versionSource(VersionSource.property(versionKey));
			ArtifactVersion.from(version).ifPresent(it::version);
			it.versionLiteral(literal);
		});
	}

	private ArtifactReference resolve(TomlKeyValue kv, TomlLiteral literal) {

		PsiElement keyPsi = literal.getParent() != null ? literal.getParent().getFirstChild() : null;
		if (keyPsi == null || !VERSION.equals(keyPsi.getText().trim())) {
			return ArtifactReference.unresolved();
		}

		Map<String, PsiPropertyValueElement> properties = parseTomlVersions(kv.getContainingFile());
		TomlDependencyDeclaration entry = TomlParser.parseTomlEntry(kv, properties);
		return getArtifactReference(entry, kv);
	}

	/**
	 * Resolves a {@code libs.…} catalog accessor through
	 * {@code gradle/libs.versions.toml}.
	 * @param tomlReference TOML reference to resolve.
	 * @param usage the PSI element where the reference is used (the
	 * {@code alias(libs…)} element).
	 * @return the resolved artifact.
	 */
	public ArtifactReference resolveReference(TomlReference tomlReference, PsiElement usage) {

		VirtualFile virtualFile = file.getVirtualFile();
		if (virtualFile == null) {
			return ArtifactReference.unresolved();
		}

		PsiFile catalogPsi = findCatalogForReference(tomlReference, virtualFile);
		if (!(catalogPsi instanceof TomlFile tomlFile)) {
			return ArtifactReference.unresolved();
		}

		List<TomlDependencyDeclaration> dependencies = new ArrayList<>();
		Map<String, PsiPropertyValueElement> properties = parseTomlVersions(tomlFile);

		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(tomlFile, TomlTable.class)) {
			if (tomlReference.getTableName().equals(getTomlTableName(table))) {
				TomlParser.parseEntries(table, properties, dependencies::add);
			}
		}

		for (TomlDependencyDeclaration declaration : dependencies) {
			if (!declaration.hasKeyMatching(tomlReference)) {
				continue;
			}

			return getArtifactReference(declaration, usage);
		}

		return ArtifactReference.unresolved();
	}

	@Nullable
	PsiFile findCatalogForReference(TomlReference tomlReference, VirtualFile virtualFile) {

		if (registry != null) {
			String path = registry.pathForAlias(tomlReference.getCatalogAlias());
			if (path == null) {
				return null;
			}
			VirtualFile root = GradleUtils.findProjectRoot(virtualFile);
			VirtualFile catalogFile = root.findFileByRelativePath(path);
			if (catalogFile != null) {
				return PsiManager.getInstance(project).findFile(catalogFile);
			}
			return null;
		}

		return TomlParser.findVersionCatalogToml(project, virtualFile);
	}

	private ArtifactReference getArtifactReference(TomlDependencyDeclaration declaration, PsiElement usage) {

		if (!declaration.isComplete()) {
			return ArtifactReference.unresolved();
		}

		GradleDependency dependency = declaration.toDependency();
		DependencyAndVersionLocation location = new DependencyAndVersionLocation(dependency,
				declaration.versionLiteral());

		if (dependency instanceof PropertyManagedDependency) {
			return ArtifactReferenceUtils.resolve(location, usage, GradlePropertyResolver.forFile(declaration.file()));
		}

		return ArtifactReferenceUtils.resolve(location, usage, it -> null);
	}

	private static boolean isInsideTable(PsiElement element, Predicate<String> predicate) {

		TomlTable table = PsiTreeUtil.getParentOfType(element, TomlTable.class);
		if (table == null) {
			return false;
		}
		String name = getTomlTableName(table);
		return name != null && predicate.test(name);
	}


}
