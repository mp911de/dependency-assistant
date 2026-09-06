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

package biz.paluch.dap.assistant.check;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.util.Sequence;
import com.intellij.psi.PsiFile;

/**
 * Build files selected for a dependency check.
 * <p>The entries are retained directly and must not be modified.
 *
 * @author Mark Paluch
 * @param reason how the scope was resolved or why it is empty.
 */
public record UpgradeScope(List<Entry> entries, Reason reason) implements Sequence<UpgradeScope.Entry> {

	/**
	 * Create a discovery scope without an explicit selection.
	 */
	public static UpgradeScope discover(List<Entry> entries) {
		return new UpgradeScope(entries, Reason.DISCOVERY);
	}

	/**
	 * Create a successfully resolved scope.
	 */
	public static UpgradeScope resolved(List<Entry> entries) {
		return new UpgradeScope(entries, Reason.SUCCESS);
	}

	/**
	 * Create an empty scope with the supplied reason.
	 */
	public static UpgradeScope notFound(Reason reason) {
		return new UpgradeScope(List.of(), reason);
	}

	@Override
	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public int size() {
		return entries.size();
	}

	@Override
	public Iterator<Entry> iterator() {
		return entries().iterator();
	}

	@Override
	public Stream<Entry> stream() {
		return entries.stream();
	}

	/**
	 * How scope resolution completed.
	 */
	public enum Reason {

		/**
		 * Initial discovery of build files.
		 */
		DISCOVERY,

		/**
		 * Upgrade scope resolved successfully.
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
	 * A build file and its available dependency context.
	 */
	public record Entry(ProjectDependencyContext context, PsiFile buildFile) {
	}

}
