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

package biz.paluch.dap.assistant.check;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.util.Sequence;
import com.intellij.psi.PsiFile;

/**
 * The outcome of resolving the build files a single {@link DependencyCheck}
 * runs over.
 *
 * @param entries the in-scope build files; empty when nothing could be
 * resolved.
 * @param reason the reason for resolution results.
 * @author Mark Paluch
 */
public record UpgradeScope(List<Entry> entries, Reason reason) implements Sequence<UpgradeScope.Entry> {

	/**
	 * Create a discovery scope from the given entries, gathered without an explicit
	 * selection.
	 */
	public static UpgradeScope discover(List<Entry> entries) {
		return new UpgradeScope(entries, Reason.DISCOVERY);
	}

	/**
	 * Create a resolved scope from the given entries, resolved from an explicit
	 * selection.
	 */
	public static UpgradeScope resolved(List<Entry> entries) {
		return new UpgradeScope(entries, Reason.SUCCESS);
	}

	/**
	 * Create an empty scope carrying why nothing was resolved.
	 */
	public static UpgradeScope notFound(Reason reason) {
		return new UpgradeScope(List.of(), reason);
	}

	/**
	 * Return whether no build file is in scope.
	 * @return {@literal true} if {@link #entries()} is empty; {@literal false}
	 * otherwise.
	 */
	@Override
	public boolean isEmpty() {
		return entries.isEmpty();
	}

	/**
	 * Return the number of build files in scope.
	 * @return the number of build files in scope.
	 */
	public int size() {
		return entries.size();
	}

	@Override
	public Iterator<Entry> iterator() {
		return entries().iterator();
	}

	/**
	 * Return the in-scope build files as a stream.
	 */
	@Override
	public Stream<Entry> stream() {
		return entries.stream();
	}

	/**
	 * Classification of how a scope was resolved, covering both populated scopes
	 * ({@link #DISCOVERY}, {@link #SUCCESS}) and the reasons a scope is empty
	 * ({@link #NO_BUILD_FILES}, {@link #NOT_IMPORTED}).
	 */
	public enum Reason {

		/**
		 * Initial discovery of build files.
		 */
		DISCOVERY,

		/**
		 * Upgrade scope resolved from an explicit selection.
		 */
		SUCCESS,

		/**
		 * The selection contained no file supported by any integration.
		 */
		NO_BUILD_FILES,

		/**
		 * The selection contained a supported build file whose project model is not
		 * imported, so no context is available for it.
		 */
		NOT_IMPORTED
	}

	/**
	 * One in-scope build file paired with the {@link ProjectDependencyContext
	 * context} that operates on it.
	 *
	 * @param context the dependency context for the file; always
	 * {@link ProjectDependencyContext#isAvailable() available}.
	 * @param buildFile the build file to scan and write back to.
	 */
	public record Entry(ProjectDependencyContext context, PsiFile buildFile) {
	}

}
