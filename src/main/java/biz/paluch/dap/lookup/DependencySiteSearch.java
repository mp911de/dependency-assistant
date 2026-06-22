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

package biz.paluch.dap.lookup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Orchestrates a Dependency Site Find across a set of build files: drives the
 * per-file search and combines the {@link DependencySearchResults},
 * deduplicating sites seen through more than one file.
 *
 * <p>Per-file resolution is injected so the aggregation can be tested in
 * isolation; {@link #create(Project)} wires it to the live build-tool
 * integration.
 *
 * @author Mark Paluch
 */
public class DependencySiteSearch {

	private final BiFunction<VirtualFile, DependencySiteQuery, DependencySearchResults> perFileSearch;

	DependencySiteSearch(BiFunction<VirtualFile, DependencySiteQuery, DependencySearchResults> perFileSearch) {
		this.perFileSearch = perFileSearch;
	}

	/**
	 * Search the given files for the query and combine the per-file results.
	 *
	 * @param query the version this find is centered on; must not be
	 * {@literal null}.
	 * @param files the files to search.
	 * @return the deduplicated results in file order.
	 */
	public DependencySearchResults find(DependencySiteQuery query, Collection<VirtualFile> files) {

		List<DependencySearchResults> perFile = new ArrayList<>(files.size());
		for (VirtualFile file : files) {
			perFile.add(perFileSearch.apply(file, query));
		}

		return DependencySearchResults.concat(perFile);
	}

	/**
	 * Create a search object that resolves each file through its build-tool
	 * integration.
	 *
	 * @param perFileSearch function to resolve a file through its build-tool
	 * integration.
	 * @return a finder bound to the live integrations.
	 */
	public static DependencySiteSearch create(
			BiFunction<VirtualFile, DependencySiteQuery, DependencySearchResults> perFileSearch) {
		return new DependencySiteSearch(perFileSearch);
	}

}
