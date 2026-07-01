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

package biz.paluch.dap.support;

import java.util.Collection;
import java.util.function.Predicate;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;

/**
 * Project-level service for locating indexed files in project content.
 *
 * <p>This service centralizes the search scope used by dependency assistants:
 * files from library roots, IDE-ignored paths, and VCS-ignored paths are
 * excluded before the caller supplied filter is applied. Use this service when
 * an assistant needs project-owned files only. Use IntelliJ's index APIs
 * directly when library files or a broader search scope are intentionally part
 * of the lookup.
 *
 * @author Mark Paluch
 * @see FileTypeIndex
 * @see FilenameIndex
 */
@Service(Service.Level.PROJECT)
public final class FileIndexLookup {

	private final Project project;

	private final ProjectFileIndex fileIndex;

	private final ChangeListManager changeListManager;

	private final GlobalSearchScope projectScope;

	private final GlobalSearchScope libraryScope;

	private FileIndexLookup(Project project) {
		this.project = project;
		this.fileIndex = ProjectFileIndex.getInstance(project);
		this.changeListManager = ChangeListManager.getInstance(project);
		this.projectScope = ProjectScope.getProjectScope(project);
		this.libraryScope = ProjectScope.getLibrariesScope(project);
	}

	/**
	 * Return the {@code FileIndexLookup} service for the given project.
	 * @param project the project whose indexed files should be queried.
	 * @return the project-scoped lookup service.
	 */
	public static FileIndexLookup getInstance(Project project) {
		return project.getService(FileIndexLookup.class);
	}

	/**
	 * Find project files of the given file type.
	 * @param fileType the file type to resolve through {@link FileTypeIndex}.
	 * @param filter the additional predicate that candidate project files must
	 * match.
	 * @return the matching project files.
	 */
	public Collection<VirtualFile> find(FileType fileType, Predicate<VirtualFile> filter) {
		return FileTypeIndex.getFiles(fileType, projectScope(filter));
	}

	/**
	 * Find project files with the given file name.
	 * @param fileName the exact file name to resolve through {@link FilenameIndex}.
	 * @param filter the additional predicate that candidate project files must
	 * match.
	 * @return the matching project files.
	 */
	public Collection<VirtualFile> find(String fileName, Predicate<VirtualFile> filter) {
		return FilenameIndex.getVirtualFilesByName(fileName, projectScope(filter));
	}

	private GlobalSearchScope projectScope(Predicate<VirtualFile> filter) {

		return new DelegatingGlobalSearchScope(projectScope) {

			@Override
			public boolean contains(VirtualFile file) {
				return super.contains(file) && fileIndex.isInContent(file)
						&& !libraryScope.contains(file)
						&& !fileIndex.isUnderIgnored(file)
						&& !changeListManager.isIgnoredFile(file)
						&& filter.test(file);
			}

		};
	}

}
