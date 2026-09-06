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
 * Immutable build-file scope retaining unresolved paths for reporting.
 * Iteration exposes only resolved files. {@link #getPaths()} includes missing
 * paths in declaration order. Use {@link #rebuild()} to resolve the paths
 * again.
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
	 * Copy the resolved files into a scope with no missing paths.
	 */
	public static FileScope of(VirtualFile... files) {
		return new FileScope(List.of(files));
	}

	/**
	 * Copy the resolved files into a scope with no missing paths.
	 */
	public static FileScope of(Collection<VirtualFile> files) {
		return new FileScope(List.copyOf(files));
	}

	/**
	 * Resolve local paths in declaration order, retaining unresolved paths as
	 * missing.
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
	 * Return a new scope by resolving the original paths against the current file
	 * system.
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
	 * Return the immutable declared paths, including those that could not be
	 * resolved.
	 */
	public List<String> getPaths() {
		return paths;
	}

	/**
	 * Return resolved files' presentable URLs, one per line in declaration order.
	 */
	public String getPresentablePaths() {
		return files.stream().map(VirtualFile::getPresentableUrl).collect(Collectors.joining("\n"));
	}

	public boolean hasMissingFiles() {
		return !missingPaths.isEmpty();
	}

	/**
	 * Return unresolved paths, one per line in declaration order.
	 */
	public String getMissingPaths() {
		return String.join("\n", missingPaths);
	}

}
