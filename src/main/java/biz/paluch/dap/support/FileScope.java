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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import biz.paluch.dap.util.Sequence;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Build-file scope shared by the dependency check result and the Upgrade Plan,
 * resolved to live files, retaining the paths that could not be resolved so
 * callers can report them. Immutable; {@link #rebuild()} re-resolves against
 * the current file system state.
 *
 * <p>Iterating or {@link #stream() streaming} a scope yields only the resolved
 * files, whereas {@link #getPaths()} returns every declared path including the
 * ones that are currently missing.
 *
 * @author Mark Paluch
 */
public class FileScope implements Sequence<VirtualFile> {

	private final List<VirtualFile> files;

	private final List<String> missingPaths;

	private final List<String> paths;

	private FileScope(List<VirtualFile> files) {
		this(files, List.of(), files.stream().map(VirtualFile::getPath).toList());
	}

	private FileScope(List<VirtualFile> files, List<String> missingPaths, List<String> paths) {
		this.files = List.copyOf(files);
		this.missingPaths = List.copyOf(missingPaths);
		this.paths = List.copyOf(paths);
	}

	/**
	 * Create a scope from already-resolved build files, with no missing paths.
	 *
	 * @param files the resolved build files.
	 * @return the scope.
	 * @see #of(List)
	 */
	public static FileScope of(VirtualFile... files) {
		return new FileScope(List.of(files));
	}

	/**
	 * Create a scope from already-resolved build files, with no missing paths.
	 *
	 * @param files the resolved build files.
	 * @return the scope.
	 */
	public static FileScope of(List<VirtualFile> files) {
		return new FileScope(files);
	}

	/**
	 * Resolve the given paths against the local file system, keeping the ones that
	 * do not resolve as missing paths. The declared order is preserved and drives
	 * {@link #getPaths()}.
	 *
	 * @param paths the declared build-file paths to resolve.
	 * @return the scope with resolved files and retained missing paths.
	 */
	public static FileScope from(Collection<String> paths) {

		LocalFileSystem fileSystem = LocalFileSystem.getInstance();

		List<VirtualFile> files = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		for (String path : paths) {

			VirtualFile file = fileSystem.findFileByPath(path);
			if (file != null) {
				files.add(file);
			} else {
				missing.add(path);
			}
		}

		return new FileScope(files, missing, List.copyOf(paths));
	}

	/**
	 * Re-resolve the scope against the current file system state: files that
	 * disappeared become missing paths, missing paths that reappeared become files.
	 */
	public FileScope rebuild() {
		return from(paths);
	}

	@Override
	public Stream<VirtualFile> stream() {
		return files.stream();
	}

	@Override
	public Iterator<VirtualFile> iterator() {
		return files.iterator();
	}

	/**
	 * Return every declared path in the scope in declaration order, resolved and
	 * currently missing alike. The inverse of {@link #from(Collection)}:
	 * {@code from(scope.getPaths())} reconstructs an equivalent scope against the
	 * current file system state.
	 *
	 * @return the declared paths, resolved and missing alike.
	 */
	public List<String> getPaths() {
		return paths;
	}

	/**
	 * Return the live files' presentable URLs joined line-by-line, for dialog and
	 * notification texts.
	 */
	public String getPresentablePaths() {
		return files.stream().map(VirtualFile::getPresentableUrl).collect(Collectors.joining("\n"));
	}

	/**
	 * Return whether the scope declares any path that does not currently resolve to
	 * a file.
	 *
	 * @return {@code true} if at least one declared path is missing; {@code false}
	 * otherwise.
	 */
	public boolean hasMissingFiles() {
		return !missingPaths.isEmpty();
	}

	/**
	 * Return the paths that could not be resolved, joined line-by-line for dialog
	 * and notification texts.
	 */
	public String getMissingPaths() {
		return String.join("\n", missingPaths);
	}

}
