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

import biz.paluch.dap.gradle.LookupSite.PropertyLookupSite;
import biz.paluch.dap.gradle.LookupSite.ResolvedSite;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.VersionedDependencySite;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;
import org.toml.lang.psi.TomlFile;
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

	private final TomlLookupSiteLocator tomlSiteLocator = new TomlLookupSiteLocator();

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
	 * Resolves the {@link ArtifactReference} from a {@link TomlLiteral literal} by
	 * classifying it through {@link TomlLookupSiteLocator} and dispatching the
	 * resulting {@link LookupSite} to the same primitives used by
	 * {@link GradleLookupSiteResolver}.
	 *
	 * @param literal the TOML literal.
	 * @return the resolved artifact reference result.
	 */
	public ArtifactReference resolveTomlLiteral(TomlLiteral literal) {
		return resolveLookupSite(tomlSiteLocator.locate(literal));
	}

	private ArtifactReference resolveLookupSite(LookupSite site) {

		if (site.isAbsent()) {
			return ArtifactReference.unresolved();
		}

		if (site instanceof PropertyLookupSite propertySite) {
			return ArtifactReferenceUtils.resolve(propertySite, projectState);
		}

		if (site instanceof ResolvedSite(VersionedDependencySite versionedDependency)) {
			return ArtifactReferenceUtils.resolve(versionedDependency,
					() -> GradlePropertyResolver.forFile(file));
		}

		return ArtifactReference.unresolved();
	}

	/**
	 * Resolves a {@code libs.…} catalog accessor through
	 * {@code gradle/libs.versions.toml}.
	 *
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
		PropertyResolver propertyResolver = PropertyResolver.fromMap(parseTomlVersions(tomlFile));

		for (TomlTable table : PsiTreeUtil.getChildrenOfTypeAsList(tomlFile, TomlTable.class)) {
			if (tomlReference.getTableName().equals(getTomlTableName(table))) {
				TomlParser.parseEntries(table, propertyResolver, dependencies::add);
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
			if (!isSafeCatalogRelativePath(path)) {
				return null;
			}
			VirtualFile root = GradleUtils.findProjectRoot(project, virtualFile);
			VirtualFile catalogFile = root.findFileByRelativePath(path);
			if (catalogFile != null && VfsUtil.isAncestor(root, catalogFile, false)) {
				return PsiManager.getInstance(project).findFile(catalogFile);
			}
			return null;
		}

		return TomlParser.findVersionCatalogToml(project, virtualFile);
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

	private ArtifactReference getArtifactReference(TomlDependencyDeclaration declaration, PsiElement usage) {

		if (!declaration.isComplete()) {
			return ArtifactReference.unresolved();
		}

		return ArtifactReferenceUtils.resolve(
				declaration.toDependencySite(usage, declaration.getRequiredVersionLiteral()),
				() -> GradlePropertyResolver.forFile(declaration.element().getContainingFile()));
	}

}
