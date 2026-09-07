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

package biz.paluch.dap.support;

import java.util.Collection;
import java.util.function.Predicate;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;

/**
 * Finds indexed project-content files, excluding library roots and IDE- or
 * VCS-ignored paths before applying the caller's filter.
 *
 * @author Mark Paluch
 */
@Service(Service.Level.PROJECT)
public final class FileIndexLookup {

	private final Project project;

	private final ProjectFileIndex fileIndex;

	private final GlobalSearchScope projectScope;

	private final GlobalSearchScope libraryScope;

	private FileIndexLookup(Project project) {
		this.project = project;
		this.fileIndex = ProjectFileIndex.getInstance(project);
		this.projectScope = ProjectScope.getProjectScope(project);
		this.libraryScope = ProjectScope.getLibrariesScope(project);
	}

	public static FileIndexLookup getInstance(Project project) {
		return project.getService(FileIndexLookup.class);
	}

	public Collection<VirtualFile> find(FileType fileType, Predicate<VirtualFile> filter) {
		return FileTypeIndex.getFiles(fileType, projectScope(filter));
	}

	/**
	 * Find project files by exact file name.
	 */
	public Collection<VirtualFile> find(String fileName, Predicate<VirtualFile> filter) {
		return FilenameIndex.getVirtualFilesByName(fileName, projectScope(filter));
	}

	private GlobalSearchScope projectScope(Predicate<VirtualFile> filter) {

		VersionControl vcs = VersionControl.find(project);
		return new DelegatingGlobalSearchScope(projectScope) {

			@Override
			public boolean contains(VirtualFile file) {
				return super.contains(file) && fileIndex.isInContent(file)
						&& !libraryScope.contains(file)
						&& !fileIndex.isUnderIgnored(file)
						&& !vcs.isIgnored(file)
						&& filter.test(file);
			}

		};
	}

}
